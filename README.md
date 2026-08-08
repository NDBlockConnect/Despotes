# Despotes

> **Despotes** — A Minecraft: Java Edition mod that opens an in-process local control channel, letting external controllers drive the game *without ever touching your keyboard or mouse*.
>
> Despotes 是一个 Minecraft: Java Edition 模组，它在游戏进程内开启本地控制通道，允许外部控制源驱动游戏，且**完全不占用用户的键盘与鼠标**。

**Default documentation language: English.** A synchronized Chinese copy is maintained under [`README_zh.md`](README_zh.md) and `docs/zh/`.
**默认文档语言为英文**，中文副本同步维护于 [`README_zh.md`](README_zh.md) 与 `docs/zh/`。

---

## What it does

Despotes runs a small control server *inside* the Minecraft client process:

| Capability | How |
|---|---|
| **Input injection** | Key presses, typing, movement, GUI clicks and world interactions are injected directly into the game's own input handlers. The OS keyboard/mouse is never touched; no window focus is required. |
| **Camera control** | View rotation is applied to the player directly, bypassing the mouse pipeline. |
| **Screen capture** | The framebuffer is captured from the render thread, so screenshots work even when the game window is unfocused or minimized (where the platform allows it). Frames can be fetched over HTTP or saved to disk. |
| **External control sources** | A `despotes.json` file in the game directory declares which control sources are enabled: built-in HTTP server, stdin command line, file-drop directory, and plugin sources. |
| **Visualization** | Every externally sourced operation is shown on an in-game overlay and appended to `despotes-oplog.jsonl`, so it is always visible *what* the external controller is doing. |

## Compatibility (v26.0 line)

| Platform | Branch | Minecraft versions |
|---|---|---|
| Minecraft Native (premain javaagent + Mixin) | `native` | 1.20 – 26.2 |
| Aprism Native (`.aje`) | `aprism` | 26.1 – 26.2 |
| Fabric | `fabric` | 1.20 – 26.2 |
| NeoForge | `neoforge` | 1.20.1 – 26.2 |
| Forge (≤ 1.21.4) | `forge` | 1.20 – 1.21.4 |

One branch per loader (branch specification follows Aprism docs §12). Each branch carries a `common/` copy of the loader-agnostic control core plus loader-specific entrypoints and version subprojects.

## Quick start

1. Download the artifact for your loader and Minecraft version from [Releases](../../releases) and drop it into the corresponding mods folder (`mods/` for Fabric/NeoForge/Forge/Aprism; launch with `-javaagent:Despotes-...jar` for the Native loader).
2. Launch the game once; `despotes.json` is generated in the game directory.
3. Control the game, e.g.:

```bash
curl -X POST http://127.0.0.1:25585/despotes/v1/actions \
  -H "Content-Type: application/json" \
  -d '{"type":"key","keys":["key.keyboard.w"],"holdTicks":20}'
```

See [docs/02-Control-Protocol.md](docs/02-Control-Protocol.md) for the full HTTP/CLI protocol and [docs/03-Configuration.md](docs/03-Configuration.md) for configuration.

## Security model

- All transports bind to `127.0.0.1` by default and accept loopback sources only.
- Remote control sources must be explicitly added to `security.allowSources` by the user.
- Despotes never sends any data out of the machine; it only *listens*.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

## Versioning

Despotes follows the Aprism version scheme `v<Year>.<minor>[-Alpha.<n>]` (see [docs/04-Versioning.md](docs/04-Versioning.md)). Alpha builds are published as GitHub **Pre-Releases**; the minor-version release (`v26.0`) is published as a GitHub **Release**.
