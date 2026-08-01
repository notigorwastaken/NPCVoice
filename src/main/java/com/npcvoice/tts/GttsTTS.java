package com.npcvoice.tts;

import com.npcvoice.config.ConfigManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Google Translate TTS via the {@code gtts-cli} command line tool.
 * Free and requires no API key.
 */
public final class GttsTTS implements TTSProvider {

    private static final Logger LOGGER = Logger.getLogger(GttsTTS.class.getName());

    private final ConfigManager config;
    private final boolean available;

    public GttsTTS(ConfigManager config) {
        this.config = config;
        this.available = checkGttsAvailable();
    }

    private boolean checkGttsAvailable() {
        try {
            Process process = new ProcessBuilder(config.gttsExecutable(), "--help")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public byte[] generateSpeech(@NotNull String text, @NotNull String voice) {
        try {
            Path tempOutput = Files.createTempFile("npcvoice_gtts_output_", ".mp3");

            ProcessBuilder pb = new ProcessBuilder(
                    config.gttsExecutable(),
                    "--text", text,
                    "--lang", config.resolveVoiceId(voice),
                    "--output", tempOutput.toString()
            );
            if (config.gttsSlow()) {
                pb.command().add("--slow");
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String error = new String(process.getInputStream().readAllBytes());
                LOGGER.warning("gTTS failed with exit code " + exitCode + ": " + error);
                Files.deleteIfExists(tempOutput);
                return null;
            }

            byte[] audioData = Files.readAllBytes(tempOutput);
            Files.deleteIfExists(tempOutput);

            return audioData;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate gTTS speech for: " + text, e);
            return null;
        }
    }

    @Override
    public @NotNull String name() {
        return "gtts";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
