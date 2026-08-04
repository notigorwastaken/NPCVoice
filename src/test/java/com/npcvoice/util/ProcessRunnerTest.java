package com.npcvoice.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessRunnerTest {

    @Test
    void capturesProcessOutput() throws Exception {
        ProcessRunner.Result result = ProcessRunner.run(
                new ProcessBuilder(javaExecutable(), "-version"), Duration.ofSeconds(10));

        assertTrue(result.succeeded());
        assertTrue(result.output().contains("version"));
    }

    @Test
    void terminatesAProcessThatExceedsItsDeadline() throws Exception {
        String testClasses = Path.of(ProcessRunnerTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString();
        ProcessRunner.Result result = ProcessRunner.run(
                new ProcessBuilder(javaExecutable(), "-cp", testClasses, SleepingProcess.class.getName()),
                Duration.ofMillis(100));

        assertTrue(result.timedOut());
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    public static final class SleepingProcess {
        public static void main(String[] args) throws Exception {
            Thread.sleep(10_000);
        }
    }
}
