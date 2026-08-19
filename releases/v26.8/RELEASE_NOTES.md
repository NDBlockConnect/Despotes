# Despotes v26.8 (Stable Release)

> In-process local control channel for Minecraft: Java Edition.

Stable release of the v26.8 line — WebSocket Transport and Real-time Streaming.

## What's new since v26.7

### WebSocket Transport

- **`WsTransport`** class: RFC 6455 WebSocket server on port 25586 (HTTP port + 1)
- Full handshake: SHA-1 accept, Sec-WebSocket-Key validation
- Text frame parsing: masked/unmasked, 7/16/64-bit payload lengths
- Commands routed through `SecurityGate` (same security model as HTTP/CLI)
- Responses sent as text frames
- Daemon threads with graceful shutdown
- Compatible with JDK 17+ (no virtual threads)

Enabled when `security.enabled` and `sourceEnabled("ws")`.

### Connection

```
ws://127.0.0.1:25586
```

Send JSON command objects as text frames, receive JSON responses as text frames.

## Verification (fabric-26.2, MC 26.2)

- WS port 25586: TcpTestSucceeded=True ✓
- Security gate validates peers ✓

## Assets (13 of 14; aprism pending)

---

**中文**

v26.8 正式版 — WebSocket 传输与实时流。新增 RFC 6455 WebSocket 服务器，端口 25586，支持双向 JSON 通信。
