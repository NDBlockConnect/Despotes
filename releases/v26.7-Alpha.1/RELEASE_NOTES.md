# Despotes v26.7-Alpha.1 — Building and WebSocket / 建筑与 WebSocket

**English**

Adds building/block operations and WebSocket transport in one release.

## v26.7: Building and Block Operations

### `place-block` — place a block at coordinates
```json
{"type":"place-block","x":10,"y":64,"z":20}
```
Looks at target, uses item to place block (requires block in hotbar).

### `dig` — break a block at coordinates
```json
{"type":"dig","x":10,"y":64,"z":20}
```
Looks at target, attacks to start breaking.

### `fill` — fill a cuboid region
```json
{"type":"fill","x1":0,"y1":64,"z1":0,"x2":5,"y2":64,"z2":5,"block":"minecraft:stone"}
```
Uses `/fill` command for efficient bulk placement.

## v26.8: WebSocket Transport

### `WsTransport` — RFC 6455 WebSocket server

- Listens on port 25586 (HTTP port + 1)
- Full WebSocket handshake (SHA-1 accept, Sec-WebSocket-Key)
- Text frame parsing (masked/unmasked, 7/16/64-bit lengths)
- Commands routed through `SecurityGate` (same security as HTTP/CLI)
- Response sent back as text frame
- Daemon threads, graceful shutdown

Enabled when `security.enabled` and `sourceEnabled("ws")`.

## Verification (fabric-26.2, MC 26.2)

- fill: correctly rejected with NOT_IN_GAME when in menu ✓
- WS port 25586: TcpTestSucceeded=True ✓

## Assets (13 of 14; aprism pending)

---

**中文**

v26.7-Alpha.1 — 新增建筑操作（place-block/dig/fill）和 WebSocket 传输（端口 25586）。
