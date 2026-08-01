package com.npcvoice.api;

import com.npcvoice.config.ConfigManager;
import com.npcvoice.config.ConfigManager.NPCConfig;
import com.npcvoice.dialogue.DialogueManager;
import com.npcvoice.tts.TTSManager;
import com.npcvoice.voice.VoiceManager;
import net.citizensnpcs.api.npc.NPC;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class NPCVoiceAPI {

    private static VoiceManager voiceManager;
    private static DialogueManager dialogueManager;
    private static ConfigManager configManager;
    private static TTSManager ttsManager;

    private NPCVoiceAPI() {}

    public static void initialize(
            @NotNull VoiceManager vm,
            @NotNull DialogueManager dm,
            @NotNull ConfigManager cm,
            @NotNull TTSManager tm
    ) {
        voiceManager = vm;
        dialogueManager = dm;
        configManager = cm;
        ttsManager = tm;
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

    public static Optional<String> getNpcVoice(@NotNull String npcName) {
        if (configManager == null) return Optional.empty();
        NPCConfig cfg = configManager.npcConfig(npcName);
        return cfg == null ? Optional.empty() : cfg.voice();
    }

    public static Optional<String> getNpcProvider(@NotNull String npcName) {
        if (configManager == null) return Optional.empty();
        NPCConfig cfg = configManager.npcConfig(npcName);
        return cfg == null ? Optional.empty() : cfg.provider();
    }

    public static void setNpcVoice(@NotNull String npcName, @NotNull String voice) {
        if (configManager == null) return;
        configManager.setNpcVoice(npcName, voice);
    }

    public static void setNpcProvider(@NotNull String npcName, @NotNull String provider) {
        if (configManager == null) return;
        configManager.setNpcProvider(npcName, provider);
    }

    public static void setNpcSttEnabled(@NotNull String npcName, boolean enabled) {
        if (configManager == null) return;
        configManager.setNpcSttEnabled(npcName, enabled);
    }

    public static void saveNpcs() {
        if (configManager != null) configManager.saveNpcs();
    }

    public static void shutdown() {
        voiceManager = null;
        dialogueManager = null;
        configManager = null;
        ttsManager = null;
    }
}
