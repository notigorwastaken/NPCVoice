package com.npcvoice.config;

import com.npcvoice.NPCVoicePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ConfigManager {

    private final NPCVoicePlugin plugin;
    private FileConfiguration config;
    private final File configFile;
    private final Map<String, NPCConfig> npcConfigs = new ConcurrentHashMap<>();

    private String ttsProvider;
    private boolean ttsStreaming;
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
    private int httpConnectTimeoutMs;
    private int httpReadTimeoutMs;

    private String piperExecutable;
    private String piperModel;
    private String piperUrl;

    private String elevenLabsApiKey;
    private String elevenLabsModel;
    private String elevenLabsApiUrl;
    private String elevenLabsVoice;

    private String openaiApiKey;
    private String openaiModel;
    private String openaiVoice;
    private String openaiApiUrl;

    private String edgeTtsVoice;
    private int edgeTtsRate;
    private int edgeTtsVolume;
    private int edgeTtsPitch;

    private String googleApiKey;
    private String googleLanguageCode;
    private String googleVoice;
    private double googlePitch;
    private double googleSpeed;

    private String azureApiKey;
    private String azureRegion;
    private String azureVoice;
    private String azurePitch;
    private double azureSpeed;

    private String gttsExecutable;
    private String gttsLang;
    private boolean gttsSlow;

    private String sttProvider;
    private String sttOpenaiApiKey;
    private String sttOpenaiModel;
    private String sttOpenaiUrl;
    private String sttGoogleApiKey;
    private String sttGoogleUrl;
    private String sttGoogleLanguageCode;

    private boolean s2sEnabled;
    private int s2sRadius;
    private int s2sCooldown;
    private int s2sMaxAudioMs;
    private int s2sMinSpeechMs;
    private int s2sSilenceMs;

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
        ttsProvider = nonBlank(config.getString("tts.provider"), "piper");
        ttsStreaming = config.getBoolean("tts.streaming", true);
        defaultVoice = nonBlank(config.getString("voices.default"), "narrator");
        voiceChatDistance = Math.max(1, config.getInt("voicechat.distance", 20));
        voiceChatVolume = Math.clamp(config.getDouble("voicechat.volume", 1.0), 0.0, 1.0);
        cacheEnabled = config.getBoolean("cache.enabled", true);
        cacheMaxSizeMb = Math.max(0, config.getInt("cache.max_size_mb", 500));
        defaultCooldown = Math.max(0, config.getInt("dialogue.default_cooldown", 3));
        defaultApproachRadius = Math.max(0, config.getInt("dialogue.default_approach_radius", 6));
        debug = config.getBoolean("debug", false);
        speakingIconEnabled = config.getBoolean("nameplate.speaking_icon.enabled", true);
        speakingIcon = config.getString("nameplate.speaking_icon.icon", "🗨");
        httpConnectTimeoutMs = Math.clamp(config.getInt("network.connect_timeout_ms", 10000), 1000, 120000);
        httpReadTimeoutMs = Math.clamp(config.getInt("network.read_timeout_ms", 60000), 1000, 300000);

        piperExecutable = config.getString("tts.piper.executable", "piper");
        piperModel = config.getString("tts.piper.model", "en_US-lessac-medium");
        piperUrl = config.getString("tts.piper.url",
                "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx");

        elevenLabsApiKey = config.getString("tts.elevenlabs.api_key", "");
        elevenLabsModel = config.getString("tts.elevenlabs.model", "eleven_multilingual_v2");
        elevenLabsApiUrl = config.getString("tts.elevenlabs.api_url", "https://api.elevenlabs.io/v1/text-to-speech");
        elevenLabsVoice = config.getString("tts.elevenlabs.voice", "");

        openaiApiKey = config.getString("tts.openai.api_key", "");
        openaiModel = config.getString("tts.openai.model", "tts-1");
        openaiVoice = config.getString("tts.openai.voice", "alloy");
        openaiApiUrl = config.getString("tts.openai.api_url", "https://api.openai.com/v1/audio/speech");

        edgeTtsVoice = config.getString("tts.edgetts.voice", "en-US-JennyNeural");
        edgeTtsRate = config.getInt("tts.edgetts.rate", 0);
        edgeTtsVolume = config.getInt("tts.edgetts.volume", 0);
        edgeTtsPitch = config.getInt("tts.edgetts.pitch", 0);

        googleApiKey = config.getString("tts.google.api_key", "");
        googleLanguageCode = config.getString("tts.google.language_code", "en-US");
        googleVoice = config.getString("tts.google.voice", "en-US-Neural2-C");
        googlePitch = config.getDouble("tts.google.pitch", 0);
        googleSpeed = config.getDouble("tts.google.speed", 1.0);

        azureApiKey = config.getString("tts.azure.api_key", "");
        azureRegion = config.getString("tts.azure.region", "");
        azureVoice = config.getString("tts.azure.voice", "en-US-JennyNeural");
        azurePitch = config.getString("tts.azure.pitch", "+0Hz");
        azureSpeed = config.getDouble("tts.azure.speed", 1.0);

        gttsExecutable = config.getString("tts.gtts.executable", "gtts-cli");
        gttsLang = config.getString("tts.gtts.lang", "en");
        gttsSlow = config.getBoolean("tts.gtts.slow", false);

        sttProvider = config.getString("stt.provider", "none");
        sttOpenaiApiKey = config.getString("stt.openai.api_key", "");
        sttOpenaiModel = config.getString("stt.openai.model", "whisper-1");
        sttOpenaiUrl = config.getString("stt.openai.api_url", "https://api.openai.com/v1/audio/transcriptions");
        sttGoogleApiKey = config.getString("stt.google.api_key", "");
        sttGoogleUrl = config.getString("stt.google.api_url", "https://speech.googleapis.com/v1/speech:recognize");
        sttGoogleLanguageCode = config.getString("stt.google.language_code", "en-US");

        s2sEnabled = config.getBoolean("speak_to_speak.enabled", false);
        s2sRadius = Math.max(0, config.getInt("speak_to_speak.radius", 8));
        s2sCooldown = Math.max(0, config.getInt("speak_to_speak.cooldown", 10));
        s2sMaxAudioMs = Math.clamp(config.getInt("speak_to_speak.max_audio_ms", 10000), 1000, 120000);
        s2sMinSpeechMs = Math.clamp(config.getInt("speak_to_speak.min_speech_ms", 700), 0, s2sMaxAudioMs);
        s2sSilenceMs = Math.clamp(config.getInt("speak_to_speak.silence_ms", 400), 50, s2sMaxAudioMs);
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void loadNpcs() {
        npcConfigs.clear();
        ConfigurationSection npcsSection = config.getConfigurationSection("npcs");
        if (npcsSection == null) return;

        for (String key : npcsSection.getKeys(false)) {
            NPCConfig npcConfig = NPCConfig.fromConfig(npcsSection.getConfigurationSection(key), key);
            if (npcConfig == null) {
                plugin.getLogger().warning("Ignoring invalid NPC configuration at npcs." + key
                        + ": expected a configuration section.");
                continue;
            }
            npcConfigs.put(key, npcConfig);
        }
    }

    private void loadVoicePresets() {
        voicePresets.clear();
        ConfigurationSection presetsSection = config.getConfigurationSection("voices.presets");
        if (presetsSection == null) return;

        for (String key : presetsSection.getKeys(false)) {
            String voiceId = presetsSection.getString(key);
            if (voiceId == null || voiceId.isBlank()) {
                plugin.getLogger().warning("Ignoring empty voice preset: " + key);
                continue;
            }
            voicePresets.put(key, voiceId.trim());
        }
    }

    public String resolveVoiceId(String voiceName) {
        return resolveVoiceId(voiceName, ttsProvider);
    }

    public String resolveVoiceId(String voiceName, String provider) {
        if (voiceName == null || voiceName.isBlank() || voiceName.equals(defaultVoice)) {
            String providerDefault = providerDefaultVoice(provider);
            if (providerDefault != null && !providerDefault.isBlank()) {
                return providerDefault.trim();
            }
            voiceName = defaultVoice;
        }
        String trimmed = voiceName.trim();
        return voicePresets.getOrDefault(trimmed, trimmed);
    }

    private String providerDefaultVoice(String provider) {
        if (provider == null) return null;
        return switch (provider.toLowerCase()) {
            case "piper" -> piperModel;
            case "elevenlabs" -> elevenLabsVoice;
            case "openai" -> openaiVoice;
            case "edgetts" -> edgeTtsVoice;
            case "google" -> googleVoice;
            case "azure" -> azureVoice;
            case "gtts" -> gttsLang;
            default -> null;
        };
    }

    public String ttsProvider() { return ttsProvider; }

    public boolean ttsStreaming() {
        return ttsStreaming;
    }
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
    public int httpConnectTimeoutMs() { return httpConnectTimeoutMs; }
    public int httpReadTimeoutMs() { return httpReadTimeoutMs; }

    public String piperExecutable() { return piperExecutable; }
    public String piperModel() { return piperModel; }
    public String piperUrl() { return piperUrl; }

    public String elevenLabsApiKey() { return elevenLabsApiKey; }
    public String elevenLabsModel() { return elevenLabsModel; }
    public String elevenLabsApiUrl() { return elevenLabsApiUrl; }
    public String elevenLabsVoice() { return elevenLabsVoice; }

    public String openaiApiKey() { return openaiApiKey; }
    public String openaiModel() { return openaiModel; }
    public String openaiVoice() { return openaiVoice; }
    public String openaiApiUrl() { return openaiApiUrl; }

    public String edgeTtsVoice() { return edgeTtsVoice; }
    public int edgeTtsRate() { return edgeTtsRate; }
    public int edgeTtsVolume() { return edgeTtsVolume; }
    public int edgeTtsPitch() { return edgeTtsPitch; }

    public String googleApiKey() {
        return googleApiKey;
    }

    public String googleLanguageCode() {
        return googleLanguageCode;
    }

    public String googleVoice() {
        return googleVoice;
    }

    public double googlePitch() {
        return googlePitch;
    }

    public double googleSpeed() {
        return googleSpeed;
    }

    public String azureApiKey() {
        return azureApiKey;
    }

    public String azureRegion() {
        return azureRegion;
    }

    public String azureVoice() {
        return azureVoice;
    }

    public String azurePitch() {
        return azurePitch;
    }

    public double azureSpeed() {
        return azureSpeed;
    }

    public String gttsExecutable() {
        return gttsExecutable;
    }

    public String gttsLang() {
        return gttsLang;
    }

    public boolean gttsSlow() {
        return gttsSlow;
    }

    public String sttProvider() {
        return sttProvider;
    }

    public String sttOpenaiApiKey() {
        return sttOpenaiApiKey;
    }

    public String sttOpenaiModel() {
        return sttOpenaiModel;
    }

    public String sttOpenaiUrl() {
        return sttOpenaiUrl;
    }

    public String sttGoogleApiKey() {
        return sttGoogleApiKey;
    }

    public String sttGoogleUrl() {
        return sttGoogleUrl;
    }

    public String sttGoogleLanguageCode() {
        return sttGoogleLanguageCode;
    }

    public boolean s2sEnabled() {
        return s2sEnabled;
    }

    public int s2sRadius() {
        return s2sRadius;
    }

    public int s2sCooldown() {
        return s2sCooldown;
    }

    public int s2sMaxAudioMs() {
        return s2sMaxAudioMs;
    }

    public int s2sMinSpeechMs() {
        return s2sMinSpeechMs;
    }

    public int s2sSilenceMs() {
        return s2sSilenceMs;
    }

    public NPCConfig npcConfig(String name) { return npcConfigs.get(name); }

    public Optional<NPCConfig> npcConfigByNpcId(int npcId) {
        return npcConfigs.values().stream()
                .filter(cfg -> cfg.id() == npcId)
                .findFirst();
    }

    public Map<String, NPCConfig> allNpcConfigs() { return Map.copyOf(npcConfigs); }
    public Map<String, String> voicePresets() { return Map.copyOf(voicePresets); }

    public void setNpcVoice(String name, String voice) {
        NPCConfig npcConfig = npcConfigs.get(name);
        if (npcConfig == null) return;
        npcConfigs.put(name, npcConfig.withVoice(voice));
    }

    public void setNpcProvider(String name, String provider) {
        NPCConfig npcConfig = npcConfigs.get(name);
        if (npcConfig == null) return;
        npcConfigs.put(name, npcConfig.withProvider(provider));
    }

    public void setNpcSttEnabled(String name, boolean enabled) {
        NPCConfig npcConfig = npcConfigs.get(name);
        if (npcConfig == null) return;
        npcConfigs.put(name, npcConfig.withSttEnabled(enabled));
    }

    public void saveNpcs() {
        ConfigurationSection npcsSection = config.getConfigurationSection("npcs");
        if (npcsSection == null) {
            npcsSection = config.createSection("npcs");
        }

        for (Map.Entry<String, NPCConfig> entry : npcConfigs.entrySet()) {
            ConfigurationSection npcSection = npcsSection.getConfigurationSection(entry.getKey());
            if (npcSection == null) {
                npcSection = npcsSection.createSection(entry.getKey());
            }
            final ConfigurationSection section = npcSection;
            NPCConfig npcConfig = entry.getValue();
            section.set("id", npcConfig.id());
            npcConfig.voice().ifPresentOrElse(v -> section.set("voice", v), () -> section.set("voice", null));
            npcConfig.provider().ifPresentOrElse(p -> section.set("provider", p), () -> section.set("provider", null));
            section.set("stt_enabled", npcConfig.sttEnabled());
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save NPC config", e);
        }
    }

    public static final class NPCConfig {

        private final String name;
        private final int id;
        private final String voice;
        private final String provider;
        private final boolean sttEnabled;
        private final SpeechConfig click;
        private final SpeechConfig approach;

        private NPCConfig(String name, int id, String voice, String provider, boolean sttEnabled,
                          SpeechConfig click, SpeechConfig approach) {
            this.name = name;
            this.id = id;
            this.voice = voice;
            this.provider = provider;
            this.sttEnabled = sttEnabled;
            this.click = click;
            this.approach = approach;
        }

        public static NPCConfig fromConfig(ConfigurationSection section, String name) {
            if (section == null) return null;

            int id = section.getInt("id", -1);
            String voice = section.getString("voice", null);
            String provider = section.getString("provider", null);
            boolean sttEnabled = section.getBoolean("stt_enabled", true);

            SpeechConfig click = SpeechConfig.fromConfig(section.getConfigurationSection("click"), "click");
            SpeechConfig approach = SpeechConfig.fromConfig(section.getConfigurationSection("approach"), "approach");

            return new NPCConfig(name, id, voice, provider, sttEnabled, click, approach);
        }

        public NPCConfig withVoice(String voice) {
            return new NPCConfig(name, id, voice, provider, sttEnabled, click, approach);
        }

        public NPCConfig withProvider(String provider) {
            return new NPCConfig(name, id, voice, provider, sttEnabled, click, approach);
        }

        public NPCConfig withSttEnabled(boolean sttEnabled) {
            return new NPCConfig(name, id, voice, provider, sttEnabled, click, approach);
        }

        public String name() { return name; }
        public int id() { return id; }
        public Optional<String> voice() { return Optional.ofNullable(voice); }

        public Optional<String> provider() {
            return Optional.ofNullable(provider);
        }

        public boolean sttEnabled() {
            return sttEnabled;
        }
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
            int cooldown = Math.max(-1, section.getInt("cooldown", -1));
            int radius = Math.max(-1, section.getInt("radius", -1));
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
