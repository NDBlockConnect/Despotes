# Despotes Architecture

> Doc 1 / 4 | Despotes Docs > Version: v26.0 | Status: in development
> Spec language: English (Chinese copies synchronized)

## 1. Goals

Despotes provides a **local, in-process control channel** for Minecraft: Java Edition that:

1. Injects input (keyboard, chat, movement, GUI clicks, world interaction, camera rotation) **through the game's own handler classes**, never through OS keyboard/mouse events — no focus required, no key/mouse contention with the user.
2. Captures the game framebuffer **from the render thread**, usable while unfocused.
3. Accepts commands from **configurable external sources** (`despotes.json` in the game directory).
4. **Visualizes** every externally sourced operation in-game and on disk.

Non-goals for v26.0: server-side control, plugin scripting engines, non-loopback defaults, Bedrock support.

## 2. Layer model

```
┌────────────────────────────────────────────────────────────┐
│ Transports (sources)          HTTP / CLI(stdin) / FileDrop │
│                               + plugin SPI (v26.1+)        │
├────────────────────────────────────────────────────────────┤
│ Protocol layer    JSON command schema (actions/query/shot) │
├────────────────────────────────────────────────────────────┤
│ Dispatcher        queue → game-thread drain (1 op / tick)  │
│                   security gate · source tagging           │
├────────────────────────────────────────────────────────────┤
│ Action layer      Key · Type · Move · Click · Look ·       │
│                   UseItem · Screenshot · Query · Config    │
├────────────────────────────────────────────────────────────┤
│ Platform layer    IGamePlatform (SPI)                      │
│   native │ fabric │ neoforge │ forge │ aprism              │
├────────────────────────────────────────────────────────────┤
│ Minecraft client process                                   │
└────────────────────────────────────────────────────────────┘
```

The **common** source set contains everything above the platform layer and is byte-identical across loader branches (each loader branch keeps its own copy, per the per-loader-branch spec).

## 3. Control channel

### 3.1 Threading

- Transport threads parse JSON and perform validation only.
- Validated commands are enqueued into a bounded `LinkedBlockingQueue<ControlCommand>` (default capacity 1024; overflow → error reply `QUEUE_FULL`).
- A client tick event drains at most `maxActionsPerTick` (default 4) commands and executes them **on the game thread**, guaranteeing thread safety against game state.
- `Screenshot` executes on the render thread via the platform's frame-end hook; the HTTP response blocks until the capture completes (timeout 5 s default).

### 3.2 Transports

| Transport | Endpoint | Enabled by default |
|---|---|---|
| HTTP | `127.0.0.1:25585/despotes/v1/*` | yes |
| CLI | game process stdin, one JSON per line | yes |
| FileDrop | `despotes-in/*.json` consumed per tick | no |

HTTP server: `com.sun.net.httpserver.HttpServer` with a cached thread pool (max 8 workers). Endpoints defined in doc 02. A request carries the transport identity; the dispatcher tags every executed action with `{source, transport, requestId}` for visualization and logging.

### 3.3 Security gate

- Bind address defaults to `127.0.0.1`; the HTTP server **rejects non-loopback sources** regardless of bind configuration unless the remote address is listed in `security.allowSources`.
- `security.enabled=false` (emergency switch) disables all transports except CLI.
- Commands can be gated by `security.requireToken` + `security.token` (off by default; loopback is trusted).

## 4. Input injection

All injection targets game-internal handler objects; the OS input devices are never touched.

| Action | Mechanism (all loaders) |
|---|---|
| key press/release | Direct invocation of `KeyMapping` click-count queue / key state map on the Options object, plus a tick scheduler that holds/releases. No window focus needed because the handler is called directly. |
| typing (chat/sign/book) | Direct append into the focused edit-box widget's value (GUI text), or chat send via `Connection` for command lines prefixed `/`. |
| movement | Per-tick synthetic state applied to `KeyboardHandler` movement fields (forward/left/back/right/jump/sneak/sprint) on the game thread. |
| look | Direct write of the player's rotation fields on the game thread (delta or absolute), with smoothing option. |
| GUI click | Resolution of the hovered widget at (x, y) on the current screen, then direct invocation of the mouse-click handler with the given button. |
| world interaction | Construction of a client-side use/place packet via `gameMode`/`Connection` on the game thread (attack, use item, place block, drop). |

