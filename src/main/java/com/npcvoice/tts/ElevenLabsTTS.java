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

public final class ElevenLabsTTS implements TTSProvider, StreamingTTSProvider {

    private static final Logger LOGGER = Logger.getLogger(ElevenLabsTTS.class.getName());

    private final ConfigManager config;
    private final boolean available;

    public ElevenLabsTTS(ConfigManager config) {
        this.config = config;
        this.available = config.elevenLabsApiKey() != null && !config.elevenLabsApiKey().isBlank();
    }

    @Override
    public byte[] generateSpeech(@NotNull String text, @NotNull String voice) {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(text, voice, false);
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOGGER.warning("ElevenLabs API returned code " + responseCode + HttpSupport.readError(conn));
                return null;
            }

            return conn.getInputStream().readAllBytes();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate ElevenLabs speech", e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Override
    public @Nullable Iterator<byte[]> generateSpeechStream(@NotNull String text, @NotNull String voice) {
        HttpURLConnection conn;
        try {
            conn = openConnection(text, voice, true);
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOGGER.warning("ElevenLabs API returned code " + responseCode + HttpSupport.readError(conn));
                conn.disconnect();
                return null;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to start ElevenLabs stream for: " + text, e);
            return null;
        }

        return new HttpChunkIterator(conn);
    }

    private HttpURLConnection openConnection(String text, String voice, boolean stream) throws IOException {
        String voiceId = config.resolveVoiceId(voice, name());
        String apiUrl = config.elevenLabsApiUrl() + "/" + voiceId;

        JsonObject payload = new JsonObject();
        payload.addProperty("text", text);
        payload.addProperty("model_id", config.elevenLabsModel());
        if (stream) {
            payload.addProperty("stream", true);
        }

        URL url = URI.create(apiUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        HttpSupport.configure(conn, config.httpConnectTimeoutMs(), config.httpReadTimeoutMs());
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "audio/mpeg");
        conn.setRequestProperty("xi-api-key", config.elevenLabsApiKey());
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
        return "elevenlabs";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public @NotNull String cacheKey() {
        return name() + ":" + config.elevenLabsModel();
    }
}
