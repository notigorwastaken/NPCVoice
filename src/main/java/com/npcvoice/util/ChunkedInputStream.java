package com.npcvoice.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

/**
 * An {@link InputStream} backed by a blocking queue of byte chunks. Reads block
 * until the next chunk arrives, allowing audio to be decoded incrementally
 * while it is still being downloaded/generated.
 * <p>
 * An empty byte array is treated as the end-of-stream sentinel.
 */
public final class ChunkedInputStream extends InputStream {

    private final BlockingQueue<byte[]> queue;
    private byte[] current;
    private int pos;
    private boolean eof;

    private byte[] pendingBuf;
    private int pendingLen;
    private int pendingPos;

    public ChunkedInputStream(BlockingQueue<byte[]> queue) {
        this.queue = queue;
    }

    /**
     * Reads up to {@code n} bytes without consuming them. The peeked bytes will
     * still be returned by subsequent {@link #read} calls.
     */
    public byte[] peek(int n) throws IOException {
        if (n <= 0) return new byte[0];
        if (pendingBuf == null) {
            pendingBuf = new byte[n];
        } else if (pendingBuf.length < n) {
            pendingBuf = Arrays.copyOf(pendingBuf, n);
        }
        while (pendingLen < n) {
            int got = readInternal(pendingBuf, pendingLen, n - pendingLen);
            if (got < 0) break;
            if (got == 0) continue;
            pendingLen += got;
        }
        return Arrays.copyOf(pendingBuf, Math.min(pendingLen, n));
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int r = read(one, 0, 1);
        return r == -1 ? -1 : one[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (pendingLen > pendingPos) {
            int n = Math.min(len, pendingLen - pendingPos);
            System.arraycopy(pendingBuf, pendingPos, b, off, n);
            pendingPos += n;
            if (pendingPos >= pendingLen) {
                pendingBuf = null;
                pendingLen = 0;
                pendingPos = 0;
            }
            return n;
        }
        return readInternal(b, off, len);
    }

    private int readInternal(byte[] b, int off, int len) throws IOException {
        if (eof) return -1;
        if (current == null || pos >= current.length) {
            try {
                current = queue.take();
                if (current.length == 0) {
                    eof = true;
                    return -1;
                }
                pos = 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for audio data", e);
            }
        }
        int n = Math.min(len, current.length - pos);
        System.arraycopy(current, pos, b, off, n);
        pos += n;
        return n;
    }
}
