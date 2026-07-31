package com.npcvoice.audio;

import com.npcvoice.NPCVoicePlugin;
import com.npcvoice.util.AudioConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Stream;

public final class AudioFileManager {

    private final NPCVoicePlugin plugin;
    private final Path audioDir;
    private final int maxCachedFiles;
    private final Map<String, CachedAudio> cache;
    private List<String> fileIndex;

    public AudioFileManager(NPCVoicePlugin plugin, Path dataFolder) {
        this.plugin = plugin;
        this.audioDir = dataFolder.resolve("audio");
        this.maxCachedFiles = plugin.getConfig().getInt("audio.max_cached_files", 50);
        this.cache = new ConcurrentHashMap<>();
        this.fileIndex = new ArrayList<>();
    }

    public void initialize() {
        try {
            Files.createDirectories(audioDir);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create audio directory", e);
        }
        scanFiles();
        plugin.getLogger().info("Audio files found: " + fileIndex.size());
    }

    public void scanFiles() {
        fileIndex = new ArrayList<>();
        if (!Files.isDirectory(audioDir)) return;

        try (Stream<Path> stream = Files.list(audioDir)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> {
                        String lower = name.toLowerCase();
                        return lower.endsWith(".wav") || lower.endsWith(".mp3");
                    })
                    .map(name -> {
                        int dot = name.lastIndexOf('.');
                        return dot > 0 ? name.substring(0, dot) : name;
                    })
                    .sorted()
                    .forEach(fileIndex::add);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to scan audio directory", e);
        }
    }

    public List<String> listAudioFiles() {
        return List.copyOf(fileIndex);
    }

    @Nullable
    public short[] getPcmShorts(@NotNull String name) {
        CachedAudio cached = cache.get(name);
        if (cached != null) {
            return cached.samples;
        }

        short[] samples = loadAudioFile(name);
        if (samples != null) {
            if (cache.size() >= maxCachedFiles) {
                evictOldest();
            }
            cache.put(name, new CachedAudio(samples, System.currentTimeMillis()));
        }
        return samples;
    }

    @Nullable
    public File findAudioFile(@NotNull String name) {
        File wavFile = audioDir.resolve(name + ".wav").toFile();
        if (wavFile.exists()) return wavFile;

        File mp3File = audioDir.resolve(name + ".mp3").toFile();
        if (mp3File.exists()) return mp3File;

        return null;
    }

    @Nullable
    public String findAudioFileName(@NotNull String name) {
        File wavFile = audioDir.resolve(name + ".wav").toFile();
        if (wavFile.exists()) return name + ".wav";

        File mp3File = audioDir.resolve(name + ".mp3").toFile();
        if (mp3File.exists()) return name + ".mp3";

        return null;
    }

    @Nullable
    private short[] loadAudioFile(@NotNull String name) {
        File file = findAudioFile(name);
        if (file == null) return null;

        try {
            String lower = file.getName().toLowerCase();

            if (lower.endsWith(".wav")) {
                byte[] wavBytes = Files.readAllBytes(file.toPath());
                if (AudioConverter.isWav(wavBytes)) {
                    return AudioConverter.toPcmShorts(wavBytes);
                }
                return decodeWithJavaSound(file);
            }

            if (lower.endsWith(".mp3")) {
                byte[] mp3Bytes = Files.readAllBytes(file.toPath());
                return AudioConverter.decodeMp3(mp3Bytes);
            }

            plugin.getLogger().warning("Unsupported audio file: " + file.getName());
            return null;

        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load audio file: " + file.getName(), e);
            return null;
        }
    }

    @Nullable
    private short[] decodeWithJavaSound(File file) {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            AudioFormat format = ais.getFormat();
            int sampleRate = (int) format.getSampleRate();

            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(),
                    16,
                    format.getChannels(),
                    format.getChannels() * 2,
                    format.getSampleRate(),
                    false
            );

            if (!format.matches(pcmFormat)) {
                try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcmFormat, ais)) {
                    return AudioConverter.resamplePcm(readAllBytes(converted), sampleRate);
                }
            }

            return AudioConverter.resamplePcm(readAllBytes(ais), sampleRate);

        } catch (UnsupportedAudioFileException e) {
            return null;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Java Sound failed to decode: " + file.getName(), e);
            return null;
        }
    }

    private byte[] readAllBytes(AudioInputStream ais) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = ais.read(buf)) != -1) {
            baos.write(buf, 0, read);
        }
        return baos.toByteArray();
    }

    private void evictOldest() {
        cache.entrySet().stream()
                .min(Map.Entry.comparingByValue(Comparator.comparingLong(c -> c.timestamp)))
                .ifPresent(entry -> cache.remove(entry.getKey()));
    }

    public void clearCache() {
        cache.clear();
    }

    public void shutdown() {
        cache.clear();
        fileIndex.clear();
    }

    private static final class CachedAudio {
        final short[] samples;
        final long timestamp;

        CachedAudio(short[] samples, long timestamp) {
            this.samples = samples;
            this.timestamp = timestamp;
        }
    }
}
