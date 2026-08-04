package com.npcvoice.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkedInputStreamTest {

    @Test
    void peekDoesNotConsumeBytesAcrossQueuedChunks() throws Exception {
        LinkedBlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();
        chunks.add(new byte[]{1, 2});
        chunks.add(new byte[]{3, 4});
        chunks.add(new byte[0]);

        ChunkedInputStream stream = new ChunkedInputStream(chunks);

        assertArrayEquals(new byte[]{1, 2, 3, 4}, stream.peek(4));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, stream.readAllBytes());
        assertEquals(-1, stream.read());
    }
}
