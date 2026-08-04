package com.npcvoice.tts;

import com.npcvoice.config.ConfigManager;
import com.npcvoice.util.HttpSupport;
import com.npcvoice.util.ProcessRunner;
import com.npcvoice.util.SafePaths;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PiperTTS implements TTSProvider {

    private static final Logger LOGGER = Logger.getLogger(PiperTTS.class.getName());
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration GENERATION_TIMEOUT = Duration.ofMinutes(2);

    private final ConfigManager config;
    private final Path modelsDirectory;
    private boolean available;

    public PiperTTS(ConfigManager config, Path dataDirectory) {
        this.config = config;
        this.modelsDirectory = dataDirectory.resolve("models");
        checkAvailability();
    }

    private void checkAvailability() {
        String executable = config.piperExecutable();
        Path execPath = Path.of(executable);

        if (Files.exists(execPath) && Files.isExecutable(execPath)) {
            available = true;
            return;
        }

        try {
            available = ProcessRunner.run(
                    new ProcessBuilder(executable, "--help"), CHECK_TIMEOUT).succeeded();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            available = false;
        } catch (Exception e) {
            available = false;
        }
    }

    @Override
    public byte[] generateSpeech(@NotNull String text, @NotNull String voice) {
        Path tempInput = null;
        Path tempOutput = null;
        try {
            tempInput = Files.createTempFile("npcvoice_piper_input_", ".txt");
            tempOutput = Files.createTempFile("npcvoice_piper_output_", ".wav");

            Files.writeString(tempInput, text);

            String model = config.resolveVoiceId(voice, name());
            Path modelPath = downloadModelIfNeeded(model);

            ProcessBuilder pb = new ProcessBuilder(
                    config.piperExecutable(),
                    "--model", modelPath.toString(),
                    "--output", tempOutput.toString()
            );

            pb.redirectInput(tempInput.toFile());
            ProcessRunner.Result result = ProcessRunner.run(pb, GENERATION_TIMEOUT);
            if (!result.succeeded()) {
                LOGGER.warning(result.timedOut()
                        ? "Piper TTS timed out after " + GENERATION_TIMEOUT.toSeconds() + " seconds"
                        : "Piper TTS failed with exit code " + result.exitCode() + ": " + result.output());
                return null;
            }

            return Files.readAllBytes(tempOutput);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Piper generation was interrupted", e);
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate Piper speech", e);
            return null;
        } finally {
            cleanTempFiles(tempInput, tempOutput);
        }
    }

    private Path downloadModelIfNeeded(String model) throws IOException {
        Files.createDirectories(modelsDirectory);
        Path modelPath = SafePaths.resolveDirectChild(modelsDirectory, model, ".onnx")
                .orElseThrow(() -> new IOException("Invalid Piper model name: " + model));

        if (Files.exists(modelPath)) {
            return modelPath;
        }

        LOGGER.info("Downloading Piper model: " + model);
        String url = config.piperUrl();

        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
        HttpSupport.configure(connection, config.httpConnectTimeoutMs(), config.httpReadTimeoutMs());
        Path partial = modelPath.resolveSibling(modelPath.getFileName() + ".part");
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Piper model download returned HTTP " + responseCode
                        + HttpSupport.readError(connection));
            }
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, partial, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(partial, modelPath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
            Files.deleteIfExists(partial);
        }

        LOGGER.info("Model downloaded: " + modelPath);
        return modelPath;
    }

    private void cleanTempFiles(Path... paths) {
        for (Path path : paths) {
            if (path == null) continue;
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {}
        }
    }

    @Override
    public @NotNull String name() {
        return "piper";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
