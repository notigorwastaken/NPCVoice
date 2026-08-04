package com.npcvoice.tts;

import org.jetbrains.annotations.NotNull;

public interface TTSProvider {

    byte[] generateSpeech(@NotNull String text, @NotNull String voice);

    @NotNull String name();

    boolean isAvailable();

    /** Identifies provider settings that affect generated audio for cache invalidation. */
    default @NotNull String cacheKey() {
        return name();
    }

    default boolean supportsStreaming() {
        return false;
    }
}
