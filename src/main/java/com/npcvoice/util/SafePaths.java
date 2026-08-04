package com.npcvoice.util;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves user- or configuration-provided file stems without allowing them to
 * escape the directory owned by the plugin.
 */
public final class SafePaths {

    private SafePaths() {
    }

    public static Optional<Path> resolveDirectChild(
            @NotNull Path directory,
            @NotNull String fileStem,
            @NotNull String extension
    ) {
        String trimmed = fileStem.trim();
        if (trimmed.isEmpty()
                || trimmed.equals(".")
                || trimmed.equals("..")
                || trimmed.indexOf('/') >= 0
                || trimmed.indexOf('\\') >= 0
                || trimmed.indexOf('\0') >= 0) {
            return Optional.empty();
        }

        Path root = directory.toAbsolutePath().normalize();
        Path candidate = root.resolve(trimmed + extension).normalize();
        if (!root.equals(candidate.getParent())) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }
}
