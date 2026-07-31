package com.npcvoice.cache;

import com.npcvoice.NPCVoicePlugin;
import com.npcvoice.config.ConfigManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class AudioCache {

    private final NPCVoicePlugin plugin;
    private final ConfigManager configManager;
    private final Path cacheDir;
    private final boolean enabled;

    public AudioCache(NPCVoicePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cacheDir = Path.of(plugin.getDataFolder().getAbsolutePath(), "cache");
        this.enabled = configManager.cacheEnabled();
    }

    public void initialize() {
        if (!enabled) return;
        try {
            Files.createDirectories(cacheDir);
            plugin.getLogger().info("Audio cache directory: " + cacheDir);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create cache directory", e);
        }
    }

    @Nullable
    public byte[] get(@NotNull String text, @NotNull String voice) {
        if (!enabled) return null;
        try {
            Path cacheFile = cachePath(text, voice);
            if (Files.exists(cacheFile)) {
                return Files.readAllBytes(cacheFile);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read cache for: " + text, e);
        }
        return null;
    }

    public void put(@NotNull String text, @NotNull String voice, byte @NotNull [] audioData) {
        if (!enabled || audioData == null || audioData.length == 0) return;
        try {
            Path cacheFile = cachePath(text, voice);
            Files.write(cacheFile, audioData);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write cache for: " + text, e);
        }
    }

    public CompletableFuture<byte[]> getOrGenerateAsync(
            @NotNull String text,
            @NotNull String voice,
            @NotNull Supplier<CompletableFuture<byte[]>> generator
    ) {
        if (!enabled) {
            return generator.get();
        }

        byte[] cached = get(text, voice);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return generator.get().thenApply(audio -> {
            if (audio != null && audio.length > 0) {
                put(text, voice, audio);
            }
            return audio;
        });
    }

    public void clear() {
        if (!enabled) return;
        try {
            Files.walk(cacheDir)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            plugin.getLogger().log(Level.WARNING, "Failed to delete cache file: " + file, e);
                        }
                    });
            plugin.getLogger().info("Audio cache cleared.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to clear cache", e);
        }
    }

    public long size() {
        if (!enabled) return 0;
        try {
            return Files.walk(cacheDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    @NotNull
    private Path cachePath(@NotNull String text, @NotNull String voice) {
        String hash = hashText(text);
        return cacheDir.resolve(hash + "_" + voice.replaceAll("[^a-zA-Z0-9_-]", "_") + ".raw");
    }

    @NotNull
    private String hashText(@NotNull String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes());
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    public void shutdown() {
        plugin.getLogger().info("Audio cache shutdown. Total cache size: " + (size() / 1024 / 1024) + " MB");
    }
}
