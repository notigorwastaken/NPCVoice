package com.npcvoice.config;

import com.npcvoice.NPCVoicePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigManager {

    private final NPCVoicePlugin plugin;
    private FileConfiguration config;
    private final File configFile;
    private final Map<String, NPCConfig> npcConfigs = new ConcurrentHashMap<>();

    private String ttsProvider;
    private String defaultVoice;
    private int voiceChatDistance;
    private double voiceChatVolume;
    private boolean cacheEnabled;
    private int cacheMaxSizeMb;
    private int defaultCooldown;
    private int defaultApproachRadius;
    private boolean debug;
    private boolean speakingIconEnabled;
    private String speakingIcon;

    private String piperExecutable;
    private String piperModel;
    private String piperUrl;

    private String elevenLabsApiKey;
    private String elevenLabsModel;
    private String elevenLabsApiUrl;

    private String openaiApiKey;
    private String openaiModel;
    private String openaiVoice;
    private String openaiApiUrl;

    private String edgeTtsVoice;
    private int edgeTtsRate;
    private int edgeTtsVolume;
    private int edgeTtsPitch;

    private final Map<String, String> voicePresets = new ConcurrentHashMap<>();

    public ConfigManager(NPCVoicePlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }

        loadSettings();
        loadNpcs();
        loadVoicePresets();
    }

    public void reload() {
        load();
    }

    private void loadSettings() {
        ttsProvider = config.getString("tts.provider", "piper");
        defaultVoice = config.getString("voices.default", "narrator");
        voiceChatDistance = config.getInt("voicechat.distance", 20);
        voiceChatVolume = Math.clamp(config.getDouble("voicechat.volume", 1.0), 0.0, 1.0);
        cacheEnabled = config.getBoolean("cache.enabled", true);
        cacheMaxSizeMb = config.getInt("cache.max_size_mb", 500);
        defaultCooldown = config.getInt("dialogue.default_cooldown", 3);
        defaultApproachRadius = config.getInt("dialogue.default_approach_radius", 6);
        debug = config.getBoolean("debug", false);
        speakingIconEnabled = config.getBoolean("nameplate.speaking_icon.enabled", true);
        speakingIcon = config.getString("nameplate.speaking_icon.icon", "🗨");

        piperExecutable = config.getString("tts.piper.executable", "piper");
        piperModel = config.getString("tts.piper.model", "en_US-lessac-medium");
        piperUrl = config.getString("tts.piper.url",
                "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx");

        elevenLabsApiKey = config.getString("tts.elevenlabs.api_key", "");
        elevenLabsModel = config.getString("tts.elevenlabs.model", "eleven_multilingual_v2");
        elevenLabsApiUrl = config.getString("tts.elevenlabs.api_url", "https://api.elevenlabs.io/v1/text-to-speech");

        openaiApiKey = config.getString("tts.openai.api_key", "");
        openaiModel = config.getString("tts.openai.model", "tts-1");
        openaiVoice = config.getString("tts.openai.voice", "alloy");
        openaiApiUrl = config.getString("tts.openai.api_url", "https://api.openai.com/v1/audio/speech");

        edgeTtsVoice = config.getString("tts.edgetts.voice", "en-US-JennyNeural");
        edgeTtsRate = config.getInt("tts.edgetts.rate", 0);
        edgeTtsVolume = config.getInt("tts.edgetts.volume", 0);
        edgeTtsPitch = config.getInt("tts.edgetts.pitch", 0);
    }

    private void loadNpcs() {
        npcConfigs.clear();
        ConfigurationSection npcsSection = config.getConfigurationSection("npcs");
        if (npcsSection == null) return;

        for (String key : npcsSection.getKeys(false)) {
            NPCConfig npcConfig = NPCConfig.fromConfig(npcsSection.getConfigurationSection(key), key);
            npcConfigs.put(key, npcConfig);
        }
    }

    private void loadVoicePresets() {
        voicePresets.clear();
        ConfigurationSection presetsSection = config.getConfigurationSection("voices.presets");
        if (presetsSection == null) return;

        for (String key : presetsSection.getKeys(false)) {
            voicePresets.put(key, presetsSection.getString(key));
        }
    }

    public String resolveVoiceId(String voiceName) {
        if (voiceName == null) return defaultVoice;
        return voicePresets.getOrDefault(voiceName, voiceName);
    }

    public String ttsProvider() { return ttsProvider; }
    public String defaultVoice() { return defaultVoice; }
    public int voiceChatDistance() { return voiceChatDistance; }
    public double voiceChatVolume() { return voiceChatVolume; }
    public boolean cacheEnabled() { return cacheEnabled; }
    public int cacheMaxSizeMb() { return cacheMaxSizeMb; }
    public int defaultCooldown() { return defaultCooldown; }
    public int defaultApproachRadius() { return defaultApproachRadius; }
    public boolean debug() { return debug; }
    public void toggleDebug() { this.debug = !this.debug; }
    public boolean speakingIconEnabled() { return speakingIconEnabled; }
    public String speakingIcon() { return speakingIcon; }

    public String piperExecutable() { return piperExecutable; }
    public String piperModel() { return piperModel; }
    public String piperUrl() { return piperUrl; }

    public String elevenLabsApiKey() { return elevenLabsApiKey; }
    public String elevenLabsModel() { return elevenLabsModel; }
    public String elevenLabsApiUrl() { return elevenLabsApiUrl; }

    public String openaiApiKey() { return openaiApiKey; }
    public String openaiModel() { return openaiModel; }
    public String openaiVoice() { return openaiVoice; }
    public String openaiApiUrl() { return openaiApiUrl; }

    public String edgeTtsVoice() { return edgeTtsVoice; }
    public int edgeTtsRate() { return edgeTtsRate; }
    public int edgeTtsVolume() { return edgeTtsVolume; }
    public int edgeTtsPitch() { return edgeTtsPitch; }

    public NPCConfig npcConfig(String name) { return npcConfigs.get(name); }
    public Map<String, NPCConfig> allNpcConfigs() { return Map.copyOf(npcConfigs); }
    public Map<String, String> voicePresets() { return Map.copyOf(voicePresets); }

    public static final class NPCConfig {

        private final String name;
        private final int id;
        private final String voice;
        private final SpeechConfig click;
        private final SpeechConfig approach;

        private NPCConfig(String name, int id, String voice, SpeechConfig click, SpeechConfig approach) {
            this.name = name;
            this.id = id;
            this.voice = voice;
            this.click = click;
            this.approach = approach;
        }

        public static NPCConfig fromConfig(ConfigurationSection section, String name) {
            if (section == null) return null;

            int id = section.getInt("id", -1);
            String voice = section.getString("voice", null);

            SpeechConfig click = SpeechConfig.fromConfig(section.getConfigurationSection("click"), "click");
            SpeechConfig approach = SpeechConfig.fromConfig(section.getConfigurationSection("approach"), "approach");

            return new NPCConfig(name, id, voice, click, approach);
        }

        public String name() { return name; }
        public int id() { return id; }
        public Optional<String> voice() { return Optional.ofNullable(voice); }
        public Optional<SpeechConfig> click() { return Optional.ofNullable(click); }
        public Optional<SpeechConfig> approach() { return Optional.ofNullable(approach); }
    }

    public static final class SpeechConfig {

        private final String type;
        private final boolean random;
        private final int cooldown;
        private final int radius;
        private final List<String> speech;

        private SpeechConfig(String type, boolean random, int cooldown, int radius, List<String> speech) {
            this.type = type;
            this.random = random;
            this.cooldown = cooldown;
            this.radius = radius;
            this.speech = speech;
        }

        public static SpeechConfig fromConfig(ConfigurationSection section, String type) {
            if (section == null) return null;

            boolean random = section.getBoolean("random", false);
            int cooldown = section.getInt("cooldown", 3);
            int radius = section.getInt("radius", 6);
            List<String> speech = section.getStringList("speech");

            return new SpeechConfig(type, random, cooldown, radius, speech);
        }

        public String type() { return type; }
        public boolean random() { return random; }
        public int cooldown() { return cooldown; }
        public int radius() { return radius; }
        public List<String> speech() { return List.copyOf(speech); }
    }
}
