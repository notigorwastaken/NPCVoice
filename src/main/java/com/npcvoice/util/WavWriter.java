package com.npcvoice.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Writes 16-bit PCM samples into a RIFF/WAVE container.
 */
public final class WavWriter {

    private WavWriter() {
    }

    public static byte[] toWavBytes(short[] pcm, int sampleRate, int channels) {
        Objects.requireNonNull(pcm, "pcm");
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive");
        if (channels <= 0) throw new IllegalArgumentException("channels must be positive");

        long dataSizeLong = (long) pcm.length * 2;
        if (dataSizeLong > Integer.MAX_VALUE - 44L) {
            throw new IllegalArgumentException("PCM data is too large for an in-memory WAV file");
        }
        int dataSize = (int) dataSizeLong;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(new byte[]{'R', 'I', 'F', 'F'});
        buffer.putInt(36 + dataSize);
        buffer.put(new byte[]{'W', 'A', 'V', 'E'});
        buffer.put(new byte[]{'f', 'm', 't', ' '});
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * channels * 2);
        buffer.putShort((short) (channels * 2));
        buffer.putShort((short) 16);
        buffer.put(new byte[]{'d', 'a', 't', 'a'});
        buffer.putInt(dataSize);

        for (short sample : pcm) {
            buffer.putShort(sample);
        }

        return buffer.array();
    }
}
