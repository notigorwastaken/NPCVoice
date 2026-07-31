package com.npcvoice;

import com.npcvoice.api.NPCVoiceAPI;
import com.npcvoice.audio.AudioFileManager;
import com.npcvoice.cache.AudioCache;
import com.npcvoice.commands.NPCVoiceCommand;
import com.npcvoice.config.ConfigManager;
import com.npcvoice.dialogue.DialogueManager;
import com.npcvoice.listeners.NPCListener;
import com.npcvoice.tts.TTSManager;
import com.npcvoice.util.PlaceholderUtil;
import com.npcvoice.voice.VoiceManager;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class NPCVoicePlugin extends JavaPlugin implements VoicechatPlugin {

    private ConfigManager configManager;
    private TTSManager ttsManager;
    private AudioCache audioCache;
    private AudioFileManager audioFileManager;
    private VoiceManager voiceManager;
    private DialogueManager dialogueManager;
    private PlaceholderUtil placeholderUtil;
    private NPCVoiceCommand command;
    private NPCListener listener;

    private VoicechatServerApi voicechatApi;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        configManager = new ConfigManager(this);
        configManager.load();

        audioCache = new AudioCache(this, configManager);
        audioCache.initialize();

        audioFileManager = new AudioFileManager(this, getDataFolder().toPath());
        audioFileManager.initialize();

        ttsManager = new TTSManager(this, configManager);
        ttsManager.load();

        voiceManager = new VoiceManager(this, configManager, ttsManager, audioCache, audioFileManager);

        placeholderUtil = new PlaceholderUtil(this);

        dialogueManager = new DialogueManager(this, configManager, voiceManager, placeholderUtil);

        command = new NPCVoiceCommand(this, voiceManager, dialogueManager, audioCache, audioFileManager);
        getCommand("npcvoice").setExecutor(command);
        getCommand("npcvoice").setTabCompleter(command);

        listener = new NPCListener(this, voiceManager, dialogueManager);
        getServer().getPluginManager().registerEvents(listener, this);

        NPCVoiceAPI.initialize(voiceManager, dialogueManager);

        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            service.registerPlugin(this);
            getLogger().info("Registered Simple Voice Chat plugin.");
        } else {
            getLogger().warning("Simple Voice Chat not found. Voice features will not work.");
        }

        long elapsed = System.currentTimeMillis() - startTime;
        getLogger().info("NPCVoice enabled in " + elapsed + "ms");
    }

    @Override
    public void onDisable() {
        if (voiceManager != null) voiceManager.shutdown();
        if (ttsManager != null) ttsManager.shutdown();
        if (audioCache != null) audioCache.shutdown();

        NPCVoiceAPI.shutdown();

        getLogger().info("NPCVoice disabled.");
    }

    public void reloadConfig() {
        super.reloadConfig();
        if (configManager != null) configManager.reload();
        if (ttsManager != null) ttsManager.load();
        if (placeholderUtil != null) placeholderUtil.reload();
        if (audioFileManager != null) {
            audioFileManager.clearCache();
            audioFileManager.scanFiles();
        }
    }

    @Override
    public @NotNull String getPluginId() {
        return "NPCVoice";
    }

    @Override
    public void registerEvents(@NotNull EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onVoiceChatServerStarted);
    }

    private void onVoiceChatServerStarted(VoicechatServerStartedEvent event) {
        this.voicechatApi = event.getVoicechat();
        voiceManager.initialize(voicechatApi);
        getLogger().info("VoiceChat server started. Voice system initialized.");
    }

    public @NotNull ConfigManager getConfigManager() { return configManager; }
    public @NotNull VoiceManager getVoiceManager() { return voiceManager; }
    public @NotNull TTSManager getTtsManager() { return ttsManager; }
    public @NotNull AudioCache getAudioCache() { return audioCache; }
    public @NotNull DialogueManager getDialogueManager() { return dialogueManager; }
    public @NotNull PlaceholderUtil getPlaceholderUtil() { return placeholderUtil; }
}
