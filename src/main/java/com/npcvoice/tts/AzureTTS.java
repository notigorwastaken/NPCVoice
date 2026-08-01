package com.npcvoice.tts;

import com.npcvoice.config.ConfigManager;
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

/**
 * Microsoft Azure Cognitive Services Text-to-Speech (REST API).
 * Supports streaming audio responses.
 */
public final class AzureTTS implements TTSProvider, StreamingTTSProvider {

    private static final Logger LOGGER = Logger.getLogger(AzureTTS.class.getName());

    private final ConfigManager config;
    private final boolean available;

    public AzureTTS(ConfigManager config) {
        this.config = config;
        this.available = config.azureApiKey() != null && !config.azureApiKey().isBlank()
                && config.azureRegion() != null && !config.azureRegion().isBlank();
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    @Override
    public byte[] generateSpeech(@NotNull String text, @NotNull String voice) {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(text, voice);
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOGGER.warning("Azure TTS API returned code " + responseCode);
                return null;
            }

            return conn.getInputStream().readAllBytes();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate Azure speech for: " + text, e);
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
                LOGGER.warning("Azure TTS API returned code " + responseCode);
                return null;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to start Azure stream for: " + text, e);
            return null;
        }

        return new HttpChunkIterator(conn);
    }

    private HttpURLConnection openConnection(String text, String voice) throws IOException {
        String urlString = "https://" + config.azureRegion() + ".tts.speech.microsoft.com/cognitiveservices/v1";
        String ssml = buildSsml(text, config.resolveVoiceId(voice));

        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/ssml+xml");
        conn.setRequestProperty("Accept", "audio/mpeg");
        conn.setRequestProperty("X-Microsoft-OutputFormat", "audio-24khz-96kbitrate-mono-mp3");
        conn.setRequestProperty("Ocp-Apim-Subscription-Key", config.azureApiKey());
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] body = ssml.getBytes(StandardCharsets.UTF_8);
            os.write(body, 0, body.length);
        }
        return conn;
    }

    private String buildSsml(String text, String voice) {
        String rate = String.format("%+.0f%%", (config.azureSpeed() - 1.0) * 100.0);
        String escaped = escapeXml(text);
        return "<?xml version='1.0' encoding='UTF-8'?>"
                + "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' "
                + "xml:lang='" + voice + "'>"
                + "<voice name='" + config.azureVoice() + "'>"
                + "<prosody pitch='" + config.azurePitch() + "' rate='" + rate + "'>"
                + escaped
                + "</prosody></voice></speak>";
    }

    @Override
    public @NotNull String name() {
        return "azure";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
