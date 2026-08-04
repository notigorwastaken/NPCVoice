package com.npcvoice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioConverterTest {

    @Test
    void roundTripsPcmThroughAStandardWavContainer() {
        short[] pcm = {Short.MIN_VALUE, -10_000, 0, 10_000, Short.MAX_VALUE};

        byte[] wav = WavWriter.toWavBytes(pcm, 48_000, 1);

        assertTrue(AudioConverter.isWav(wav));
        assertArrayEquals(pcm, AudioConverter.toPcmShorts(wav));
    }

    @Test
    void downmixesInterleavedStereoWithoutDroppingAChannel() {
        short[] stereo = {1_000, 3_000, -4_000, 2_000, 8_000, 4_000};

        assertArrayEquals(
                new short[]{2_000, -1_000, 6_000},
                AudioConverter.downmixInterleaved(stereo, stereo.length, 2));
    }

    @Test
    void rejectsTruncatedOrInvalidWavChunks() {
        byte[] wav = WavWriter.toWavBytes(new short[]{1, 2}, 48_000, 1);
        wav[40] = (byte) 0xFF;
        wav[41] = (byte) 0xFF;
        wav[42] = (byte) 0xFF;
        wav[43] = 0x7F;

        assertArrayEquals(new short[0], AudioConverter.toPcmShorts(wav));
        assertFalse(AudioConverter.isWav(null));
    }

    @Test
    void rejectsInvalidWavWriterArguments() {
        assertThrows(NullPointerException.class, () -> WavWriter.toWavBytes(null, 48_000, 1));
        assertThrows(IllegalArgumentException.class, () -> WavWriter.toWavBytes(new short[0], 0, 1));
        assertThrows(IllegalArgumentException.class, () -> WavWriter.toWavBytes(new short[0], 48_000, 0));
    }

    @Test
    void invalidResamplingInputProducesNoSamples() {
        assertArrayEquals(new short[0], AudioConverter.resample(new short[]{1}, 0, 48_000));
        assertArrayEquals(new short[0], AudioConverter.resample(new short[0], 48_000, 48_000));
    }

    @Test
    void scalesVolumeWithoutMutatingTheSource() {
        short[] source = {10_000, -10_000, Short.MAX_VALUE};

        assertArrayEquals(new short[]{5_000, -5_000, 16_384}, AudioConverter.scaleVolume(source, 0.5));
        assertArrayEquals(new short[]{10_000, -10_000, Short.MAX_VALUE}, source);
        assertArrayEquals(new short[]{0, 0, 0}, AudioConverter.scaleVolume(source, -1));
    }
}
