package com.npcvoice.util;

import javazoom.jl.decoder.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

public final class AudioConverter {

    private static final int TARGET_SAMPLE_RATE = 48000;

    private AudioConverter() {}

    public static short[] toPcmShorts(byte[] audioData) {
        if (audioData == null || audioData.length == 0) return new short[0];

        if (isWav(audioData)) {
            WavInfo info = parseWavHeader(audioData);
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
        return data.length > 44
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
            SampleBuffer buffer = new SampleBuffer();
            decoder.setOutputBuffer(buffer);

            int sampleRate = TARGET_SAMPLE_RATE;
            List<Short> allSamples = new ArrayList<>();

            Header header;
            while ((header = bitstream.readFrame()) != null) {
                sampleRate = decoder.getOutputFrequency();
                decoder.decodeFrame(header, bitstream);
                bitstream.closeFrame();

                short[] pcm = buffer.getSamples();
                int channels = decoder.getOutputChannels();
                int samplesPerChannel = pcm.length / channels;

                for (int i = 0; i < samplesPerChannel; i++) {
                    long sum = 0;
                    for (int ch = 0; ch < channels; ch++) {
                        sum += pcm[ch * samplesPerChannel + i];
                    }
                    allSamples.add((short) (sum / channels));
                }

                buffer.reset();
            }

            bitstream.close();

            if (allSamples.isEmpty()) return new short[0];

            short[] mono = new short[allSamples.size()];
            for (int i = 0; i < allSamples.size(); i++) {
                mono[i] = allSamples.get(i);
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
        SampleBuffer buffer = new SampleBuffer();
        decoder.setOutputBuffer(buffer);

        Header header;
        while ((header = bitstream.readFrame()) != null) {
            int sampleRate = decoder.getOutputFrequency();
            decoder.decodeFrame(header, bitstream);
            bitstream.closeFrame();

            short[] pcm = buffer.getSamples();
            int channels = decoder.getOutputChannels();
            int samplesPerChannel = pcm.length / channels;

            short[] mono = new short[samplesPerChannel];
            for (int i = 0; i < samplesPerChannel; i++) {
                long sum = 0;
                for (int ch = 0; ch < channels; ch++) {
                    sum += pcm[ch * samplesPerChannel + i];
                }
                mono[i] = (short) (sum / channels);
            }

            frameConsumer.accept(mono, sampleRate);
            buffer.reset();
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
        ByteBuffer buf = ByteBuffer.wrap(wavData, 0, 44).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(22);
        int channels = buf.getShort() & 0xFFFF;
        int sampleRate = buf.getInt();
        buf.position(34);
        int bitsPerSample = buf.getShort() & 0xFFFF;

        int dataOffset = 44;
        for (int i = 44; i < wavData.length - 4; i++) {
            if (wavData[i] == 'd' && wavData[i + 1] == 'a' && wavData[i + 2] == 't' && wavData[i + 3] == 'a') {
                dataOffset = i + 8;
                break;
            }
        }

        return new WavInfo(channels, sampleRate, bitsPerSample, dataOffset);
    }

    private static short[] extractShortsFromWav(byte[] wavData, WavInfo info) {
        int sampleBytes = info.bitsPerSample / 8;
        int totalSamples = (wavData.length - info.dataOffset) / sampleBytes;
        int frames = totalSamples / info.channels;

        short[] mono = new short[frames];

        ByteBuffer buf = ByteBuffer.wrap(wavData, info.dataOffset, wavData.length - info.dataOffset)
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

    private record WavInfo(int channels, int sampleRate, int bitsPerSample, int dataOffset) {
    }

    static final class SampleBuffer extends Obuffer {
        private short[] buffer;
        private final int[] bitPosition;

        SampleBuffer() {
            buffer = new short[4096];
            bitPosition = new int[2];
        }

        @Override
        public void append(int channel, short value) {
            if (bitPosition[channel] >= buffer.length) {
                buffer = Arrays.copyOf(buffer, buffer.length * 2);
            }
            buffer[bitPosition[channel]++] = value;
        }

        @Override
        public void appendSamples(int channel, float[] samples) {
            int pos = bitPosition[channel];
            int needed = pos + samples.length;
            if (needed > buffer.length) {
                buffer = Arrays.copyOf(buffer, Math.max(needed, buffer.length * 2));
            }
            for (float s : samples) {
                buffer[pos++] = (short) Math.clamp(s, -32767.0f, 32767.0f);
            }
            bitPosition[channel] = pos;
        }

        @Override
        public void write_buffer(int val) {}

        @Override
        public void close() {}

        @Override
        public void clear_buffer() {}

        @Override
        public void set_stop_flag() {}

        short[] getSamples() {
            return Arrays.copyOf(buffer, bitPosition[0] + bitPosition[1]);
        }

        void reset() {
            bitPosition[0] = 0;
            bitPosition[1] = 0;
        }
    }
}
