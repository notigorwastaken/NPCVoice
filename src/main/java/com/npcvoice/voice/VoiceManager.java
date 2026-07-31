package com.npcvoice.voice;

import com.npcvoice.NPCVoicePlugin;
import com.npcvoice.audio.AudioFileManager;
import com.npcvoice.cache.AudioCache;
import com.npcvoice.config.ConfigManager;
import com.npcvoice.tts.TTSManager;
import com.npcvoice.util.AudioConverter;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.trait.HologramTrait;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class VoiceManager {

    private final NPCVoicePlugin plugin;
    private final ConfigManager configManager;
    private final TTSManager ttsManager;
    private final AudioCache audioCache;
    private final AudioFileManager audioFileManager;
    private final Map<UUID, NPCVoiceSession> sessions;
    private final Map<UUID, AudioPlayer> activePlayers;
    private VoicechatServerApi voicechatApi;
    private boolean categoryRegistered;

    public VoiceManager(NPCVoicePlugin plugin, ConfigManager configManager, TTSManager ttsManager, AudioCache audioCache, AudioFileManager audioFileManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.ttsManager = ttsManager;
        this.audioCache = audioCache;
        this.audioFileManager = audioFileManager;
        this.sessions = new ConcurrentHashMap<>();
        this.activePlayers = new ConcurrentHashMap<>();
        this.categoryRegistered = false;
    }

    public void initialize(VoicechatServerApi api) {
        this.voicechatApi = api;
        registerVolumeCategory();
    }

    private void registerVolumeCategory() {
        if (voicechatApi == null || categoryRegistered) return;
        var category = voicechatApi.volumeCategoryBuilder()
                .setId("npc_voice")
                .setName("NPC Voices")
                .setDescription("Voice chat volume for NPCs")
                .build();
        voicechatApi.registerVolumeCategory(category);
        categoryRegistered = true;
    }

    public NPCVoiceSession getOrCreateSession(NPC npc) {
        return sessions.computeIfAbsent(npc.getUniqueId(), id -> new NPCVoiceSession(npc));
    }

    public Optional<NPCVoiceSession> getSession(NPC npc) {
        return Optional.ofNullable(sessions.get(npc.getUniqueId()));
    }

    public void removeSession(NPC npc) {
        stop(npc);
        sessions.remove(npc.getUniqueId());
    }

    public CompletableFuture<Void> speak(@NotNull NPC npc, @NotNull String text) {
        String voice = configManager.defaultVoice();
        NPCVoiceSession session = getOrCreateSession(npc);
        session.updateLocation();

        return speak(npc, text, voice);
    }

    public CompletableFuture<Void> speak(@NotNull NPC npc, @NotNull String text, @NotNull String voice) {
        NPCVoiceSession session = getOrCreateSession(npc);
        session.updateLocation();
        session.setSpeaking(true);
        session.interrupt();
        session.resetInterrupted();

        if (configManager.debug()) {
            plugin.getLogger().info("NPC " + npc.getName() + " speaking: " + text);
        }

        return audioCache.getOrGenerateAsync(text, voice, () -> {
            String resolvedVoice = configManager.resolveVoiceId(voice);
            return ttsManager.generateSpeechAsync(text, resolvedVoice);
        }).thenAccept(audioData -> {
            if (audioData == null || audioData.length == 0) {
                plugin.getLogger().warning("Failed to generate audio for NPC " + npc.getName());
                session.setSpeaking(false);
                return;
            }

            playAudio(npc, session, audioData);
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error in speech generation for NPC " + npc.getName(), ex);
            session.setSpeaking(false);
            return null;
        });
    }

    public CompletableFuture<Void> playFile(@NotNull NPC npc, @NotNull String fileName) {
        NPCVoiceSession session = getOrCreateSession(npc);
        session.updateLocation();
        session.setSpeaking(true);
        session.interrupt();
        session.resetInterrupted();

        return CompletableFuture.supplyAsync(() -> {
            short[] pcmSamples = audioFileManager.getPcmShorts(fileName);
            if (pcmSamples == null || pcmSamples.length == 0) {
                plugin.getLogger().warning("Audio file not found or empty: " + fileName + " for NPC " + npc.getName());
                session.setSpeaking(false);
                return null;
            }
            return pcmSamples;
        }).thenAccept(pcmSamples -> {
            if (pcmSamples == null) return;
            playPcm(npc, session, pcmSamples);
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error playing audio file for NPC " + npc.getName(), ex);
            session.setSpeaking(false);
            return null;
        });
    }

    private void playAudio(NPC npc, NPCVoiceSession session, byte[] audioData) {
        short[] pcmSamples = AudioConverter.toPcmShorts(audioData);
        if (pcmSamples.length == 0) {
            session.setSpeaking(false);
            return;
        }
        playPcm(npc, session, pcmSamples);
    }

    private void playPcm(NPC npc, NPCVoiceSession session, short[] pcmSamples) {
        if (voicechatApi == null) return;
        if (!npc.isSpawned()) return;

        if (configManager.debug()) {
            plugin.getLogger().info("NPC " + npc.getName() + " playing " + pcmSamples.length
                    + " samples (" + String.format("%.2f", pcmSamples.length / 48000.0) + "s)");
        }

        try {
            Location loc = npc.getStoredLocation();
            if (loc == null || loc.getWorld() == null) return;

            AudioChannel channel = createAudioChannel(npc, loc);

            if (channel == null) {
                plugin.getLogger().warning("Failed to create audio channel for NPC " + npc.getName());
                session.setSpeaking(false);
                return;
            }

            channel.setCategory("npc_voice");
            if (channel instanceof LocationalAudioChannel locationalChannel) {
                locationalChannel.setDistance(configManager.voiceChatDistance());
            } else if (channel instanceof EntityAudioChannel entityChannel) {
                entityChannel.setDistance(configManager.voiceChatDistance());
            }

            AudioPlayer player = voicechatApi.createAudioPlayer(
                    channel, voicechatApi.createEncoder(), pcmSamples
            );

            if (player == null) {
                plugin.getLogger().warning("Failed to create audio player for NPC " + npc.getName());
                session.setSpeaking(false);
                return;
            }

            player.setOnStopped(() -> {
                activePlayers.remove(npc.getUniqueId());
                session.setSpeaking(false);
                hideSpeakingIcon(npc, session);
            });

            int durationTicks = Math.max(40, (int) Math.ceil(pcmSamples.length / 48000.0 * 20.0) + 40);
            activePlayers.put(npc.getUniqueId(), player);
            showSpeakingIcon(npc, session, durationTicks);
            player.startPlaying();

            session.setSpeaking(true);
            session.updateLastSpeechTime();

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to play audio for NPC " + npc.getName(), e);
            session.setSpeaking(false);
            hideSpeakingIcon(npc, session);
        }
    }

    private AudioChannel createAudioChannel(NPC npc, Location loc) {
        if (npc.isSpawned()) {
            try {
                org.bukkit.entity.Entity bukkitEntity = npc.getEntity();
                if (bukkitEntity != null) {
                    de.maxhenkel.voicechat.api.Entity svcEntity = voicechatApi.fromEntity(bukkitEntity);
                    if (svcEntity != null) {
                        AudioChannel entityChannel = voicechatApi.createEntityAudioChannel(UUID.randomUUID(), svcEntity);
                        if (entityChannel != null) {
                            return entityChannel;
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Failed to create entity audio channel for NPC " + npc.getName()
                        + ", falling back to locational channel", e);
            }
        }

        de.maxhenkel.voicechat.api.Position position = voicechatApi.createPosition(
                loc.getX(), loc.getY(), loc.getZ()
        );
        return voicechatApi.createLocationalAudioChannel(
                UUID.randomUUID(),
                voicechatApi.fromServerLevel(loc.getWorld()),
                position
        );
    }

    private void showSpeakingIcon(NPC npc, NPCVoiceSession session, int durationTicks) {
        if (!configManager.speakingIconEnabled()) return;
        String icon = configManager.speakingIcon();
        if (icon == null || icon.isEmpty()) return;

        String realName = session.realName();
        if (realName.isEmpty()) return;

        runOnMainThread(() -> {
            if (isPlayerType(npc)) {
                showHologramIcon(npc, icon, durationTicks);
            } else {
                String current = npc.getName();
                if (current == null || current.equals(realName)) {
                    npc.setName(icon + " " + realName);
                }
            }
        });
    }

    private void hideSpeakingIcon(NPC npc, NPCVoiceSession session) {
        String realName = session.realName();
        if (realName.isEmpty()) return;

        runOnMainThread(() -> {
            if (isPlayerType(npc)) {
                hideHologramIcon(npc);
            } else {
                String current = npc.getName();
                if (current != null && !current.equals(realName)) {
                    npc.setName(realName);
                }
            }
        });
    }

    private void showHologramIcon(NPC npc, String icon, int durationTicks) {
        try {
            HologramTrait hologram = npc.getOrAddTrait(HologramTrait.class);
            if (hologram == null) return;
            int index = hologram.getLines().indexOf(icon);
            if (index >= 0) {
                hologram.removeLine(index);
            }
            hologram.addTemporaryLine(icon, durationTicks);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to show hologram speaking icon for NPC " + npc.getName(), e);
        }
    }

    private void hideHologramIcon(NPC npc) {
        try {
            if (!npc.hasTrait(HologramTrait.class)) return;
            HologramTrait hologram = npc.getTrait(HologramTrait.class);
            if (hologram == null) return;
            int index = hologram.getLines().indexOf(configManager.speakingIcon());
            if (index >= 0) {
                hologram.removeLine(index);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to hide hologram speaking icon for NPC " + npc.getName(), e);
        }
    }

    private boolean isPlayerType(NPC npc) {
        try {
            org.bukkit.entity.Entity entity = npc.getEntity();
            if (entity != null) {
                return entity.getType() == EntityType.PLAYER;
            }
            MobType mobType = npc.getTraitNullable(MobType.class);
            return mobType != null && mobType.getType() == EntityType.PLAYER;
        } catch (Exception e) {
            return true;
        }
    }

    private void runOnMainThread(Runnable task) {
        if (!plugin.isEnabled()) return;
        if (plugin.getServer().isPrimaryThread()) {
            task.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    public void stop(@NotNull NPC npc) {
        NPCVoiceSession session = sessions.get(npc.getUniqueId());
        if (session != null) {
            session.interrupt();
            session.setSpeaking(false);
            hideSpeakingIcon(npc, session);
        }

        AudioPlayer player = activePlayers.remove(npc.getUniqueId());
        if (player != null) {
            try {
                player.stopPlaying();
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Error stopping audio player for NPC " + npc.getName(), e);
            }
        }
    }

    public boolean isSpeaking(@NotNull NPC npc) {
        NPCVoiceSession session = sessions.get(npc.getUniqueId());
        return session != null && session.isSpeaking();
    }

    public void stopAll() {
        activePlayers.values().forEach(player -> {
            try {
                player.stopPlaying();
            } catch (Exception ignored) {}
        });
        activePlayers.clear();
        sessions.values().forEach(session -> {
            session.interrupt();
            session.setSpeaking(false);
            NPC npc = session.npc();
            if (npc != null) {
                hideSpeakingIcon(npc, session);
            }
        });
    }

    public void shutdown() {
        stopAll();
        sessions.clear();
        voicechatApi = null;
    }
}
