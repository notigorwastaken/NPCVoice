package com.npcvoice.gui;

import com.npcvoice.NPCVoicePlugin;
import com.npcvoice.cache.AudioCache;
import com.npcvoice.config.ConfigManager;
import com.npcvoice.config.ConfigManager.NPCConfig;
import com.npcvoice.tts.TTSManager;
import com.npcvoice.voice.VoiceManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;

public final class NPCEditorGUI implements Listener {

    private static final String TEST_TEXT = "Hello! This is a test of my voice.";

    private final NPCVoicePlugin plugin;
    private final ConfigManager configManager;
    private final TTSManager ttsManager;
    private final VoiceManager voiceManager;
    private final AudioCache audioCache;

    private final IdentityHashMap<Inventory, GuiState> guis = new IdentityHashMap<>();

    public NPCEditorGUI(
            NPCVoicePlugin plugin,
            ConfigManager configManager,
            TTSManager ttsManager,
            VoiceManager voiceManager,
            AudioCache audioCache
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.ttsManager = ttsManager;
        this.voiceManager = voiceManager;
        this.audioCache = audioCache;
    }

    private static int indexOf(List<String> options, String value) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(value)) return i;
        }
        return -1;
    }

    private static int wrapIndex(int index, int size) {
        if (size <= 0) return -1;
        return Math.floorMod(index, size);
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Component.text(line).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public void openMainMenu(@NotNull Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, Component.text("NPCVoice"));
        guis.put(inv, new GuiState(GuiState.Type.MAIN));

        inv.setItem(2, item(Material.BOOK, "NPC Editor",
                "Configure per-NPC voice settings",
                "NPCs configured: " + configManager.allNpcConfigs().size()));
        inv.setItem(4, item(Material.ANVIL, "Reload Config",
                "Reloads configuration from disk"));
        inv.setItem(5, item(Material.HOPPER, "Clear Audio Cache",
                "Clears the generated audio cache"));
        inv.setItem(6, item(Material.LEVER, "Toggle Debug",
                "Debug mode: " + (configManager.debug() ? "ON" : "OFF")));

        player.openInventory(inv);
    }

    private void openNpcList(@NotNull Player player, int page) {
        List<NPCConfig> npcs = new ArrayList<>(configManager.allNpcConfigs().values());
        npcs.sort(Comparator.comparing(NPCConfig::name));

        int pageSize = 45;
        int pages = Math.max(1, (int) Math.ceil(npcs.size() / (double) pageSize));
        page = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("NPCs - page " + (page + 1) + "/" + pages));
        guis.put(inv, new GuiState(GuiState.Type.NPC_LIST).page(page));

        int start = page * pageSize;
        for (int i = start; i < Math.min(npcs.size(), start + pageSize); i++) {
            NPCConfig cfg = npcs.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("Id: " + cfg.id());
            lore.add("Voice: " + cfg.voice().orElse("(default)"));
            lore.add("Provider: " + cfg.provider().orElse("(global)"));
            lore.add("STT: " + (cfg.sttEnabled() ? "enabled" : "disabled"));
            inv.setItem(i - start, item(Material.VILLAGER_SPAWN_EGG, cfg.name(), lore.toArray(new String[0])));
        }

        inv.setItem(45, item(Material.OAK_DOOR, "Back", "Return to main menu"));
        if (page > 0) {
            inv.setItem(48, item(Material.ARROW, "Previous page"));
        }
        if (page < pages - 1) {
            inv.setItem(50, item(Material.ARROW, "Next page"));
        }

        player.openInventory(inv);
    }

    private void openNpcEditor(@NotNull Player player, @NotNull String npcName) {
        NPCConfig cfg = configManager.npcConfig(npcName);
        if (cfg == null) {
            player.sendMessage(Component.text("NPC config not found: " + npcName).color(NamedTextColor.RED));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Edit NPC: " + npcName));
        GuiState state = new GuiState(GuiState.Type.NPC_EDITOR)
                .npcName(npcName)
                .sttEnabled(cfg.sttEnabled())
                .voiceIndex(indexOf(voiceOptions(), cfg.voice().orElse(configManager.defaultVoice())))
                .providerIndex(indexOf(providerOptions(), cfg.provider().orElse(configManager.ttsProvider())));
        guis.put(inv, state);

        renderNpcEditor(inv, state);
        player.openInventory(inv);
    }

    private void renderNpcEditor(Inventory inv, GuiState state) {
        NPCConfig cfg = configManager.npcConfig(state.npcName);
        if (cfg == null) return;

        List<String> voices = voiceOptions();
        List<String> providers = providerOptions();

        String currentVoice = state.voiceIndex >= 0 && state.voiceIndex < voices.size()
                ? voices.get(state.voiceIndex)
                : cfg.voice().orElse(configManager.defaultVoice());

        String currentProvider = state.providerIndex >= 0 && state.providerIndex < providers.size()
                ? providers.get(state.providerIndex)
                : cfg.provider().orElse(configManager.ttsProvider());

        inv.setItem(0, item(Material.NOTE_BLOCK, "Voice",
                "Current: " + currentVoice,
                "Resolves to: " + configManager.resolveVoiceId(currentVoice, currentProvider)));
        inv.setItem(1, item(Material.ARROW, "< Voice"));
        inv.setItem(2, item(Material.ARROW, "Voice >"));

        inv.setItem(4, item(Material.BOOK, "Provider",
                "Current: " + currentProvider,
                "Per-NPC override (empty = global)"));
        inv.setItem(5, item(Material.ARROW, "< Provider"));
        inv.setItem(6, item(Material.ARROW, "Provider >"));

        inv.setItem(9, item(state.sttEnabled ? Material.ENDER_PEARL : Material.BARRIER, "Speak-to-speak",
                state.sttEnabled ? "Enabled (click to disable)" : "Disabled (click to enable)"));

        inv.setItem(13, item(Material.MUSIC_DISC_CAT, "Test Speak",
                "Makes the NPC say: " + TEST_TEXT));

        inv.setItem(17, item(Material.EMERALD_BLOCK, "Save",
                "Persists changes to config.yml"));
        inv.setItem(22, item(Material.OAK_DOOR, "Back", "Discard and return to NPC list"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getClickedInventory();
        if (inv == null || !guis.containsKey(inv)) return;

        event.setCancelled(true);

        if (event.getRawSlot() < 0 || event.getRawSlot() >= inv.getSize()) return;
        GuiState state = guis.get(inv);

        switch (state.type) {
            case MAIN -> handleMainClick(player, inv, event.getRawSlot());
            case NPC_LIST -> handleNpcListClick(player, inv, event.getRawSlot(), state);
            case NPC_EDITOR -> handleNpcEditorClick(player, inv, event.getRawSlot(), state);
        }
    }

    private void handleMainClick(Player player, Inventory inv, int slot) {
        switch (slot) {
            case 2 -> openNpcList(player, 0);
            case 4 -> {
                plugin.reloadConfig();
                configManager.reload();
                player.sendMessage(Component.text("NPCVoice configuration reloaded.").color(NamedTextColor.GREEN));
                player.closeInventory();
            }
            case 5 -> {
                audioCache.clear();
                player.sendMessage(Component.text("Audio cache cleared.").color(NamedTextColor.GREEN));
                player.closeInventory();
            }
            case 6 -> {
                configManager.toggleDebug();
                player.sendMessage(Component.text("Debug mode: "
                        + (configManager.debug() ? "enabled" : "disabled")).color(NamedTextColor.GRAY));
                inv.setItem(6, item(Material.LEVER, "Toggle Debug",
                        "Debug mode: " + (configManager.debug() ? "ON" : "OFF")));
            }
            default -> {
            }
        }
    }

    private void handleNpcListClick(Player player, Inventory inv, int slot, GuiState state) {
        if (slot == 45) {
            openMainMenu(player);
            return;
        }
        if (slot == 48 && state.page > 0) {
            openNpcList(player, state.page - 1);
            return;
        }
        if (slot == 50) {
            int pageSize = 45;
            int pages = Math.max(1, (int) Math.ceil(configManager.allNpcConfigs().size() / (double) pageSize));
            if (state.page < pages - 1) {
                openNpcList(player, state.page + 1);
            }
            return;
        }

        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || !clicked.hasItemMeta()) return;
        Component display = clicked.getItemMeta().displayName();
        if (display == null) return;
        String npcName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(display);
        if (configManager.npcConfig(npcName) != null) {
            openNpcEditor(player, npcName);
        }
    }

    private void handleNpcEditorClick(Player player, Inventory inv, int slot, GuiState state) {
        List<String> voices = voiceOptions();
        List<String> providers = providerOptions();

        switch (slot) {
            case 1 -> {
                state.voiceIndex = wrapIndex(state.voiceIndex - 1, voices.size());
                renderNpcEditor(inv, state);
            }
            case 2 -> {
                state.voiceIndex = wrapIndex(state.voiceIndex + 1, voices.size());
                renderNpcEditor(inv, state);
            }
            case 5 -> {
                state.providerIndex = wrapIndex(state.providerIndex - 1, providers.size());
                renderNpcEditor(inv, state);
            }
            case 6 -> {
                state.providerIndex = wrapIndex(state.providerIndex + 1, providers.size());
                renderNpcEditor(inv, state);
            }
            case 9 -> {
                state.sttEnabled = !state.sttEnabled;
                renderNpcEditor(inv, state);
            }
            case 13 -> {
                NPC npc = findNpc(state.npcName);
                if (npc == null) {
                    player.sendMessage(Component.text("NPC not found: " + state.npcName).color(NamedTextColor.RED));
                } else {
                    voiceManager.speak(npc, TEST_TEXT);
                }
            }
            case 17 -> {
                applyState(state);
                player.sendMessage(Component.text("Saved NPC settings for " + state.npcName + ".").color(NamedTextColor.GREEN));
                guis.remove(inv);
                player.closeInventory();
                openNpcList(player, 0);
            }
            case 22 -> {
                guis.remove(inv);
                player.closeInventory();
                openNpcList(player, 0);
            }
            default -> {
            }
        }
    }

    private void applyState(GuiState state) {
        List<String> voices = voiceOptions();
        List<String> providers = providerOptions();

        String voice = state.voiceIndex >= 0 && state.voiceIndex < voices.size()
                ? voices.get(state.voiceIndex) : null;
        String provider = state.providerIndex >= 0 && state.providerIndex < providers.size()
                ? providers.get(state.providerIndex) : null;

        configManager.setNpcVoice(state.npcName, voice);
        configManager.setNpcProvider(state.npcName, provider);
        configManager.setNpcSttEnabled(state.npcName, state.sttEnabled);
        configManager.saveNpcs();
    }

    private NPC findNpc(String name) {
        NPCConfig cfg = configManager.npcConfig(name);
        if (cfg == null) return null;
        return CitizensAPI.getNPCRegistry().getById(cfg.id());
    }

    private List<String> voiceOptions() {
        List<String> options = new ArrayList<>();
        options.add(configManager.defaultVoice());
        configManager.voicePresets().keySet().stream()
                .filter(voice -> !voice.equalsIgnoreCase(configManager.defaultVoice()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(options::add);
        return options;
    }

    private List<String> providerOptions() {
        return ttsManager.getAllProviders().keySet().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        guis.remove(event.getInventory());
    }

    private static final class GuiState {
        final Type type;
        int page;
        String npcName;
        int voiceIndex = -1;
        int providerIndex = -1;
        boolean sttEnabled = true;

        GuiState(Type type) {
            this.type = type;
        }

        GuiState page(int page) {
            this.page = page;
            return this;
        }

        GuiState npcName(String npcName) {
            this.npcName = npcName;
            return this;
        }

        GuiState voiceIndex(int voiceIndex) {
            this.voiceIndex = voiceIndex;
            return this;
        }

        GuiState providerIndex(int providerIndex) {
            this.providerIndex = providerIndex;
            return this;
        }

        GuiState sttEnabled(boolean sttEnabled) {
            this.sttEnabled = sttEnabled;
            return this;
        }

        enum Type {MAIN, NPC_LIST, NPC_EDITOR}
    }
}
