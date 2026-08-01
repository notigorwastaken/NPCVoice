package com.npcvoice.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Converts an iterator of raw encoded audio chunks (MP3, WAV or raw PCM) into a
 * queue of PCM chunks that can be played while the audio is still arriving.
 * <p>
 * The returned queue is terminated with {@link PcmChunk#END} once all audio has
 * been decoded (or decoding failed).
 */
public final class StreamingAudioDecoder {

    private static final Logger LOGGER = Logger.getLogger(StreamingAudioDecoder.class.getName());
    private static final int TARGET_SAMPLE_RATE = 48000;
    private static final byte[] END_CHUNK = new byte[0];

    private StreamingAudioDecoder() {
    }

    public static BlockingQueue<PcmChunk> decodeAsync(Iterator<byte[]> rawChunks, int chunkSamples) {
        BlockingQueue<byte[]> rawQueue = new LinkedBlockingQueue<>();
        BlockingQueue<PcmChunk> pcmQueue = new LinkedBlockingQueue<>();

        Thread producer = new Thread(() -> {
            try {
                if (rawChunks != null) {
                    while (rawChunks.hasNext()) {
                        byte[] chunk = rawChunks.next();
                        if (chunk == null || chunk.length == 0) continue;
                        rawQueue.put(chunk);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    rawQueue.put(END_CHUNK);
                } catch (InterruptedException ignored) {
                }
            }
        }, "npcvoice-stream-producer");
        producer.setDaemon(true);
        producer.start();

        Thread decoder = new Thread(() -> {
            try {
                ChunkedInputStream in = new ChunkedInputStream(rawQueue);
                byte[] header = in.peek(12);

                if (isWav(header)) {
                    decodeWav(in, chunkSamples, pcmQueue);
                } else if (isMp3(header)) {
                    decodeMp3(in, chunkSamples, pcmQueue);
                } else {
                    decodeRaw(in, chunkSamples, pcmQueue);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to decode streaming audio", e);
            } finally {
                try {
                    pcmQueue.put(PcmChunk.END);
                } catch (InterruptedException ignored) {
                }
            }
        }, "npcvoice-stream-decoder");
        decoder.setDaemon(true);
        decoder.start();

        return pcmQueue;
    }

    private static boolean isWav(byte[] header) {
        return header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'A' && header[10] == 'V' && header[11] == 'E';
    }

    private static boolean isMp3(byte[] header) {
        if (header.length >= 3 && header[0] == 'I' && header[1] == 'D' && header[2] == '3') return true;
        return header.length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0;
    }

    private static void decodeMp3(ChunkedInputStream in, int chunkSamples, BlockingQueue<PcmChunk> out) throws Exception {
        Accumulator accumulator = new Accumulator(chunkSamples, out);
        try {
            AudioConverter.decodeMp3Streaming(in, accumulator::append);
        } finally {
            accumulator.flush();
        }
    }

    private static void decodeWav(ChunkedInputStream in, int chunkSamples, BlockingQueue<PcmChunk> out) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int read;
        while ((read = in.read(tmp)) != -1) {
            buffer.write(tmp, 0, read);
        }
        short[] pcm = AudioConverter.toPcmShorts(buffer.toByteArray());
        if (pcm.length == 0) return;

        Accumulator accumulator = new Accumulator(chunkSamples, out);
        accumulator.append(pcm, TARGET_SAMPLE_RATE);
        accumulator.flush();
    }

    private static void decodeRaw(ChunkedInputStream in, int chunkSamples, BlockingQueue<PcmChunk> out) throws IOException {
        Accumulator accumulator = new Accumulator(chunkSamples, out);
        byte[] tmp = new byte[4096];
        int read;
        while ((read = in.read(tmp)) != -1) {
            short[] chunk = bytesToShorts(tmp, read);
            accumulator.append(chunk, TARGET_SAMPLE_RATE);
        }
        accumulator.flush();
    }

    private static short[] bytesToShorts(byte[] data, int len) {
        int count = len / 2;
        short[] samples = new short[count];
        ByteBuffer buf = ByteBuffer.wrap(data, 0, len).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            samples[i] = buf.getShort();
        }
        return samples;
    }

    public static final class PcmChunk {
        public static final PcmChunk END = new PcmChunk(null);

        public final short[] samples;

        PcmChunk(short[] samples) {
            this.samples = samples;
        }

        public boolean isEnd() {
            return this == END;
        }
    }

    private static final class Accumulator {
        private final int chunkSamples;
        private final BlockingQueue<PcmChunk> out;
        private final short[] buffer;
        private int length;

        Accumulator(int chunkSamples, BlockingQueue<PcmChunk> out) {
            this.chunkSamples = chunkSamples;
            this.out = out;
            this.buffer = new short[chunkSamples];
        }

        void append(short[] samples, int sampleRate) {
            short[] resampled = sampleRate == TARGET_SAMPLE_RATE
                    ? samples
                    : AudioConverter.resample(samples, sampleRate, TARGET_SAMPLE_RATE);
            for (short s : resampled) {
                if (length >= buffer.length) {
                    emit();
                }
                buffer[length++] = s;
            }
        }

        void flush() {
            if (length > 0) {
                emit();
            }
        }

        private void emit() {
            try {
                out.put(new PcmChunk(Arrays.copyOf(buffer, length)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            length = 0;
        }
    }
}
