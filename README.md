# NPCVoice

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

Place your recordings here.

### Basic Usage

https://github.com/USER/REPO/assets/example1.mp4

### AI Voice

https://github.com/USER/REPO/assets/example2.mp4

### Custom Audio

https://github.com/USER/REPO/assets/example3.mp4

> Replace the links above with your uploaded GitHub videos. GitHub plays `.mp4` files directly inside the README.

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
