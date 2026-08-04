package com.npcvoice.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public final class HttpSupport {

    private static final int MAX_ERROR_BYTES = 4096;

    private HttpSupport() {
    }

    public static void configure(
            @NotNull HttpURLConnection connection,
            int connectTimeoutMs,
            int readTimeoutMs
    ) {
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setUseCaches(false);
    }

    public static String readError(@NotNull HttpURLConnection connection) {
        InputStream stream = connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        try (stream) {
            byte[] bytes = stream.readNBytes(MAX_ERROR_BYTES);
            String body = new String(bytes, StandardCharsets.UTF_8).trim();
            return body.isEmpty() ? "" : ": " + body;
        } catch (IOException ignored) {
            return "";
        }
    }
}
