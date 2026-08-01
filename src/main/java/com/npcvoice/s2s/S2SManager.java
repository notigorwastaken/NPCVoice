package com.npcvoice.s2s;

import com.npcvoice.NPCVoicePlugin;
import com.npcvoice.config.ConfigManager;
import com.npcvoice.config.ConfigManager.NPCConfig;
import com.npcvoice.stt.STTManager;
import com.npcvoice.util.WavWriter;
import com.npcvoice.voice.VoiceManager;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiolistener.PlayerAudioListener;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Speak-to-speak: a player talks near an NPC, the speech is transcribed (STT)
 * and the NPC replies with generated TTS audio.
 */
public final class S2SManager implements Listener {

    private static final int TARGET_SAMPLE_RATE = 48000;
    private static final int SILENCE_THRESHOLD = 150;

    private final NPCVoicePlugin plugin;
    private final ConfigManager configManager;
    private final VoiceManager voiceManager;
    private final STTManager sttManager;
    private final Map<UUID, PlayerAudioListener> listeners = new ConcurrentHashMap<>();
    private final Map<UUID, Utterance> utterances = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
    private VoicechatServerApi api;

    public S2SManager(NPCVoicePlugin plugin, ConfigManager configManager, VoiceManager voiceManager, STTManager sttManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.voiceManager = voiceManager;
        this.sttManager = sttManager;
    }

