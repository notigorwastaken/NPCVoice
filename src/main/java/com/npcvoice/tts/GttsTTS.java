package com.npcvoice.tts;

import com.npcvoice.config.ConfigManager;
import com.npcvoice.util.ProcessRunner;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Google Translate TTS via the {@code gtts-cli} command line tool.
 * Free and requires no API key.
 */
public final class GttsTTS implements TTSProvider {

    private static final Logger LOGGER = Logger.getLogger(GttsTTS.class.getName());
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration GENERATION_TIMEOUT = Duration.ofMinutes(2);

    private final ConfigManager config;
    private final boolean available;

    public GttsTTS(ConfigManager config) {
        this.config = config;
        this.available = checkGttsAvailable();
    }

    private boolean checkGttsAvailable() {
        try {
            return ProcessRunner.run(
                    new ProcessBuilder(config.gttsExecutable(), "--help"), CHECK_TIMEOUT).succeeded();
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
            tempOutput = Files.createTempFile("npcvoice_gtts_output_", ".mp3");

            ProcessBuilder pb = new ProcessBuilder(
                    config.gttsExecutable(),
                    "--text", text,
                    "--lang", config.resolveVoiceId(voice, name()),
                    "--output", tempOutput.toString()
            );
            if (config.gttsSlow()) {
                pb.command().add("--slow");
            }

            ProcessRunner.Result result = ProcessRunner.run(pb, GENERATION_TIMEOUT);
            if (!result.succeeded()) {
                LOGGER.warning(result.timedOut()
                        ? "gTTS timed out after " + GENERATION_TIMEOUT.toSeconds() + " seconds"
                        : "gTTS failed with exit code " + result.exitCode() + ": " + result.output());
                return null;
            }

            return Files.readAllBytes(tempOutput);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "gTTS generation was interrupted", e);
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate gTTS speech", e);
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
        return "gtts";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public @NotNull String cacheKey() {
        return name() + ":" + config.gttsSlow();
    }
}
