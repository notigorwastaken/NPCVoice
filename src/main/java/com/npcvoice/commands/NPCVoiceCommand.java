package com.npcvoice.commands;

import com.npcvoice.NPCVoicePlugin;
import com.npcvoice.audio.AudioFileManager;
import com.npcvoice.cache.AudioCache;
import com.npcvoice.dialogue.DialogueManager;
import com.npcvoice.voice.VoiceManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class NPCVoiceCommand implements CommandExecutor, TabCompleter {

    private final NPCVoicePlugin plugin;
    private final VoiceManager voiceManager;
    private final DialogueManager dialogueManager;
    private final AudioCache audioCache;
    private final AudioFileManager audioFileManager;

    public NPCVoiceCommand(
            NPCVoicePlugin plugin,
            VoiceManager voiceManager,
            DialogueManager dialogueManager,
            AudioCache audioCache,
            AudioFileManager audioFileManager
    ) {
        this.plugin = plugin;
        this.voiceManager = voiceManager;
        this.dialogueManager = dialogueManager;
        this.audioCache = audioCache;
        this.audioFileManager = audioFileManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendMessage(sender, Component.text("Usage: /npcvoice <reload|speak|stop|cache|debug|audio>").color(NamedTextColor.RED));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "speak" -> handleSpeak(sender, args);
            case "stop" -> handleStop(sender, args);
            case "cache" -> handleCache(sender, args);
            case "debug" -> handleDebug(sender);
            case "audio" -> handleAudio(sender, args);
            default -> sendMessage(sender, Component.text("Unknown subcommand: " + args[0]).color(NamedTextColor.RED));
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("npcvoice.reload")) {
            sendNoPermission(sender);
            return;
        }

        plugin.reloadConfig();
        plugin.getConfigManager().reload();
        dialogueManager.clearCooldowns();
        sendMessage(sender, Component.text("NPCVoice configuration reloaded.").color(NamedTextColor.GREEN));
    }

    private void handleSpeak(CommandSender sender, String[] args) {
        if (!sender.hasPermission("npcvoice.speak")) {
            sendNoPermission(sender);
            return;
        }

        if (args.length < 3) {
            sendMessage(sender, Component.text("Usage: /npcvoice speak <npc_id|npc_name> <text>").color(NamedTextColor.RED));
            return;
        }

        String npcIdentifier = args[1];
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        NPC npc = findNPC(npcIdentifier);
        if (npc == null) {
            sendMessage(sender, Component.text("NPC not found: " + npcIdentifier).color(NamedTextColor.RED));
            return;
        }

        voiceManager.speak(npc, text).thenRun(() -> {
            if (sender instanceof Player player && player.isOnline()) {
                sendMessage(player, Component.text("NPC speaking: " + npc.getName()).color(NamedTextColor.GREEN));
            }
        });
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("npcvoice.speak")) {
            sendNoPermission(sender);
            return;
        }

        if (args.length < 2) {
            sendMessage(sender, Component.text("Usage: /npcvoice stop <npc_id|npc_name>").color(NamedTextColor.RED));
            return;
        }

        String npcIdentifier = args[1];
        NPC npc = findNPC(npcIdentifier);

        if (npc == null) {
            sendMessage(sender, Component.text("NPC not found: " + npcIdentifier).color(NamedTextColor.RED));
            return;
        }

        voiceManager.stop(npc);
        sendMessage(sender, Component.text("Stopped NPC voice: " + npc.getName()).color(NamedTextColor.GREEN));
    }

    private void handleCache(CommandSender sender, String[] args) {
        if (!sender.hasPermission("npcvoice.cache.clear")) {
            sendNoPermission(sender);
            return;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("clear")) {
            sendMessage(sender, Component.text("Usage: /npcvoice cache clear").color(NamedTextColor.RED));
            return;
        }

        audioCache.clear();
        sendMessage(sender, Component.text("Audio cache cleared.").color(NamedTextColor.GREEN));
    }

    private void handleAudio(CommandSender sender, String[] args) {
        if (!sender.hasPermission("npcvoice.audio.list") && !sender.hasPermission("npcvoice.audio.play")) {
            sendNoPermission(sender);
            return;
        }

        if (args.length < 2) {
            sendMessage(sender, Component.text("Usage: /npcvoice audio <list|play>").color(NamedTextColor.RED));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list" -> handleAudioList(sender);
            case "play" -> handleAudioPlay(sender, args);
            default -> sendMessage(sender, Component.text("Usage: /npcvoice audio <list|play>").color(NamedTextColor.RED));
        }
    }

    private void handleAudioList(CommandSender sender) {
        if (!sender.hasPermission("npcvoice.audio.list")) {
            sendNoPermission(sender);
            return;
        }

        List<String> files = audioFileManager.listAudioFiles();
        if (files.isEmpty()) {
            sendMessage(sender, Component.text("No audio files found. Place .wav or .mp3 files in the audio/ folder.").color(NamedTextColor.YELLOW));
            return;
        }

        sendMessage(sender, Component.text("Available audio files (" + files.size() + "):").color(NamedTextColor.GOLD));
        for (String file : files) {
            sendMessage(sender, Component.text("  - " + file).color(NamedTextColor.WHITE));
        }
    }

    private void handleAudioPlay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("npcvoice.audio.play")) {
            sendNoPermission(sender);
            return;
        }

        if (args.length < 4) {
            sendMessage(sender, Component.text("Usage: /npcvoice audio play <npc_id|npc_name> <filename>").color(NamedTextColor.RED));
            return;
        }

        String npcIdentifier = args[2];
        String fileName = args[3];

        NPC npc = findNPC(npcIdentifier);
        if (npc == null) {
            sendMessage(sender, Component.text("NPC not found: " + npcIdentifier).color(NamedTextColor.RED));
            return;
        }

        String resolvedFile = audioFileManager.findAudioFileName(fileName);
        if (resolvedFile == null) {
            sendMessage(sender, Component.text("Audio file not found: " + fileName + " (looked in audio/ for .wav or .mp3)").color(NamedTextColor.RED));
            return;
        }

        voiceManager.playFile(npc, fileName).thenRun(() -> {
            if (sender instanceof Player player && player.isOnline()) {
                sendMessage(player, Component.text("Playing audio file: " + resolvedFile + " on NPC " + npc.getName()).color(NamedTextColor.GREEN));
            }
        });
    }

    private void handleDebug(CommandSender sender) {
        if (!sender.hasPermission("npcvoice.debug")) {
            sendNoPermission(sender);
            return;
        }

        boolean currentDebug = plugin.getConfigManager().debug();
        plugin.getConfigManager().toggleDebug();
        boolean newDebug = plugin.getConfigManager().debug();

        sendMessage(sender, Component.text("Debug mode: " + (newDebug ? "enabled" : "disabled")).color(
                newDebug ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private NPC findNPC(String identifier) {
        try {
            int id = Integer.parseInt(identifier);
            return CitizensAPI.getNPCRegistry().getById(id);
        } catch (NumberFormatException e) {
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (npc.getName().equalsIgnoreCase(identifier)) {
                    return npc;
                }
            }
            return null;
        }
    }

    private void sendNoPermission(CommandSender sender) {
        sendMessage(sender, Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
    }

    private void sendMessage(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("reload", "speak", "stop", "cache", "debug", "audio"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("audio")) {
            completions.addAll(List.of("list", "play"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("speak") || args[0].equalsIgnoreCase("stop"))) {
            CitizensAPI.getNPCRegistry().forEach(npc -> completions.add(String.valueOf(npc.getId())));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("cache")) {
            completions.add("clear");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("audio") && args[1].equalsIgnoreCase("play")) {
            CitizensAPI.getNPCRegistry().forEach(npc -> completions.add(String.valueOf(npc.getId())));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("audio") && args[1].equalsIgnoreCase("play")) {
            completions.addAll(audioFileManager.listAudioFiles());
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
