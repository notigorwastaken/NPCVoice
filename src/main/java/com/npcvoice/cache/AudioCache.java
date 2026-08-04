package com.npcvoice.cache;

import com.npcvoice.NPCVoicePlugin;
import com.npcvoice.config.ConfigManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Stream;

public final class AudioCache {

    private final NPCVoicePlugin plugin;
    private final ConfigManager configManager;
    private final Path cacheDir;
    private final Map<String, CompletableFuture<byte[]>> inFlight = new ConcurrentHashMap<>();
    private final Object diskLock = new Object();

    public AudioCache(NPCVoicePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cacheDir = Path.of(plugin.getDataFolder().getAbsolutePath(), "cache");
    }

    public void initialize() {
        if (!configManager.cacheEnabled()) return;
        try {
            Files.createDirectories(cacheDir);
            pruneToConfiguredSize();
            plugin.getLogger().info("Audio cache directory: " + cacheDir);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create cache directory", e);
        }
    }

    @Nullable
    public byte[] get(@NotNull String text, @NotNull String voice, @NotNull String provider) {
        if (!configManager.cacheEnabled() || configManager.cacheMaxSizeMb() == 0) return null;
        try {
            Path cacheFile = cachePath(text, voice, provider);
            if (Files.exists(cacheFile)) {
                byte[] audio = Files.readAllBytes(cacheFile);
                try {
                    Files.setLastModifiedTime(cacheFile, FileTime.fromMillis(System.currentTimeMillis()));
                } catch (IOException ignored) {
                }
                return audio;
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read audio cache", e);
        }
        return null;
    }

    public void put(@NotNull String text, @NotNull String voice, @NotNull String provider, byte @NotNull [] audioData) {
        if (!configManager.cacheEnabled() || configManager.cacheMaxSizeMb() == 0 || audioData.length == 0) return;
        synchronized (diskLock) {
            Path temporary = null;
            try {
                Files.createDirectories(cacheDir);
                Path cacheFile = cachePath(text, voice, provider);
                temporary = Files.createTempFile(cacheDir, "npcvoice-", ".tmp");
                Files.write(temporary, audioData);
                try {
                    Files.move(temporary, cacheFile,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException atomicMoveFailure) {
                    Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING);
                }
                pruneToConfiguredSize();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to write audio cache", e);
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    public CompletableFuture<byte[]> getOrGenerateAsync(
            @NotNull String text,
            @NotNull String voice,
            @NotNull String provider,
            @NotNull Supplier<CompletableFuture<byte[]>> generator
    ) {
        if (!configManager.cacheEnabled() || configManager.cacheMaxSizeMb() == 0) {
            return generator.get();
        }

        byte[] cached = get(text, voice, provider);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        String key = cacheFileName(text, voice, provider);
        CompletableFuture<byte[]> future = inFlight.computeIfAbsent(key, ignored -> {
            CompletableFuture<byte[]> generated;
            try {
                generated = generator.get();
            } catch (Throwable error) {
                generated = CompletableFuture.failedFuture(error);
            }
            return generated.thenApply(audio -> {
                if (audio != null && audio.length > 0) {
                    put(text, voice, provider, audio);
                }
                return audio;
            });
        });
        future.whenComplete((audio, error) -> inFlight.remove(key, future));
        return future;
    }

    public void clear() {
        if (!Files.isDirectory(cacheDir)) return;
        try {
            synchronized (diskLock) {
                try (Stream<Path> files = Files.walk(cacheDir)) {
                    files.filter(Files::isRegularFile)
                            .forEach(file -> {
                                try {
                                    Files.delete(file);
                                } catch (IOException e) {
                                    plugin.getLogger().log(Level.WARNING, "Failed to delete cache file: " + file, e);
                                }
                            });
                }
            }
            plugin.getLogger().info("Audio cache cleared.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to clear cache", e);
        }
    }

    public long size() {
        if (!Files.isDirectory(cacheDir)) return 0;
        try {
            try (Stream<Path> files = Files.walk(cacheDir)) {
                return files.filter(Files::isRegularFile)
                        .mapToLong(file -> {
                            try {
                                return Files.size(file);
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .sum();
            }
        } catch (IOException e) {
            return 0;
        }
    }

    @NotNull
    private Path cachePath(@NotNull String text, @NotNull String voice, @NotNull String provider) {
        return cacheDir.resolve(cacheFileName(text, voice, provider));
    }

    private String cacheFileName(String text, String voice, String provider) {
        return hashText(provider + "\0" + voice + "\0" + text) + ".raw";
    }

    @NotNull
    private String hashText(@NotNull String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    public void shutdown() {
        inFlight.clear();
        plugin.getLogger().info("Audio cache shutdown. Total cache size: " + (size() / 1024 / 1024) + " MB");
    }

    private void pruneToConfiguredSize() throws IOException {
        if (!Files.isDirectory(cacheDir)) return;
        long maxBytes = configManager.cacheMaxSizeMb() * 1024L * 1024L;

        List<Path> files;
        try (Stream<Path> stream = Files.list(cacheDir)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(this::lastModified))
                    .toList();
        }

        long total = 0;
        for (Path file : files) {
            total += safeSize(file);
        }
        for (Path file : files) {
            if (total <= maxBytes) break;
            long fileSize = safeSize(file);
            if (Files.deleteIfExists(file)) {
                total -= fileSize;
            }
        }
    }

    private long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private long safeSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }
}
