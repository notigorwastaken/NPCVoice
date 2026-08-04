package com.npcvoice.util;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingAudioDecoderTest {

    @Test
    void preservesRawPcmSamplesSplitAcrossOddChunkBoundaries() throws Exception {
        short[] expected = {1, 2, 3, 4, 5, 6, 7, 8};
        ByteBuffer bytes = ByteBuffer.allocate(expected.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : expected) bytes.putShort(sample);
        byte[] raw = bytes.array();

        List<byte[]> chunks = List.of(
                slice(raw, 0, 3),
                slice(raw, 3, 7),
                slice(raw, 7, 13),
                slice(raw, 13, raw.length));

        BlockingQueue<StreamingAudioDecoder.PcmChunk> output =
                StreamingAudioDecoder.decodeAsync(chunks.iterator(), 32);

        StreamingAudioDecoder.PcmChunk audio = output.poll(5, TimeUnit.SECONDS);
        StreamingAudioDecoder.PcmChunk end = output.poll(5, TimeUnit.SECONDS);
        assertNotNull(audio);
        assertNotNull(end);
        assertArrayEquals(expected, audio.samples);
        assertTrue(end.isEnd());
    }

    private static byte[] slice(byte[] source, int from, int to) {
        byte[] result = new byte[to - from];
        System.arraycopy(source, from, result, 0, result.length);
        return result;
    }
}
