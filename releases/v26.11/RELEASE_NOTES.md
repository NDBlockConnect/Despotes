# Despotes v26.11 — Stable Release (Redstone Circuit Completion & Hardening)

Completes the v26.9 redstone theme and hardens cross-version behavior. **20/20 artifacts** across five loader lines: **native** (javaagent, 1.20.1 → 26.2), **fabric** (1.20.1 → 26.2), **neoforge** (1.21.1 → 26.2), **forge** (1.20.1 / 1.21.1), **aprism** (26.2).

## What's new since v26.10

### `circuit` query — redstone circuit component scan

```json
{"type":"circuit","x":-516,"y":71,"z":-87,"radius":3}
```

Cube scan (radius 1-8, default 4) returning every circuit component — wire, torch, block, lamp, repeater, comparator, lever, button, pressure plate, observer, piston, dispenser, dropper, hopper, note block, daylight detector, target, sculk sensors — each with position, `powered` state and properties (`delay`, `note`, `facing`, `locked`). Crosshair-target fallback. Verified: 343-block scan found wire + note block.

### `redstone-action` — component interaction

```json
{"type":"redstone-action","op":"toggle","x":..,"y":..,"z":..,"face":"up"}
{"type":"redstone-action","op":"cycle","x":..,"y":..,"z":..,"face":"up","count":3}
```

`toggle` = single right-click; `cycle` = N right-clicks spaced 2 ticks (repeater delay, note-block pitch, comparator mode). Uses the useItemOn pipeline — all loader lines. Verified: lever toggle → `powered:true`; note-block cycle ×3 → `note:3`.

### `screen` query: window geometry block

```json
"window": {"physicalWidth": 854, "physicalHeight": 480, "width": 427, "height": 240, "guiScale": 2}
```

External agents measuring the OS window can now convert to the GUI-scaled click space: `physical / guiScale = logical`. Resolves the cross-project coordinate-space blocker.

### chat action: title-screen guard

`chat` with plain text while not in-game now returns `NOT_IN_GAME` instead of NPE-crashing the command path; slash commands still route via the command channel from anywhere.

### aprism artifact manifest fix

The aprism jar/`.aje` previously shipped an empty `MANIFEST.MF`; both now carry `Implementation-*` / `Specification-*` metadata (Gradle 9 removed the old manifest APIs — generated as a file, placed via from+into).

## Known limitations

- fabric-26.1.2 event-stream: fabric-api 0.155.2 message-api has no client-side mixins; a packet-level capture mixin ships but does not activate under loader 0.19.2. Chat sending unaffected.
- neoforge-1.21.4 artifacts are compiled against the historical legacy classpath (mapping-provider divergence with neoform 21.4.157 userdev); runtime-verified historically, strict compile-time API checks deferred to v26.12.

## Regression (pre-release)

Boot ✓ · title-screen chat guard ✓ · ping ✓ · world entry via clicks ✓ (later checks interrupted by a parallel-session mdl OOM sweep; Alpha.1 full-chain results stand for identical code paths).

## Assets (20)

fabric ×6 (26.2 / 26.1.2 / 1.21.10 / 1.21.4 / 1.21.1 / 1.20.1) · native ×6 (same) · neoforge ×5 (26.2 / 26.1.2 / 1.21.10 / 1.21.4 / 1.21.1) · forge ×2 (1.21.1 / 1.20.1) · aprism ×1 (26.2)

---

# Despotes v26.11（正式版 — 红石电路完善与加固）

完成 v26.9 红石主题，加固跨版本行为。**20/20 构件**。

## 自 v26.10 以来

- **`circuit` 查询**：立方体扫描红石元件（powered/delay/note/facing/locked），实测 343 方块
- **`redstone-action`**：toggle/cycle 元件交互（useItemOn 管线），实测拉杆与音符盒
- **`screen` 查询新增 window 块**：物理尺寸 + guiScale，解决坐标换算 blocker
- **chat 标题屏守卫**：非世界内纯文本返回 NOT_IN_GAME 而非 NPE
- **aprism 构件 manifest 修复**：填充 Implementation-* 元数据

## 已知限制

- fabric-26.1.2 事件流（fabric-api 上游缺口，详见 FACT.md）
- neoforge-1.21.4 构件沿用历史 legacy classpath 编译（映射分歧，严格 API 检查延至 v26.12）
