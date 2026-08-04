package com.npcvoice.tts;

import com.google.gson.JsonObject;
import com.npcvoice.config.ConfigManager;
import com.npcvoice.util.HttpSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OpenAITTS implements TTSProvider, StreamingTTSProvider {

    private static final Logger LOGGER = Logger.getLogger(OpenAITTS.class.getName());
    private final ConfigManager config;
    private final boolean available;

    public OpenAITTS(ConfigManager config) {
        this.config = config;
        this.available = config.openaiApiKey() != null && !config.openaiApiKey().isBlank();
    }

    @Override
    public byte[] generateSpeech(@NotNull String text, @NotNull String voice) {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(text, voice);
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOGGER.warning("OpenAI TTS API returned code " + responseCode + HttpSupport.readError(conn));
                return null;
            }

            return conn.getInputStream().readAllBytes();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate OpenAI speech", e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Override
    public @Nullable Iterator<byte[]> generateSpeechStream(@NotNull String text, @NotNull String voice) {
        HttpURLConnection conn;
        try {
            conn = openConnection(text, voice);
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOGGER.warning("OpenAI TTS API returned code " + responseCode + HttpSupport.readError(conn));
                conn.disconnect();
                return null;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to start OpenAI TTS stream for: " + text, e);
            return null;
        }

        return new HttpChunkIterator(conn);
    }

    private HttpURLConnection openConnection(String text, String voice) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", config.openaiModel());
        payload.addProperty("input", text);
        payload.addProperty("voice", config.resolveVoiceId(voice, name()));

        URL url = URI.create(config.openaiApiUrl()).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        HttpSupport.configure(conn, config.httpConnectTimeoutMs(), config.httpReadTimeoutMs());
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + config.openaiApiKey());
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        } catch (IOException | RuntimeException e) {
            conn.disconnect();
            throw e;
        }
        return conn;
    }

    @Override
    public @NotNull String name() {
        return "openai";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public @NotNull String cacheKey() {
        return name() + ":" + config.openaiModel();
    }
}