The exact member names are version-resolved by the platform layer (mappings: intermediary/official for Fabric 1.20–1.21.x, official names from 26.1+, MCPConfig names for Forge/NeoForge).

## 5. Screen capture

- Primary path: on the render thread, after the main framebuffer is drawn, `glReadPixels` into a preallocated direct buffer; encode PNG (or JPEG q=0.8) off-thread; store or stream.
- Storage: `despotes-shots/<timestamp>-<requestId>.png` in the game directory (configurable), or returned directly by `GET /despotes/v1/screenshot`.
- Unfocused capture: since the read happens from the GL context's own thread before swap, captures work without window focus. (Minimized windows may yield black frames on some drivers — documented limitation.)

## 6. Platform SPI

```java
public interface IGamePlatform {
    String loaderId();                 // "native" | "fabric" | "neoforge" | "forge" | "aprism"
    void scheduleOnClientThread(Runnable r);
    void scheduleOnRenderThread(Runnable r);
    boolean isInGame();                // has client + world
    PlayerHandle player();             // null when not in world
    ScreenHandle currentScreen();      // null when no GUI
    ShotHandle captureFrame(ScreenshotOptions o);
    void log(String line);
}
```

Handles (`PlayerHandle`, `ScreenHandle`, …) are common-layer interfaces implemented per loader with version-adapter classes. This isolates every Minecraft symbol to the platform module, keeping the dispatcher/actions loader-agnostic.

Loader entry points:

| Loader | Entrypoint | Tick hooks |
|---|---|---|
| native | premain agent → Mixin init | Mixin into client tick & frame end |
| fabric | `fabric.mod.json` client entrypoint | Fabric API client tick/frame events |
| neoforge | `@Mod` | `ClientTickEvent.Pre/Post`, `RenderLevelStageEvent` end |
| forge | `@Mod` | same event names (legacy bus variants) |
| aprism | `aprism.manifest.json` main entrypoint | Aprism phase CLIENT + CLIENT tick event |

## 7. Visualization

- **Overlay**: top-left HUD list of the last N (default 8) external operations: `[HTTP#42] key W hold 1.0s ✓`. Rendered by the platform's GUI-draw hook; toggled with F8 (configurable, off-able).
- **Op log**: `despotes-oplog.jsonl` in the game directory, one JSON line per executed action with source, transport, params, result, duration.
- Both are on by default and can be disabled in config.

## 8. Build & branch model

Per Aprism docs §12 branch spec: **one branch per loader** — `native`, `aprism`, `fabric`, `neoforge`, `forge` — plus `main` holding docs, spec, and CI manifests only.

Each loader branch layout:

```
common/                  loader-agnostic control core (Gradle project)
<loader>/                loader entrypoint + platform SPI impl
<loader>-<mc>/           per-MC-version subproject when mappings/APIs diverge
gradle/libs.versions.toml (version catalog, Aprism §10.5)
```

| MC range | Gradle | Java target | Loom/toolchain |
|---|---|---|---|
| 1.20 – 1.20.4 | 8.11 | 17 | Fabric Loom / ForgeGradle-userdev-equivalent |
| 1.20.5 – 1.21.4 | 8.11 | 21 | same |
| 26.1 – 26.2 | 9.7 | 25 | Loom 1.18+ / NeoGradle 7.1+ |

## 9. Risks

| Risk | Mitigation |
|---|---|
| Member names drift across MC versions | Platform layer version adapters + smoke tests per supported version via MDL |
| Third-party mods overwrite handlers | Despotes calls handlers directly; conflicts surface as test failures, documented per-version |
| Untrusted LAN source reaches control port | Loopback-only bind + source allowlist + optional token |
| Frame capture stalls render thread | Bounded capture queue; readPixels is the only GL work; encode off-thread |