    public void initialize(@NotNull VoicechatServerApi api) {
        this.api = api;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            registerPlayer(player);
        }
    }

    public void shutdown() {
        for (PlayerAudioListener listener : listeners.values()) {
            try {
                api.unregisterAudioListener(listener);
            } catch (Exception ignored) {
            }
        }
        for (OpusDecoder decoder : decoders.values()) {
            try {
                decoder.close();
            } catch (Exception ignored) {
            }
        }
        listeners.clear();
        decoders.clear();
        utterances.clear();
        cooldowns.clear();
        api = null;
    }

    public void registerPlayer(@NotNull Player player) {
        if (api == null || listeners.containsKey(player.getUniqueId())) return;
        if (configManager.sttProvider().equalsIgnoreCase("none")) return;

        try {
            PlayerAudioListener listener = api.playerAudioListenerBuilder()
                    .setPlayer(player.getUniqueId())
                    .setPacketListener(packet -> handlePacket(player, packet))
                    .build();
            if (api.registerAudioListener(listener)) {
                listeners.put(player.getUniqueId(), listener);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to register audio listener for " + player.getName(), e);
        }
    }

    public void unregisterPlayer(@NotNull Player player) {
        PlayerAudioListener listener = listeners.remove(player.getUniqueId());
        if (listener != null && api != null) {
            try {
                api.unregisterAudioListener(listener);
            } catch (Exception ignored) {
            }
        }
        OpusDecoder decoder = decoders.remove(player.getUniqueId());
        if (decoder != null) {
            try {
                decoder.close();
            } catch (Exception ignored) {
            }
        }
        utterances.remove(player.getUniqueId());
        cooldowns.remove(player.getUniqueId());
    }

    private void handlePacket(@NotNull Player player, @NotNull SoundPacket packet) {
        if (api == null || !configManager.s2sEnabled()) return;
        if (!player.isOnline()) return;

        NPC target = nearestS2sNpc(player);
        if (target == null) {
            utterances.remove(player.getUniqueId());
            return;
        }

        short[] pcm;
        try {
            OpusDecoder decoder = decoders.computeIfAbsent(player.getUniqueId(), id -> api.createDecoder());
            pcm = decoder.decode(packet.getOpusEncodedData());
        } catch (Exception e) {
            return;
        }
        if (pcm == null || pcm.length == 0) return;

        Utterance utterance = utterances.computeIfAbsent(player.getUniqueId(), k -> new Utterance(configManager.s2sMaxAudioMs()));

        if (isSilence(pcm)) {
            utterance.silenceMs += durationMs(pcm.length);
            if (utterance.silenceMs >= configManager.s2sSilenceMs()) {
                finalizeUtterance(player, target, utterance);
            }
        } else {
            utterance.silenceMs = 0;
            utterance.append(pcm);
            if (utterance.durationMs() >= configManager.s2sMaxAudioMs()) {
                finalizeUtterance(player, target, utterance);
            }
        }
    }

    private NPC nearestS2sNpc(@NotNull Player player) {
        int radius = configManager.s2sRadius();
        int radiusSq = radius * radius;
        Location playerLoc = player.getLocation();

        NPC nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (NPCConfig cfg : configManager.allNpcConfigs().values()) {
            if (!cfg.sttEnabled()) continue;
            NPC npc = CitizensAPI.getNPCRegistry().getById(cfg.id());
            if (npc == null || !npc.isSpawned()) continue;

            Location npcLoc = npc.getStoredLocation();
            if (npcLoc == null || !npcLoc.getWorld().equals(playerLoc.getWorld())) continue;

            double distSq = npcLoc.distanceSquared(playerLoc);
            if (distSq <= radiusSq && distSq < nearestDistSq) {
                nearest = npc;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }

    private void finalizeUtterance(@NotNull Player player, @NotNull NPC npc, @NotNull Utterance utterance) {
        short[] pcm = utterance.toArray();
        utterance.reset();
        utterances.remove(player.getUniqueId());

        if (pcm.length == 0) return;
        long durationMs = durationMs(pcm.length);
        if (durationMs < configManager.s2sMinSpeechMs()) return;

        if (isOnCooldown(player)) return;
        setCooldown(player);

        byte[] wav = WavWriter.toWavBytes(pcm, TARGET_SAMPLE_RATE, 1);

        sttManager.transcribeAsync(wav).thenAccept(transcript -> {
            if (transcript.isEmpty()) {
                plugin.getLogger().fine("STT returned no transcript for " + player.getName());
                return;
            }

            String text = transcript.get();
            if (configManager.debug()) {
                plugin.getLogger().info(player.getName() + " -> NPC " + npc.getName() + ": " + text);
            }

            String voice = configManager.npcConfigByNpcId(npc.getId())
                    .flatMap(NPCConfig::voice)
                    .orElse(configManager.defaultVoice());

            voiceManager.speak(npc, text, voice);
        });
    }

    private boolean isSilence(short[] pcm) {
        for (short s : pcm) {
            if (Math.abs(s) >= SILENCE_THRESHOLD) return false;
        }
        return true;
    }

    private long durationMs(int sampleCount) {
        return sampleCount * 1000L / TARGET_SAMPLE_RATE;
    }

    private boolean isOnCooldown(@NotNull Player player) {
        Long last = cooldowns.get(player.getUniqueId());
        if (last == null) return false;
        return (System.currentTimeMillis() - last) < (configManager.s2sCooldown() * 1000L);
    }

    private void setCooldown(@NotNull Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        registerPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unregisterPlayer(event.getPlayer());
    }

    private static final class Utterance {
        private final int maxSamples;
        private short[] buffer;
        private int length;
        private long silenceMs;

        Utterance(int maxAudioMs) {
            this.maxSamples = Math.max(4800, (int) (maxAudioMs * TARGET_SAMPLE_RATE / 1000L));
            this.buffer = new short[Math.min(maxSamples, TARGET_SAMPLE_RATE)];
        }

        void append(short[] samples) {
            for (short s : samples) {
                if (length >= maxSamples) return;
                if (length >= buffer.length) {
                    grow();
                }
                buffer[length++] = s;
            }
        }

        private void grow() {
            short[] bigger = new short[Math.min(buffer.length * 2, maxSamples)];
            System.arraycopy(buffer, 0, bigger, 0, length);
            buffer = bigger;
        }

        long durationMs() {
            return length * 1000L / TARGET_SAMPLE_RATE;
        }

        short[] toArray() {
            short[] copy = new short[length];
            System.arraycopy(buffer, 0, copy, 0, length);
            return copy;
        }

        void reset() {
            length = 0;
            silenceMs = 0;
        }
    }
}
