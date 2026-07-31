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

public final class OpenAITTS implements TTSProvider {

    private static final Logger LOGGER = Logger.getLogger(OpenAITTS.class.getName());

    private final ConfigManager config;
    private final boolean available;

    public OpenAITTS(ConfigManager config) {
        this.config = config;
        this.available = config.openaiApiKey() != null && !config.openaiApiKey().isBlank();
    }

    @Override
    public byte[] generateSpeech(@NotNull String text, @NotNull String voice) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("model", config.openaiModel());
            payload.addProperty("input", text);
            payload.addProperty("voice", config.openaiVoice());

            URL url = URI.create(config.openaiApiUrl()).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + config.openaiApiKey());
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOGGER.warning("OpenAI TTS API returned code " + responseCode);
                return null;
            }

            return conn.getInputStream().readAllBytes();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate OpenAI speech for: " + text, e);
            return null;
        }
    }

    @Override
    public @NotNull String name() {
        return "openai";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
