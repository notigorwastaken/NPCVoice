package com.npcvoice.util;

import com.npcvoice.NPCVoicePlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PlaceholderUtil {

    private final NPCVoicePlugin plugin;
    private boolean placeholderApiAvailable;

    public PlaceholderUtil(NPCVoicePlugin plugin) {
        this.plugin = plugin;
        this.placeholderApiAvailable = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public String processPlaceholders(@NotNull Player player, @NotNull String text) {
        if (!placeholderApiAvailable) return text;

        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Exception e) {
            return text;
        }
    }

    public void reload() {
        this.placeholderApiAvailable = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }
}
