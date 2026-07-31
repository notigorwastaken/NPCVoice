package com.npcvoice.tts;

import com.npcvoice.config.ConfigManager;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PiperTTS implements TTSProvider {

    private static final Logger LOGGER = Logger.getLogger(PiperTTS.class.getName());

    private final ConfigManager config;
    private boolean available;

    public PiperTTS(ConfigManager config) {
        this.config = config;
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
            Process process = new ProcessBuilder(executable, "--help")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            available = exitCode == 0;
        } catch (Exception e) {
            available = false;
        }
    }

    @Override
    public byte[] generateSpeech(@NotNull String text, @NotNull String voice) {
        try {
            Path tempInput = Files.createTempFile("npcvoice_piper_input_", ".txt");
            Path tempOutput = Files.createTempFile("npcvoice_piper_output_", ".wav");

            Files.writeString(tempInput, text);

            String model = config.resolveVoiceId(voice);
            Path modelPath = downloadModelIfNeeded(model);

            ProcessBuilder pb = new ProcessBuilder(
                    config.piperExecutable(),
                    "--model", modelPath.toString(),
                    "--output", tempOutput.toString()
            );

            pb.redirectInput(tempInput.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String error = new String(process.getInputStream().readAllBytes());
                LOGGER.warning("Piper TTS failed with exit code " + exitCode + ": " + error);
                cleanTempFiles(tempInput, tempOutput);
                return null;
            }

            byte[] audioData = Files.readAllBytes(tempOutput);
            cleanTempFiles(tempInput, tempOutput);

            return audioData;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate Piper speech for: " + text, e);
            return null;
        }
    }

    private Path downloadModelIfNeeded(String model) throws IOException {
        Path pluginFolder = new File("plugins/NPCVoice/models").toPath();
        Files.createDirectories(pluginFolder);

        String modelFileName = model + ".onnx";
        Path modelPath = pluginFolder.resolve(modelFileName);

        if (Files.exists(modelPath)) {
            return modelPath;
        }

        LOGGER.info("Downloading Piper model: " + model);
        String url = config.piperUrl();

        try (InputStream in = new java.net.URL(url).openStream()) {
            Files.copy(in, modelPath, StandardCopyOption.REPLACE_EXISTING);
        }

        LOGGER.info("Model downloaded: " + modelPath);
        return modelPath;
    }

    private void cleanTempFiles(Path... paths) {
        for (Path path : paths) {
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
