# Despotes FACT — Project State and Roadmap

> Last updated: 2026-08-16 | Current release: v26.2 stable

## Current State

### Released versions
- **v26.0** — five loader lines, input injection, screenshot, overlay, oplog, HTTP/CLI/FileDrop
- **v26.1** — API Key auth, AI intent translation, AI assistant, world event stream, chat action, container/hotbar
- **v26.2** — death awareness, perception (self/threats/damage), ASM instrumentation, JavaAgent-for-all (loader mixing), Aprism adaptation, latency telemetry, UX (ping/lookat/long-poll), loader parity
- **v26.3** — inventory action, recipe query, craft, interact, trade, sort
- **v26.4** — multiplayer perception (players/server/tablist/scoreboard/coords/whisper)
- **v26.5** — pathfinding & navigation (goto/follow/stop-nav)
- **v26.6** — combat system (attack-entity/combat/retreat/shield)
- **v26.7** — building ops (place-block/dig/fill)
- **v26.8** — WebSocket transport (RFC 6455, port 25586)
- **v26.9** — redstone query + schedule/macro/condition automation primitives
- **v26.10-Alpha.1** — version support expansion: +26.1.2 and +1.21.10 lines (18/20 artifacts; neoforge new lines pending NeoForm build)

### Version scheme
Per BC skill spec: `v{Year}.{Major}-Alpha{N}` — Major range 0-∞, valid through 2026-12-31 for v26. Each major: Alpha.1-9 (Pre-Release), then bare version (Release).

### Branch model
- `main` — docs/spec only
- `native` — primary dev line (premain javaagent + Mixin + ASM)
- `fabric` / `neoforge` / `forge` / `aprism` — loader branches, ff-merged from native

### Build matrix (20 artifacts planned; 18 in v26.10-Alpha.1)
| Loader | MC versions | JDK |
|---|---|---|
| native | 1.20.1, 1.21.1, 1.21.4, **1.21.10**, **26.1.2**, 26.2 | 25 (26.x), 21 (1.21.x), 17 (1.20.x) |
| fabric | 1.20.1, 1.21.1, 1.21.4, **1.21.10**, **26.1.2**, 26.2 | same |
| neoforge | 1.21.1, 1.21.4, 26.2 (+ 1.21.10 / 26.1.2 pending NeoForm) | same |
| forge | 1.20.1, 1.21.1 | same |
| aprism | 26.2 | 25 |

### Implemented actions
key, type, chat, move, look (delta/absolute/lookat), function, click, use (attack/useItem/placeBlock/drop/pickBlock), mouse, screenshot, status, screen, inventory, self, threats, world, blocks, entities, target, container, hotbar, respawn, ping, ai, pending, config-reload

### Implemented queries
status (incl. lifecycle/latency), screen (incl. kind:death), inventory, self (30+ vitals), threats (hostile+projectile, targetingYou), world (biome/time/difficulty/isDay), blocks, entities, target, container, pending

### Event stream (GET /events?since=N and wait=MS)
chat, system, overlay, death, respawn, world_joined, world_left, damage

### MDL test instances
- despotes-neo-26.2 (neoforge 26.2.0.51-beta, MC 26.2)
- despotes-neo-1214 (neoforge 21.4.157, MC 1.21.4)
- despotes-neo-1211 (neoforge 21.1.248, MC 1.21.1)
- despotes-fab-1201 (fabric, MC 1.20.1)
- despotes-fab-1211 (fabric, MC 1.21.1)
- despotes-fab-1214 (fabric, MC 1.21.4)
- despotes-test-26.2 (fabric 0.19.3, MC 26.2)
- despotes-forge-1201 (forge 47.1.3, MC 1.20.1)
- despotes-forge-1211 (forge, MC 1.21.1)
- despotes-nat-262 (vanilla, MC 26.2) — for javaagent
- despotes-nat-1214 (vanilla, MC 1.21.4)
- despotes-nat-1211 (vanilla, MC 1.21.1)
- apr-test (aprism, MC 26.2)

### MDL launch with agent
```
mdl launch <instance> --agent --agent-port 25585 --detach
mdl game status <instance>
```

### Known issues
- Root gradle.properties still at v26.0-Alpha.2 (cosmetic; subprojects override)
- JDK path in root gradle.properties points to old workspace dir (subprojects carry their own)
- v26.2 14-artifact full runtime verification never completed
- MDL instances have older mod versions installed
- neoforge-26.1.2 / neoforge-1.21.10: NeoForm first-pass decompile+recompile repeatedly killed by memory pressure on 15.7GB host; source identical to verified neoforge-26.2/1.21.4 — retry on a less loaded machine, then attach artifacts and cut stable v26.10
  - neoforge-1.21.10 additionally needs the Vineflower decompile-failure patch hook (added in its build.gradle: neoFormTransformSource doLast rewrites the illegal `$VF: Couldn't be decompiled` lambda in EntitySectionStorage.java:120); recompile passes after patch. Legacy NeoForge 21.1.248 jars must NOT be on its compile classpath (they shadow 21.10 classes).
  - neoforge-26.1.2 BUILD SUCCESSFUL (artifact in releases/v26.10-Alpha.1/, 161KB)
