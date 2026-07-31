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
- 🔊 Play custom audio files
- 👤 Citizens NPC integration
- 🎧 Native Simple Voice Chat support
- ⚡ Audio cache for better performance
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

| Command | Description |
|----------|-------------|
| `/npcvoice reload` | Reload the configuration |
| `/npcvoice speak` | Make an NPC speak |
| `/npcvoice stop` | Stop the current audio |
| `/npcvoice cache` | Manage audio cache |
| `/npcvoice debug` | Toggle debug mode |
| `/npcvoice audio` | Audio management |

Alias:

```
/nv
```

---

## Permissions

| Permission | Description |
|------------|-------------|
| `npcvoice.reload` | Reload plugin |
| `npcvoice.speak` | Make NPCs speak |
| `npcvoice.cache.clear` | Clear cache |
| `npcvoice.debug` | Debug mode |
| `npcvoice.audio.list` | List audio files |
| `npcvoice.audio.play` | Play audio files |

---

# Demo

## Video Examples

### Basic Usage
<video src="https://i.snipp.gg/4918533360320604/bcff722a4ba1c42097b89030abc988cc.mp4" poster="https://i.snipp.gg/4918533360320604/thumb/055853aba41332ee39d8272856066105.jpg"></video>

### Custom Audio
<video src="https://i.snipp.gg/4918533360320604/421a6baee2344daa0b39404540c749d2.mp4" poster="https://i.snipp.gg/4918533360320604/thumb/b70d5867395c819b5e888eca9aabb59d.jpg"></video>

---

## Configuration

NPCVoice supports multiple voice providers.

Examples include:

- Edge TTS
- OpenAI TTS
- ElevenLabs

Configuration is done through `config.yml`.

---

## API

Developers can use the built-in API to trigger NPC speech programmatically.

```java
NPCVoiceAPI api = NPCVoicePlugin.getAPI();
```

---

## Planned Features

- [ ] Streaming TTS
- [ ] More TTS providers
- [ ] GUI editor
- [ ] Per-NPC voice settings

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
