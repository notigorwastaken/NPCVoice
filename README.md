# NPCVoice

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/npcvoice?logo=modrinth&color=00AF5C)](https://modrinth.com/plugin/npcvoice)
[![Modrinth Version](https://img.shields.io/modrinth/v/npcvoice?logo=modrinth)](https://modrinth.com/plugin/npcvoice)
[![GitHub Release](https://img.shields.io/github/v/release/notigorwastaken/NPCVoice?logo=github)](https://github.com/notigorwastaken/NPCVoice/releases)
[![License](https://img.shields.io/github/license/notigorwastaken/NPCVoice)](https://github.com/notigorwastaken/NPCVoice/blob/master/LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper-1.21+-white?logo=minecraft)](https://papermc.io/)
[![GitHub Stars](https://img.shields.io/github/stars/notigorwastaken/NPCVoice?style=social)](https://github.com/notigorwastaken/NPCVoice/stargazers)
[![GitHub Issues](https://img.shields.io/github/issues/notigorwastaken/NPCVoice)](https://github.com/notigorwastaken/NPCVoice/issues)
[![GitHub Downloads](https://img.shields.io/github/downloads/notigorwastaken/NPCVoice/total)](https://github.com/notigorwastaken/NPCVoice/releases)

A Minecraft plugin that gives **Citizens NPCs** realistic voices using **Simple Voice Chat**.

NPCVoice allows NPCs to speak through AI-generated voices or pre-recorded audio, creating immersive roleplay experiences, quests, tutorials, and interactive servers.

---

## ✨ Features

- 🎙️ AI Text-to-Speech (TTS)
- ⚡ Streaming TTS (starts speaking while audio is still being generated)
- 🔤 Multiple TTS providers (Piper, OpenAI, ElevenLabs, Edge, Google Cloud, Azure, gTTS)
- 🔊 Play custom audio files
- 👤 Citizens NPC integration
- 🎚️ Per-NPC voice & provider settings (config + in-game GUI)
- 🖥️ In-game GUI editor
- 🗣️ Speak-to-speak (players talk to NPCs, NPCs reply with TTS)
- 🎧 Native Simple Voice Chat support
- 💾 Audio cache for better performance
- 🔄 Reload configuration without restarting
- 🛠️ Developer API
- 🌍 PlaceholderAPI support
- 🛡️ WorldGuard support
- 👥 LuckPerms permissions

---

## Requirements

- Java 21+
- Paper 1.21+
- Citizens
- Simple Voice Chat

Optional:

- PlaceholderAPI
- WorldGuard
- LuckPerms

---

## Installation

1. Install the required plugins.
2. Place `NPCVoice.jar` inside the `plugins` folder.
3. Start the server.
4. Configure `config.yml`.
5. Restart or execute:

```text
/npcvoice reload
```

---

## Commands

| Command            | Description                 |
|--------------------|-----------------------------|
| `/npcvoice reload` | Reload the configuration    |
| `/npcvoice speak`  | Make an NPC speak           |
| `/npcvoice stop`   | Stop the current audio      |
| `/npcvoice cache`  | Manage audio cache          |
| `/npcvoice debug`  | Toggle debug mode           |
| `/npcvoice audio`  | Audio management            |
| `/npcvoice gui`    | Open the in-game editor GUI |

Alias:

```
/nv
```

---

## Permissions

| Permission             | Description         |
|------------------------|---------------------|
| `npcvoice.reload`      | Reload plugin       |
| `npcvoice.speak`       | Make NPCs speak     |
| `npcvoice.cache.clear` | Clear cache         |
| `npcvoice.debug`       | Debug mode          |
| `npcvoice.audio.list`  | List audio files    |
| `npcvoice.audio.play`  | Play audio files    |
| `npcvoice.gui`         | Open the editor GUI |

---

## Configuration

NPCVoice supports multiple voice providers.

Examples include:

- Edge TTS
- OpenAI TTS
- ElevenLabs
- Google Cloud TTS
- Azure (Microsoft) TTS
- gTTS
- Piper (local, default)

Configuration is done through `config.yml`.

Each NPC can override the global provider and voice:

```yaml
npcs:
  my_npc:
    id: 123
    voice: narrator
    provider: openai
    stt_enabled: true
```

### Streaming TTS

Set `tts.streaming: true` to start playback while the audio is still being generated. Streaming is used automatically
when the active provider supports it (`openai`, `elevenlabs`, `azure`).

### Speak-to-speak

Enable `speak_to_speak.enabled: true` and configure a speech-to-text provider under `stt`:

```yaml
stt:
  provider: openai
  openai:
    api_key: "your-key"
```

While a player talks near an NPC (that has `stt_enabled`), their speech is transcribed and the NPC replies with
generated TTS audio.

---

## API

Developers can use the built-in API to trigger NPC speech programmatically.

```java
NPCVoiceAPI.speak(npc, "Hello there!");
NPCVoiceAPI.

speak(npc, "Hello there!","narrator");
```

Per-NPC settings can be edited at runtime:

```java
NPCVoiceAPI.setNpcVoice("my_npc","narrator");
NPCVoiceAPI.

setNpcProvider("my_npc","openai");
NPCVoiceAPI.

saveNpcs();
```

---

## Contributing

Pull Requests are welcome.

If you'd like to improve NPCVoice, feel free to fork the repository and submit a PR.

---

## License

This project is licensed under the MIT License.

---

# Support

If you find a bug or have a feature request, please open an Issue on GitHub.