- fabric-26.1.2 line: event-stream capture and /give feedback silent (fabric-api 0.155.2 behavior); chat action itself submits fine
  - v26.12 diagnosis: fabric-api 0.155.2 message-api has NO client-side mixins (server-only: MinecraftServerMixin/PlayerListMixin) — ClientReceiveMessageEvents can never fire. A packet-level MessageCaptureMixin (handleSystemChat/handlePlayerChat/handleDisguisedChat, with ctor-injection diagnostics) compiles and ships but its class never loads under fabric loader 0.19.2 on 26.1.2 — the mixin config is silently not applied. Static-block and ctor-inject logs never appear; no mixin errors in log. Next lever: run with JVM mixin debug flags via a custom launch script (mdl has no JVM-arg passthrough), or check whether fabric loader 0.19.2 requires a refmap/`minVersion` in mixins.json for non-remapped environments. Chat sending unaffected; 26.2+ lines unaffected.

### RESOLVED — v26.1-native + Aprism dual-agent crash (reported 2026-08-23)
- Symptom: Despotes-v26.1-native-26.2.jar attached alongside Aprism agent on MC 26.2 → game silently dies ~2 s after title screen (no crash report, no hs_err, log truncates after texture-atlas creation). Reproduced 3x by reporting agent; isolation confirmed Despotes as the killer (Aprism-only run survives).
- Root cause: v26.1 native predates the v26.2-Alpha.4 loader-mixing guard. Its transformer weaves hooks into game classes defined by Aprism's classloader; woven callbacks then reference agent classes invisible from that loader → hard crash inside the game loop. (v26.2-Alpha.4 release notes documented this exact historical failure mode.)
- Fix verification (2026-08-23): Vanilla 26.2 instance, Aprism v26.6 agent + Despotes v26.10-Alpha.1 native co-attached → process alive 4+ min past title screen, boot OK, HTTP 25585 responds `v26.10-Alpha.1 loader=native` (companion/pump mode). v26.10 clean.
- Disposition: v26.1-native-26.2.jar must NOT be used with Aprism (or any loader) co-attached — superseded by v26.2+ native artifacts which carry the guard. Reporting agent should register Despotes-v26.10-Alpha.1-native-26.2.jar (or later) for the JEI in-world activation verification.

---

## Roadmap (v26.3 to v27.2, 10 minor versions)

### v26.3 — Interaction: Inventory and World Interaction
- A1: inventory action (moveSlot, drop stack, split)
- A2: equip action (armor equip/unequip)
- A3: recipe query (recipe book)
- A4: craft action (crafting table + result extraction)
- A5: interact action (right-click block/entity: doors, levers, villagers)
- A6: trade query+action (villager offers, execute trade)
- A7: drop enhancement (specific count)
- A8: sort action (inventory auto-sort)
- A9: RC full regression

### v26.4 — Multiplayer Server Perception
- A1: players query (nearby players, name/UUID/distance/health/gear)
- A2: server query (MOTD, TPS estimate, online count, ping)
- A3: tablist query (tab list with latency)
- A4: scoreboard query (teams, scores, objectives)
- A5: player join/leave/death events
- A6: whisper action (/msg + private message events)
- A7: coords query (spawn, world border, key locations)
- A8: AFK guard (auto-respond config.afkGuard)
- A9: RC full regression

### v26.5 — Pathfinding and Navigation
- A1-A9: A* pathfinding, follow entity, avoid threats, walkable/climbable/hazard tags, jump/fall/swim, goto composite, nav events, waypoints

### v26.6 — Combat System
- A1-A9: Attack entity by UUID, combat query, auto-combat, retreat, weapon switch, shield, combat events, PvP detection

### v26.7 — Building and Block Operations
- A1-A9: Precise place, blueprint build, schematic loading, fill, mine, materials query, build events, undo

### v26.8 — WebSocket Transport and Real-time Streaming
- MUST complete 1.20.x-26.x version support expansion before this
- A1-A9: WS transport, event push, screen stream, status push, multi-client, reconnect, config, HTTP compat

### v26.9 — Redstone and Automation (v26 line finale)
- A1-A9: Redstone query/action, repeater/notepad, circuit topology, schedule cron, macro record/replay, condition rules

### Version Support Expansion (before v26.8)
Expand MC version coverage: add 1.20.x (beyond 1.20.1), 1.21.x intermediate versions, 26.1.x where feasible. Target: support 1.20.x through 26.x across all loaders.

### v27.0 — Cross-version Major Upgrade
- A1-A9: MC 27.x adapt, protocol v2, batch control flow, loader ecosystem, Aprism 27.x, backward compat, unified stream, perf baseline

### v27.1 — Multi-task and State Management
- A1-A9: Named tasks, scheduler, tasks query, persistence, task events, sequence action, interrupt handling, config

### v27.2 — Plugin System and Extension Ecosystem
- A1-A9: Plugin SPI, JAR loading, custom actions/events, plugin config, lifecycle events, API docs, MCP bridge
