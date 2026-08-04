package com.npcvoice.dialogue;

import com.npcvoice.NPCVoicePlugin;
import com.npcvoice.config.ConfigManager;
import com.npcvoice.config.ConfigManager.NPCConfig;
import com.npcvoice.config.ConfigManager.SpeechConfig;
import com.npcvoice.util.PlaceholderUtil;
import com.npcvoice.voice.NPCVoiceSession;
import com.npcvoice.voice.VoiceManager;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class DialogueManager {

    private final NPCVoicePlugin plugin;
    private final ConfigManager configManager;
    private final VoiceManager voiceManager;
    private final PlaceholderUtil placeholderUtil;
    private final Map<UUID, Map<String, Long>> cooldowns;

    public DialogueManager(NPCVoicePlugin plugin, ConfigManager configManager, VoiceManager voiceManager, PlaceholderUtil placeholderUtil) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.voiceManager = voiceManager;
        this.placeholderUtil = placeholderUtil;
        this.cooldowns = new ConcurrentHashMap<>();
    }

    public void handleClick(@NotNull NPC npc, @NotNull Player player) {
        NPCConfig npcConfig = findNpcConfig(npc);
        if (npcConfig == null) return;

        npcConfig.click().ifPresent(clickConfig -> {
            int cooldown = clickConfig.cooldown() >= 0
                    ? clickConfig.cooldown()
                    : configManager.defaultCooldown();
            if (isOnCooldown(npc, "click", cooldown)) return;
            playSpeech(npc, player, clickConfig, npcConfig.voice().orElse(null));
            setCooldown(npc, "click");
        });
    }

    public void handleApproach(@NotNull NPC npc, @NotNull Player player) {
        NPCConfig npcConfig = findNpcConfig(npc);
        if (npcConfig == null) return;

        npcConfig.approach().ifPresent(approachConfig -> {
            int cooldown = approachConfig.cooldown() >= 0
                    ? approachConfig.cooldown()
                    : configManager.defaultCooldown();
            if (isOnCooldown(npc, "approach", cooldown)) return;
            playSpeech(npc, player, approachConfig, npcConfig.voice().orElse(null));
            setCooldown(npc, "approach");
        });
    }

    private void playSpeech(@NotNull NPC npc, @NotNull Player player, @NotNull SpeechConfig speechConfig, String voice) {
        List<String> speeches = speechConfig.speech();
        if (speeches.isEmpty()) return;

        String selectedText;
        if (speechConfig.random()) {
            selectedText = speeches.get(ThreadLocalRandom.current().nextInt(speeches.size()));
        } else {
            NPCVoiceSession session = voiceManager.getOrCreateSession(npc);
            int index = session.currentSpeechIndex() % speeches.size();
            selectedText = speeches.get(index);
            session.incrementSpeechIndex();
        }

        if (!plugin.isEnabled()) return;
        if (selectedText.startsWith("@")) {
            voiceManager.playFile(npc, selectedText.substring(1));
            return;
        }

        String processedText = placeholderUtil.processPlaceholders(player, selectedText);
        String resolvedVoice = voice != null ? voice : configManager.defaultVoice();
        voiceManager.speak(npc, processedText, resolvedVoice);
    }

    private NPCConfig findNpcConfig(NPC npc) {
        int npcId = npc.getId();
        return configManager.allNpcConfigs().values().stream()
                .filter(cfg -> cfg.id() == npcId)
                .findFirst()
                .orElse(null);
    }

    private boolean isOnCooldown(NPC npc, String type, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return false;

        Map<String, Long> npcCooldowns = cooldowns.get(npc.getUniqueId());
        if (npcCooldowns == null) return false;

        Long lastTime = npcCooldowns.get(type);
        if (lastTime == null) return false;

        return (System.currentTimeMillis() - lastTime) < (cooldownSeconds * 1000L);
    }

    private void setCooldown(NPC npc, String type) {
        cooldowns.computeIfAbsent(npc.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(type, System.currentTimeMillis());
    }

    public void removeCooldowns(NPC npc) {
        cooldowns.remove(npc.getUniqueId());
    }

    public void clearCooldowns() {
        cooldowns.clear();
    }
}
