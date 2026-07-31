package com.npcvoice.tts;

import com.npcvoice.config.ConfigManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EdgeTTS implements TTSProvider {

    private static final Logger LOGGER = Logger.getLogger(EdgeTTS.class.getName());

    private final ConfigManager config;
    private final boolean available;

    public EdgeTTS(ConfigManager config) {
        this.config = config;
        this.available = checkEdgeTtsAvailable();
    }

    private boolean checkEdgeTtsAvailable() {
        try {
            Process process = new ProcessBuilder("edge-tts", "--help")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public byte[] generateSpeech(@NotNull String text, @NotNull String voice) {
        try {
            Path tempOutput = Files.createTempFile("npcvoice_edgetts_output_", ".mp3");

            String voiceId = config.resolveVoiceId(voice);

            ProcessBuilder pb = new ProcessBuilder(
                    "edge-tts",
                    "--voice", voiceId,
                    "--text", text,
                    "--write-media", tempOutput.toString()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String error = new String(process.getInputStream().readAllBytes());
                LOGGER.warning("Edge TTS failed with exit code " + exitCode + ": " + error);
                return null;
            }

            byte[] audioData = Files.readAllBytes(tempOutput);
            Files.deleteIfExists(tempOutput);

            return audioData;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate Edge TTS speech for: " + text, e);
            return null;
        }
    }

    @Override
    public @NotNull String name() {
        return "edgetts";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
