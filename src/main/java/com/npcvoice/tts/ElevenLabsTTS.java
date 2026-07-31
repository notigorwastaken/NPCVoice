package com.npcvoice.tts;

import com.google.gson.JsonObject;
import com.npcvoice.config.ConfigManager;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ElevenLabsTTS implements TTSProvider {

    private static final Logger LOGGER = Logger.getLogger(ElevenLabsTTS.class.getName());

    private final ConfigManager config;
    private final boolean available;

    public ElevenLabsTTS(ConfigManager config) {
        this.config = config;
        this.available = config.elevenLabsApiKey() != null && !config.elevenLabsApiKey().isBlank();
    }

    @Override
    public byte[] generateSpeech(@NotNull String text, @NotNull String voice) {
        try {
            String voiceId = config.resolveVoiceId(voice);
            String apiUrl = config.elevenLabsApiUrl() + "/" + voiceId;

            JsonObject payload = new JsonObject();
            payload.addProperty("text", text);
            payload.addProperty("model_id", config.elevenLabsModel());

            URL url = URI.create(apiUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "audio/mpeg");
            conn.setRequestProperty("xi-api-key", config.elevenLabsApiKey());
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOGGER.warning("ElevenLabs API returned code " + responseCode);
                return null;
            }

            return conn.getInputStream().readAllBytes();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate ElevenLabs speech for: " + text, e);
            return null;
        }
    }

    @Override
    public @NotNull String name() {
        return "elevenlabs";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
