package com.npcvoice.tts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.npcvoice.config.ConfigManager;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Google Cloud Text-to-Speech (REST API). Requires an API key.
 */
public final class GoogleTTS implements TTSProvider {

    private static final Logger LOGGER = Logger.getLogger(GoogleTTS.class.getName());
    private static final String API_URL = "https://texttospeech.googleapis.com/v1/text:synthesize";

    private final ConfigManager config;
    private final boolean available;

    public GoogleTTS(ConfigManager config) {
        this.config = config;
        this.available = config.googleApiKey() != null && !config.googleApiKey().isBlank();
    }

    @Override
    public byte[] generateSpeech(@NotNull String text, @NotNull String voice) {
        try {
            JsonObject input = new JsonObject();
            input.addProperty("text", text);

            JsonObject voiceConfig = new JsonObject();
            voiceConfig.addProperty("languageCode", config.googleLanguageCode());
            voiceConfig.addProperty("name", config.resolveVoiceId(voice));

            JsonObject audioConfig = new JsonObject();
            audioConfig.addProperty("audioEncoding", "MP3");
            audioConfig.addProperty("pitch", config.googlePitch());
            audioConfig.addProperty("speakingRate", config.googleSpeed());

            JsonObject payload = new JsonObject();
            payload.add("input", input);
            payload.add("voice", voiceConfig);
            payload.add("audioConfig", audioConfig);

            URL url = URI.create(API_URL + "?key=" + config.googleApiKey()).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(body, 0, body.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOGGER.warning("Google Cloud TTS API returned code " + responseCode);
                return null;
            }

            String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
            String audioContent = response.get("audioContent").getAsString();

            return Base64.getDecoder().decode(audioContent);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate Google Cloud speech for: " + text, e);
            return null;
        }
    }

    @Override
    public @NotNull String name() {
        return "google";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
