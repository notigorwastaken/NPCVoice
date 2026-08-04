package com.npcvoice.tts;

import com.npcvoice.NPCVoicePlugin;
import com.npcvoice.config.ConfigManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class TTSManager {

    private final NPCVoicePlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, TTSProvider> providers = new ConcurrentHashMap<>();
    private TTSProvider activeProvider;

    public TTSManager(NPCVoicePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void load() {
        registerProvider(new PiperTTS(configManager, plugin.getDataFolder().toPath()));
        registerProvider(new ElevenLabsTTS(configManager));
        registerProvider(new OpenAITTS(configManager));
        registerProvider(new EdgeTTS(configManager));
        registerProvider(new GoogleTTS(configManager));
        registerProvider(new AzureTTS(configManager));
        registerProvider(new GttsTTS(configManager));

        String providerName = configManager.ttsProvider();
        TTSProvider provider = providers.get(providerName.toLowerCase());

        if (provider == null) {
            plugin.getLogger().warning("Unknown TTS provider: " + providerName + ". Falling back to piper.");
            provider = providers.get("piper");
        }

        if (provider == null || !provider.isAvailable()) {
            plugin.getLogger().warning("TTS provider '" + providerName + "' is not available (check path and permissions). Voice generation will fail.");
            plugin.getLogger().info("Configured providers: " + String.join(", ", providers.keySet()));
            plugin.getLogger().info("Piper executable path: " + configManager.piperExecutable());
        }

        activeProvider = provider;
        plugin.getLogger().info("Active TTS provider: " + (activeProvider != null ? activeProvider.name() + " (available: " + activeProvider.isAvailable() + ")" : "none"));
    }

    public void registerProvider(@NotNull TTSProvider provider) {
        providers.put(provider.name().toLowerCase(), provider);
    }

    public CompletableFuture<byte[]> generateSpeechAsync(@NotNull String text, @NotNull String voice) {
        return generateSpeechAsync(text, voice, activeProvider);
    }

    public CompletableFuture<byte[]> generateSpeechAsync(@NotNull String text, @NotNull String voice, @Nullable TTSProvider provider) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (provider == null || !provider.isAvailable()) {
                    plugin.getLogger().warning("No active TTS provider available.");
                    return null;
                }

                return provider.generateSpeech(text, voice);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to generate speech", e);
                return null;
            }
        });
    }

    public Optional<TTSProvider> getProvider(String name) {
        return Optional.ofNullable(providers.get(name.toLowerCase()));
    }

    public TTSProvider getActiveProvider() {
        return activeProvider;
    }

    public Map<String, TTSProvider> getAllProviders() {
        return Map.copyOf(providers);
    }

    public void shutdown() {
        providers.clear();
        activeProvider = null;
    }
}
