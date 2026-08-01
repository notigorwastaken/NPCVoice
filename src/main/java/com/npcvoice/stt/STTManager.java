package com.npcvoice.stt;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.npcvoice.NPCVoicePlugin;
import com.npcvoice.config.ConfigManager;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Speech-to-text transcription for the speak-to-speak feature.
 * Supports OpenAI Whisper and Google Cloud Speech-to-Text.
 */
public final class STTManager {

    private final NPCVoicePlugin plugin;
    private final ConfigManager configManager;

    public STTManager(NPCVoicePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public CompletableFuture<Optional<String>> transcribeAsync(byte @NotNull [] wavBytes) {
        String provider = configManager.sttProvider();
        if (provider == null || provider.equalsIgnoreCase("none")) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return switch (provider.toLowerCase()) {
                    case "openai" -> transcribeWithOpenAI(wavBytes);
                    case "google" -> transcribeWithGoogle(wavBytes);
                    default -> {
                        plugin.getLogger().warning("Unknown STT provider: " + provider);
                        yield Optional.empty();
                    }
                };
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Speech-to-text transcription failed", e);
                return Optional.empty();
            }
        });
    }

    private Optional<String> transcribeWithOpenAI(byte[] wavBytes) throws Exception {
        if (configManager.sttOpenaiApiKey().isBlank()) {
            plugin.getLogger().warning("OpenAI STT api_key is not configured.");
            return Optional.empty();
        }

        String boundary = "----npcvoice" + UUID.randomUUID();
        byte[] body = buildMultipartBody(wavBytes, boundary, configManager.sttOpenaiModel());

        URL url = URI.create(configManager.sttOpenaiUrl()).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + configManager.sttOpenaiApiKey());
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String error = new String(conn.getErrorStream() != null ? conn.getErrorStream().readAllBytes() : new byte[0]);
            plugin.getLogger().warning("OpenAI STT API returned code " + responseCode + ": " + error);
            return Optional.empty();
        }

        String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
        if (!response.has("text")) return Optional.empty();
        return Optional.ofNullable(response.get("text").getAsString()).map(String::trim).filter(s -> !s.isEmpty());
    }

    private byte[] buildMultipartBody(byte[] wavBytes, String boundary, String model) {
        String fileHeader = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n";
        String modelHeader = "\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"model\"\r\n\r\n";
        String closing = "\r\n--" + boundary + "--\r\n";

        byte[] fileHeaderBytes = fileHeader.getBytes(StandardCharsets.UTF_8);
        byte[] modelHeaderBytes = modelHeader.getBytes(StandardCharsets.UTF_8);
        byte[] modelBytes = model.getBytes(StandardCharsets.UTF_8);
        byte[] closingBytes = closing.getBytes(StandardCharsets.UTF_8);

        byte[] body = new byte[fileHeaderBytes.length + wavBytes.length + modelHeaderBytes.length
                + modelBytes.length + closingBytes.length];
        int offset = 0;
        System.arraycopy(fileHeaderBytes, 0, body, offset, fileHeaderBytes.length);
        offset += fileHeaderBytes.length;
        System.arraycopy(wavBytes, 0, body, offset, wavBytes.length);
        offset += wavBytes.length;
        System.arraycopy(modelHeaderBytes, 0, body, offset, modelHeaderBytes.length);
        offset += modelHeaderBytes.length;
        System.arraycopy(modelBytes, 0, body, offset, modelBytes.length);
        offset += modelBytes.length;
        System.arraycopy(closingBytes, 0, body, offset, closingBytes.length);
        return body;
    }

    private Optional<String> transcribeWithGoogle(byte[] wavBytes) throws Exception {
        if (configManager.sttGoogleApiKey().isBlank()) {
            plugin.getLogger().warning("Google STT api_key is not configured.");
            return Optional.empty();
        }

        JsonObject config = new JsonObject();
        config.addProperty("encoding", "LINEAR16");
        config.addProperty("sampleRateHertz", 48000);
        config.addProperty("languageCode", configManager.sttGoogleLanguageCode());

        JsonObject audio = new JsonObject();
        audio.addProperty("content", Base64.getEncoder().encodeToString(wavBytes));

        JsonObject payload = new JsonObject();
        payload.add("config", config);
        payload.add("audio", audio);

        URL url = URI.create(configManager.sttGoogleUrl() + "?key=" + configManager.sttGoogleApiKey()).toURL();
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
            plugin.getLogger().warning("Google STT API returned code " + responseCode);
            return Optional.empty();
        }

        String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
        if (response.has("results")) {
            var results = response.getAsJsonArray("results");
            if (!results.isEmpty()) {
                var first = results.get(0).getAsJsonObject();
                if (first.has("alternatives")) {
                    var alternatives = first.getAsJsonArray("alternatives");
                    if (!alternatives.isEmpty()) {
                        String transcript = alternatives.get(0).getAsJsonObject().get("transcript").getAsString();
                        return Optional.of(transcript.trim()).filter(s -> !s.isEmpty());
                    }
                }
            }
        }
        return Optional.empty();
    }
}
