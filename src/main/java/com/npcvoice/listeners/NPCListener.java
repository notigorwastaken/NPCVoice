package com.npcvoice.listeners;

import com.npcvoice.NPCVoicePlugin;
import com.npcvoice.dialogue.DialogueManager;
import com.npcvoice.voice.VoiceManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class NPCListener implements Listener {

    private final NPCVoicePlugin plugin;
    private final VoiceManager voiceManager;
    private final DialogueManager dialogueManager;

    public NPCListener(NPCVoicePlugin plugin, VoiceManager voiceManager, DialogueManager dialogueManager) {
        this.plugin = plugin;
        this.voiceManager = voiceManager;
        this.dialogueManager = dialogueManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        Player player = event.getClicker();

        if (!npc.isSpawned()) return;

        plugin.getServer().getScheduler().runTask(plugin, () ->
                dialogueManager.handleClick(npc, player));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNPCLeftClick(NPCLeftClickEvent event) {
        NPC npc = event.getNPC();
        Player player = event.getClicker();

        if (!npc.isSpawned()) return;

        plugin.getServer().getScheduler().runTask(plugin, () ->
                dialogueManager.handleClick(npc, player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNPCSpawn(NPCSpawnEvent event) {
        NPC npc = event.getNPC();
        voiceManager.getOrCreateSession(npc);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNPCDespawn(NPCDespawnEvent event) {
        NPC npc = event.getNPC();
        voiceManager.stop(npc);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNPCRemove(NPCRemoveEvent event) {
        NPC npc = event.getNPC();
        voiceManager.stop(npc);
        voiceManager.removeSession(npc);
        dialogueManager.removeCooldowns(npc);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (event.getFrom().distanceSquared(event.getTo()) < 0.01) return;

        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;

        int approachRadius = plugin.getConfigManager().defaultApproachRadius();
        double radiusSq = (double) approachRadius * approachRadius;

        plugin.getConfigManager().allNpcConfigs().values().stream()
                .filter(cfg -> {
                    NPC npc = CitizensAPI.getNPCRegistry().getById(cfg.id());
                    return npc != null && npc.isSpawned();
                })
                .forEach(cfg -> {
                    NPC npc = CitizensAPI.getNPCRegistry().getById(cfg.id());
                    if (npc == null) return;

                    Location npcLoc = npc.getStoredLocation();
                    if (npcLoc == null || !npcLoc.getWorld().equals(to.getWorld())) return;

                    if (npcLoc.distanceSquared(to) <= radiusSq) {
                        dialogueManager.handleApproach(npc, player);
                    }
                });
    }
}
