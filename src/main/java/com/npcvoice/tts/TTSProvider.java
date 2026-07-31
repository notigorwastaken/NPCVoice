package com.npcvoice.tts;

import org.jetbrains.annotations.NotNull;

public interface TTSProvider {

    byte[] generateSpeech(@NotNull String text, @NotNull String voice);

    @NotNull String name();

    boolean isAvailable();
}
