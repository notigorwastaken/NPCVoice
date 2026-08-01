package com.npcvoice.tts;

import com.google.gson.JsonObject;
import com.npcvoice.config.ConfigManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OpenAITTS implements TTSProvider, StreamingTTSProvider {

    private static final Logger LOGGER = Logger.getLogger(OpenAITTS.class.getName());
    private static final int CHUNK_SIZE = 8192;

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
                LOGGER.warning("OpenAI TTS API returned code " + responseCode);
                return null;
            }

            return conn.getInputStream().readAllBytes();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate OpenAI speech for: " + text, e);
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
                LOGGER.warning("OpenAI TTS API returned code " + responseCode);
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
        payload.addProperty("voice", config.resolveVoiceId(voice));

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

    private static final class HttpChunkIterator implements Iterator<byte[]> {
        private final HttpURLConnection conn;
        private final InputStream in;
        private final byte[] buffer;
        private byte[] pending;

        HttpChunkIterator(HttpURLConnection conn) {
            this.conn = conn;
            InputStream input;
            try {
                input = conn.getInputStream();
            } catch (IOException e) {
                input = null;
            }
            this.in = input;
            this.buffer = new byte[CHUNK_SIZE];
            this.pending = in != null ? readNext() : null;
        }

        @Override
        public boolean hasNext() {
            return pending != null;
        }

        @Override
        public byte[] next() {
            if (pending == null) throw new NoSuchElementException();
            byte[] current = pending;
            pending = readNext();
            return current;
        }

        private byte[] readNext() {
            try {
                int n = in.read(buffer);
                if (n <= 0) {
                    close();
                    return null;
                }
                return Arrays.copyOf(buffer, n);
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Audio stream ended early", e);
                close();
                return null;
            }
        }

        private void close() {
            try {
                in.close();
            } catch (IOException ignored) {
            }
            conn.disconnect();
        }
    }
}
