package com.npcvoice.tts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * A TTS provider that is able to generate audio incrementally.
 * <p>
 * Providers implementing this interface return an iterator of raw audio chunks
 * (e.g. MP3 fragments) that can be decoded and played while still downloading.
 * Returning {@code null} means the streaming setup failed and the caller should
 * fall back to the regular {@link TTSProvider#generateSpeech} path.
 */
public interface StreamingTTSProvider extends TTSProvider {

    default boolean supportsStreaming() {
        return true;
    }

    /**
     * @return an iterator over raw encoded audio chunks (MP3), or {@code null} if streaming failed
     */
    @Nullable
    Iterator<byte[]> generateSpeechStream(@NotNull String text, @NotNull String voice);
}
