# Despotes v26.11-Alpha.1 — Redstone Circuit Completion & Hardening

**English**

Completes the v26.9 redstone theme (circuit topology query + component interactions) and hardens the 26.1.2 fabric line.

## New query

### `circuit` — redstone circuit component scan

```json
{"type":"circuit","x":-516,"y":71,"z":-87,"radius":3}
```

Walks a cube (radius 1-8, default 4) around the centre and returns every circuit component — redstone wire, torch, block, lamp, repeater, comparator, lever, button, pressure plate, observer, piston, dispenser, dropper, hopper, note block, daylight detector, target, sculk sensors — each with position, `powered` state and interesting properties (`delay` for repeaters, `note` for note blocks, `facing`, `locked`). Falls back to the crosshair target block when no coordinates are given. Verified: 343-block scan found wire + note block with `note:0`.

## New action

### `redstone-action` — component interaction

```json
{"type":"redstone-action","op":"toggle","x":-517,"y":71,"z":-87,"face":"up"}
{"type":"redstone-action","op":"cycle","x":-515,"y":71,"z":-87,"face":"up","count":3}
```

`toggle` = single right-click (lever/button/door); `cycle` = N right-clicks spaced 2 ticks apart (repeater delay 1-4, note-block pitch 0-24, comparator mode). Uses the standard useItemOn pipeline — works across all loader lines. Verified: lever toggle → `powered:true`; note-block cycle ×3 → `note:3` read back via circuit query.

## fabric-26.1.2 hardening

- **Registration-order fix**: message events are now registered before the HUD hook, and HUD registration is defensive (failure logs and disables overlay instead of silently killing later registrations).
- **Event-stream on 26.1.2 remains limited**: root cause confirmed — fabric-api 0.155.2's message-api ships **server-side mixins only** (MinecraftServerMixin/PlayerListMixin; no client hook), so `ClientReceiveMessageEvents` never fire on this line. A packet-level `MessageCaptureMixin` (handleSystemChat/handlePlayerChat/handleDisguisedChat) was added but does not activate under fabric loader 0.19.2 on this version — under investigation with mixin debug counters for v26.12. Chat *sending* works; only inbound capture is affected.

## Verification

- fabric-26.2 (MC 26.2): circuit scan (343 blocks, wire+note_block found, note:0), redstone-action cycle (note 0→3 read back), toggle (lever powered:true, facing:north) — all pass.
- openlumin-fabric-26.1.2 (MC 26.1.2): boot, world entry, chat action — pass; event-stream limitation documented above.
- All **20/20 artifacts** built green across native/fabric/neoforge/forge/aprism.

## Assets (20)

fabric ×6 (26.2 / 26.1.2 / 1.21.10 / 1.21.4 / 1.21.1 / 1.20.1) · native ×6 (same versions) · neoforge ×5 (26.2 / 26.1.2 / 1.21.10 / 1.21.4 / 1.21.1) · forge ×2 (1.21.1 / 1.20.1) · aprism ×1 (26.2)

---

**中文**

完成 v26.9 红石主题（电路拓扑查询 + 元件交互），并加固 26.1.2 fabric 线。

## 新查询

### `circuit` — 红石电路元件扫描
立方体扫描（半径 1-8，默认 4）返回全部电路元件（线/火把/中继器/比较器/拉杆/按钮/观察器/活塞/音符盒等），含位置、powered 状态与关键属性（delay/note/facing/locked）。无坐标时回退准星目标。实测：343 方块扫描发现 wire + note_block（note:0）。

## 新动作

### `redstone-action` — 元件交互
`toggle` = 单次右键（拉杆/按钮/门）；`cycle` = N 次右键间隔 2 tick（中继器延迟、音符盒音高、比较器模式）。走标准 useItemOn 管线，全加载器可用。实测：拉杆 toggle → powered:true；音符盒 cycle ×3 → note:3 回读确认。

## fabric-26.1.2 加固

- 注册顺序修复：消息事件先于 HUD 注册；HUD 注册防御式（失败仅禁用覆盖层）
- **26.1.2 事件流仍受限**：根因确认——fabric-api 0.155.2 的 message-api 仅含服务端 mixin（无客户端钩子），ClientReceiveMessageEvents 在该线永不触发。已加包级 MessageCaptureMixin 但在 fabric loader 0.19.2 上未激活（v26.12 用 mixin debug counters 继续排查）。聊天发送正常，仅入站捕获受影响。

## 验证

- fabric-26.2：circuit 扫描、cycle（note 0→3 回读）、toggle（powered:true）全通过
- openlumin-fabric-26.1.2：boot、进世界、chat 动作通过；事件流限制如上
- **20/20 构件**构建绿
