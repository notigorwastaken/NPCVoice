package com.npcvoice.tts;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Iterates over an HTTP response body in chunks so it can be decoded while
 * still downloading.
 */
public final class HttpChunkIterator implements Iterator<byte[]> {

    private static final Logger LOGGER = Logger.getLogger(HttpChunkIterator.class.getName());
    private static final int CHUNK_SIZE = 8192;

    private final HttpURLConnection conn;
    private final InputStream in;
    private final byte[] buffer;
    private byte[] pending;

    public HttpChunkIterator(@NotNull HttpURLConnection conn) {
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
