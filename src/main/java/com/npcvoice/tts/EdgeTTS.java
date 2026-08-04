package com.npcvoice.tts;

import com.npcvoice.config.ConfigManager;
import com.npcvoice.util.ProcessRunner;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EdgeTTS implements TTSProvider {

    private static final Logger LOGGER = Logger.getLogger(EdgeTTS.class.getName());
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration GENERATION_TIMEOUT = Duration.ofMinutes(2);

    private final ConfigManager config;
    private final boolean available;

    public EdgeTTS(ConfigManager config) {
        this.config = config;
        this.available = checkEdgeTtsAvailable();
    }

    private boolean checkEdgeTtsAvailable() {
        try {
            return ProcessRunner.run(new ProcessBuilder("edge-tts", "--help"), CHECK_TIMEOUT).succeeded();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public byte[] generateSpeech(@NotNull String text, @NotNull String voice) {
        Path tempOutput = null;
        try {
            tempOutput = Files.createTempFile("npcvoice_edgetts_output_", ".mp3");

            String voiceId = config.resolveVoiceId(voice, name());

            ProcessBuilder pb = new ProcessBuilder(
                    "edge-tts",
                    "--voice", voiceId,
                    "--text", text,
                    "--rate", String.format(Locale.ROOT, "%+d%%", config.edgeTtsRate()),
                    "--volume", String.format(Locale.ROOT, "%+d%%", config.edgeTtsVolume()),
                    "--pitch", String.format(Locale.ROOT, "%+dHz", config.edgeTtsPitch()),
                    "--write-media", tempOutput.toString()
            );

            ProcessRunner.Result result = ProcessRunner.run(pb, GENERATION_TIMEOUT);
            if (!result.succeeded()) {
                LOGGER.warning(result.timedOut()
                        ? "Edge TTS timed out after " + GENERATION_TIMEOUT.toSeconds() + " seconds"
                        : "Edge TTS failed with exit code " + result.exitCode() + ": " + result.output());
                return null;
            }

            return Files.readAllBytes(tempOutput);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Edge TTS generation was interrupted", e);
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate Edge TTS speech", e);
            return null;
        } finally {
            if (tempOutput != null) {
                try {
                    Files.deleteIfExists(tempOutput);
                } catch (Exception ignored) {
                }
            }
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

    @Override
    public @NotNull String cacheKey() {
        return name() + ":" + config.edgeTtsRate() + ":" + config.edgeTtsVolume() + ":" + config.edgeTtsPitch();
    }
}
