package com.npcvoice.api;

import com.npcvoice.dialogue.DialogueManager;
import com.npcvoice.voice.VoiceManager;
import net.citizensnpcs.api.npc.NPC;
import org.jetbrains.annotations.NotNull;

public final class NPCVoiceAPI {

    private static VoiceManager voiceManager;
    private static DialogueManager dialogueManager;

    private NPCVoiceAPI() {}

    public static void initialize(@NotNull VoiceManager vm, @NotNull DialogueManager dm) {
        voiceManager = vm;
        dialogueManager = dm;
    }

    public static void speak(@NotNull NPC npc, @NotNull String text) {
        if (voiceManager == null) return;
        voiceManager.speak(npc, text);
    }

    public static void speak(@NotNull NPC npc, @NotNull String text, @NotNull String voice) {
        if (voiceManager == null) return;
        voiceManager.speak(npc, text, voice);
    }

    public static void stop(@NotNull NPC npc) {
        if (voiceManager == null) return;
        voiceManager.stop(npc);
    }

    public static boolean isSpeaking(@NotNull NPC npc) {
        if (voiceManager == null) return false;
        return voiceManager.isSpeaking(npc);
    }

    public static void shutdown() {
        voiceManager = null;
        dialogueManager = null;
    }
}
