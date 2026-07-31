package com.npcvoice.voice;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NPCVoiceSession {

    private final NPC npc;
    private final UUID npcUuid;
    private final String realName;
    private volatile Location lastLocation;
    private final AtomicBoolean speaking;
    private volatile long lastSpeechTime;
    private volatile int currentSpeechIndex;
    private volatile boolean interrupted;

    public NPCVoiceSession(@NotNull NPC npc) {
        this.npc = npc;
        this.npcUuid = npc.getUniqueId();
        this.realName = npc.getName() == null ? "" : npc.getName();
        this.speaking = new AtomicBoolean(false);
        this.lastSpeechTime = 0;
        this.currentSpeechIndex = 0;
        this.interrupted = false;
        updateLocation();
    }

    public void updateLocation() {
        if (npc.isSpawned()) {
            this.lastLocation = npc.getStoredLocation();
        }
    }

    @NotNull
    public NPC npc() { return npc; }

    @NotNull
    public UUID npcUuid() { return npcUuid; }

    @NotNull
    public String realName() { return realName; }

    @NotNull
    public Location location() { return lastLocation != null ? lastLocation : new Location(null, 0, 0, 0); }

    public boolean isSpeaking() { return speaking.get(); }

    public void setSpeaking(boolean value) { speaking.set(value); }

    public long lastSpeechTime() { return lastSpeechTime; }

    public void updateLastSpeechTime() { this.lastSpeechTime = System.currentTimeMillis(); }

    public int currentSpeechIndex() { return currentSpeechIndex; }

    public void setCurrentSpeechIndex(int index) { this.currentSpeechIndex = index; }

    public void incrementSpeechIndex() { this.currentSpeechIndex++; }

    public void resetSpeechIndex() { this.currentSpeechIndex = 0; }

    public boolean isInterrupted() { return interrupted; }

    public void interrupt() { this.interrupted = true; speaking.set(false); }

    public void resetInterrupted() { this.interrupted = false; }
}
