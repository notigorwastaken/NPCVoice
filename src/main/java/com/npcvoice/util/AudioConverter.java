package com.npcvoice.util;

import javazoom.jl.decoder.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class AudioConverter {

    private static final int TARGET_SAMPLE_RATE = 48000;

    private AudioConverter() {}

    public static short[] toPcmShorts(byte[] audioData) {
        if (audioData == null || audioData.length == 0) return new short[0];

        if (isWav(audioData)) {
            WavInfo info = parseWavHeader(audioData);
            if (info == null) return new short[0];
            short[] rawSamples = extractShortsFromWav(audioData, info);
            if (info.sampleRate == TARGET_SAMPLE_RATE) {
                return rawSamples;
            }
            return resample(rawSamples, info.sampleRate, TARGET_SAMPLE_RATE);
        }

        if (isMp3(audioData)) {
            return decodeMp3(audioData);
        }

        return bytesToShortsRaw(audioData);
    }

    public static boolean isWav(byte[] data) {
        return data != null && data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E';
    }

    public static boolean isMp3(byte[] data) {
        if (data == null || data.length < 2) return false;
        if (data.length >= 3 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') return true;
        return (data[0] & 0xFF) == 0xFF && (data[1] & 0xE0) == 0xE0;
    }

    public static short[] decodeMp3(byte[] mp3Data) {
        if (mp3Data == null || mp3Data.length == 0) return new short[0];

        try {
            Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3Data));
            Decoder decoder = new Decoder();

            int sampleRate = TARGET_SAMPLE_RATE;
            int totalSamples = 0;
            List<short[]> decodedFrames = new ArrayList<>();

            Header header;
            while ((header = bitstream.readFrame()) != null) {
                try {
                    Obuffer decoded = decoder.decodeFrame(header, bitstream);
                    if (!(decoded instanceof javazoom.jl.decoder.SampleBuffer buffer)) continue;
                    sampleRate = buffer.getSampleFrequency();
                    short[] mono = downmixInterleaved(
                            buffer.getBuffer(), buffer.getBufferLength(), buffer.getChannelCount());
                    if (mono.length > 0) {
                        decodedFrames.add(mono);
                        totalSamples += mono.length;
                    }
                } finally {
                    bitstream.closeFrame();
                }
            }

            bitstream.close();

            if (totalSamples == 0) return new short[0];

            short[] mono = new short[totalSamples];
            int offset = 0;
            for (short[] frame : decodedFrames) {
                System.arraycopy(frame, 0, mono, offset, frame.length);
                offset += frame.length;
            }

            if (sampleRate == TARGET_SAMPLE_RATE) return mono;
            return resample(mono, sampleRate, TARGET_SAMPLE_RATE);

        } catch (JavaLayerException e) {
            return new short[0];
        }
    }

    /**
     * Decodes an MP3 stream frame by frame, delivering mono PCM samples for each
     * decoded frame together with the frame's output frequency. The input stream
     * may be a blocking stream fed incrementally.
     */
    public static void decodeMp3Streaming(
            InputStream in,
            BiConsumer<short[], Integer> frameConsumer
    ) throws JavaLayerException {
        Bitstream bitstream = new Bitstream(in);
        Decoder decoder = new Decoder();

        Header header;
        while ((header = bitstream.readFrame()) != null) {
            try {
                Obuffer decoded = decoder.decodeFrame(header, bitstream);
                if (decoded instanceof javazoom.jl.decoder.SampleBuffer buffer) {
                    short[] mono = downmixInterleaved(
                            buffer.getBuffer(), buffer.getBufferLength(), buffer.getChannelCount());
                    if (mono.length > 0) {
                        frameConsumer.accept(mono, buffer.getSampleFrequency());
                    }
                }
            } finally {
                bitstream.closeFrame();
            }
        }

        bitstream.close();
    }

    public static short[] resamplePcm(byte[] pcmBytes, int inputRate) {
        if (pcmBytes == null || pcmBytes.length == 0) return new short[0];
        short[] rawSamples = bytesToShortsRaw(pcmBytes);
        if (inputRate == TARGET_SAMPLE_RATE) return rawSamples;
        return resample(rawSamples, inputRate, TARGET_SAMPLE_RATE);
    }

    private static WavInfo parseWavHeader(byte[] wavData) {
        if (!isWav(wavData)) return null;

        ByteBuffer buf = ByteBuffer.wrap(wavData).order(ByteOrder.LITTLE_ENDIAN);
        int audioFormat = -1;
        int channels = -1;
        int sampleRate = -1;
        int bitsPerSample = -1;
        int dataOffset = -1;
        int dataLength = -1;

        int position = 12;
        while (position <= wavData.length - 8) {
            int chunkSize = buf.getInt(position + 4);
            if (chunkSize < 0) return null;
            int chunkData = position + 8;
            long chunkEnd = (long) chunkData + chunkSize;
            if (chunkEnd > wavData.length) return null;

            if (matches(wavData, position, "fmt ") && chunkSize >= 16) {
                audioFormat = buf.getShort(chunkData) & 0xFFFF;
                channels = buf.getShort(chunkData + 2) & 0xFFFF;
                sampleRate = buf.getInt(chunkData + 4);
                bitsPerSample = buf.getShort(chunkData + 14) & 0xFFFF;
            } else if (matches(wavData, position, "data")) {
                dataOffset = chunkData;
                dataLength = chunkSize;
            }

            if (audioFormat >= 0 && dataOffset >= 0) break;
            position = (int) chunkEnd + (chunkSize & 1);
        }

        if (audioFormat != 1
                || channels <= 0
                || sampleRate <= 0
                || (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24)
                || dataOffset < 0
                || dataLength < 0) {
            return null;
        }
        return new WavInfo(channels, sampleRate, bitsPerSample, dataOffset, dataLength);
    }

    private static boolean matches(byte[] data, int offset, String value) {
        return offset >= 0
                && offset + value.length() <= data.length
                && data[offset] == value.charAt(0)
                && data[offset + 1] == value.charAt(1)
                && data[offset + 2] == value.charAt(2)
                && data[offset + 3] == value.charAt(3);
    }

    private static short[] extractShortsFromWav(byte[] wavData, WavInfo info) {
        int sampleBytes = info.bitsPerSample / 8;
        int totalSamples = info.dataLength / sampleBytes;
        int frames = totalSamples / info.channels;

        short[] mono = new short[frames];

        ByteBuffer buf = ByteBuffer.wrap(wavData, info.dataOffset, info.dataLength)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < frames; i++) {
            long sum = 0;
            for (int ch = 0; ch < info.channels; ch++) {
                int pos = (i * info.channels + ch) * sampleBytes;
                if (info.bitsPerSample == 16) {
                    sum += buf.getShort(pos);
                } else if (info.bitsPerSample == 24) {
                    int val = buf.get(pos) & 0xFF;
                    val |= (buf.get(pos + 1) & 0xFF) << 8;
                    val |= (buf.get(pos + 2) & 0xFF) << 16;
                    if ((val & 0x800000) != 0) val |= 0xFF000000;
                    sum += (short) (val >> 8);
                } else if (info.bitsPerSample == 8) {
                    sum += (short) (((buf.get(pos) & 0xFF) - 128) << 8);
                }
            }
            mono[i] = (short) (sum / info.channels);
        }

        return mono;
    }

    private static short[] bytesToShortsRaw(byte[] data) {
        int len = data.length / 2;
        short[] samples = new short[len];
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < len; i++) {
            samples[i] = buf.getShort();
        }
        return samples;
    }

    public static short[] resample(short[] input, int inputRate, int outputRate) {
        if (input == null || input.length == 0 || inputRate <= 0 || outputRate <= 0) {
            return new short[0];
        }
        if (inputRate == outputRate) return input;

        int outputLength = (int) ((long) input.length * outputRate / inputRate);
        short[] output = new short[outputLength];
        double ratio = (double) inputRate / outputRate;

        for (int i = 0; i < outputLength; i++) {
            double srcPos = i * ratio;
            int srcIndex = (int) srcPos;
            double frac = srcPos - srcIndex;

            if (srcIndex >= input.length - 1) {
                output[i] = input[input.length - 1];
            } else {
                output[i] = (short) (input[srcIndex] * (1.0 - frac) + input[srcIndex + 1] * frac);
            }
        }

        return output;
    }

    public static short[] scaleVolume(short[] input, double volume) {
        if (input == null || input.length == 0) return new short[0];
        double clamped = Math.clamp(volume, 0.0, 1.0);
        if (clamped == 1.0) return input;

        short[] output = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (short) Math.round(input[i] * clamped);
        }
        return output;
    }

    static short[] downmixInterleaved(short[] input, int sampleCount, int channels) {
        if (input == null || channels <= 0 || sampleCount <= 0) return new short[0];
        int boundedCount = Math.min(sampleCount, input.length);
        int frames = boundedCount / channels;
        short[] mono = new short[frames];
        for (int frame = 0; frame < frames; frame++) {
            long sum = 0;
            int offset = frame * channels;
            for (int channel = 0; channel < channels; channel++) {
                sum += input[offset + channel];
            }
            mono[frame] = (short) (sum / channels);
        }
        return mono;
    }

    private record WavInfo(int channels, int sampleRate, int bitsPerSample, int dataOffset, int dataLength) {
    }
}
