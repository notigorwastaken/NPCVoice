package com.npcvoice.util;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Runs a subprocess with bounded diagnostics and a hard timeout. */
public final class ProcessRunner {

    private static final int MAX_CAPTURED_OUTPUT = 16 * 1024;

    private ProcessRunner() {
    }

    public static Result run(@NotNull ProcessBuilder builder, @NotNull Duration timeout)
            throws IOException, InterruptedException {
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread drainer = Thread.ofVirtual()
                .name("npcvoice-process-output")
                .start(() -> drain(process.getInputStream(), captured));

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            terminate(process);
            drainer.interrupt();
            throw e;
        }

        if (!finished) {
            terminate(process);
        }
        drainer.join(2000);
        if (drainer.isAlive()) drainer.interrupt();

        int exitCode = finished ? process.exitValue() : -1;
        String output;
        synchronized (captured) {
            output = captured.toString(StandardCharsets.UTF_8).trim();
        }
        return new Result(exitCode, !finished, output);
    }

    private static void terminate(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static void drain(InputStream stream, ByteArrayOutputStream captured) {
        byte[] buffer = new byte[4096];
        try (stream) {
            int read;
            while ((read = stream.read(buffer)) != -1) {
                int remaining = MAX_CAPTURED_OUTPUT - captured.size();
                if (remaining > 0) {
                    synchronized (captured) {
                        remaining = MAX_CAPTURED_OUTPUT - captured.size();
                        if (remaining > 0) {
                            captured.write(buffer, 0, Math.min(read, remaining));
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    public record Result(int exitCode, boolean timedOut, String output) {
        public boolean succeeded() {
            return !timedOut && exitCode == 0;
        }
    }
}
