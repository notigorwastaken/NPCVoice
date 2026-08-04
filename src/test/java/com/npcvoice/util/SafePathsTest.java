package com.npcvoice.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafePathsTest {

    private final Path directory = Path.of("plugins", "NPCVoice", "audio");

    @Test
    void resolvesAFileDirectlyInsideTheOwnedDirectory() {
        Path resolved = SafePaths.resolveDirectChild(directory, "greeting", ".wav").orElseThrow();

        assertEquals(directory.toAbsolutePath().normalize().resolve("greeting.wav"), resolved);
    }

    @Test
    void rejectsTraversalAndDirectorySeparators() {
        assertTrue(SafePaths.resolveDirectChild(directory, "../server", ".wav").isEmpty());
        assertTrue(SafePaths.resolveDirectChild(directory, "folder/file", ".wav").isEmpty());
        assertTrue(SafePaths.resolveDirectChild(directory, "folder\\file", ".wav").isEmpty());
    }

    @Test
    void rejectsBlankAndDotNames() {
        assertTrue(SafePaths.resolveDirectChild(directory, " ", ".wav").isEmpty());
        assertTrue(SafePaths.resolveDirectChild(directory, ".", ".wav").isEmpty());
        assertTrue(SafePaths.resolveDirectChild(directory, "..", ".wav").isEmpty());
    }
}
