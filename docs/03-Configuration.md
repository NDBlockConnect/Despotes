# Despotes Configuration

> Doc 3 / 4 | Despotes Docs > Version: v26.0

Despotes reads **`despotes.json`** from the game directory (the directory that contains `options.txt`). It is generated with defaults on first launch and reloaded on change (polled every 5 s; also re-read via `POST /despotes/v1/config/reload`).

## 1. Full reference (defaults)

```json
{
  "schemaVersion": 1,
  "control": {
    "enabled": true,
    "maxActionsPerTick": 4,
    "queueCapacity": 1024
  },
  "http": {
    "enabled": true,
    "host": "127.0.0.1",
    "port": 25585,
    "maxWorkers": 8,
    "screenshotTimeoutMs": 5000,
    "oplogLimit": 200
  },
  "cli": {
    "enabled": true,
    "echo": true
  },
  "fileDrop": {
    "enabled": false,
    "dir": "despotes-in",
    "maxPerTick": 2,
    "deleteAfter": false
  },
  "sources": [
    { "id": "local-http", "transport": "http", "enabled": true },
    { "id": "local-stdin", "transport": "cli", "enabled": true },
    { "id": "local-filedrop", "transport": "filedrop", "enabled": false }
  ],
  "security": {
    "enabled": true,
    "requireToken": false,
    "token": "",
    "allowSources": []
  },
  "capture": {
    "dir": "despotes-shots",
    "format": "png",
    "jpgQuality": 0.8,
    "maxWidth": 0
  },
  "visualization": {
    "overlay": true,
    "overlayLines": 8,
    "toggleKey": "key.keyboard.f8",
    "opLog": true,
    "opLogFile": "despotes-oplog.jsonl"
  },
  "movement": {
    "defaultLookSmoothTicks": 4
  }
}
```

## 2. External control sources

`sources` is the list of configured control sources. Built-in transports: `http`, `cli`, `filedrop`. A source is active only if **both** its `enabled` flag and its transport's section are enabled.

Plugin sources (v26.1+) add entries with `"transport": "plugin"` and a `"class"` field naming a `ControlSourceFactory` implementation on the classpath:

```json
{ "id": "my-ws", "transport": "plugin", "class": "com.example.WsSourceFactory",
  "config": { "...transport specific..." }, "enabled": true }
```

## 3. Security notes

- `security.allowSources` is a list of additional peer addresses allowed to issue commands (e.g. `"192.168.1.50"`). Loopback is always allowed.
- When `security.requireToken` is true, every request must carry `X-Despotes-Token: <token>` (HTTP header / CLI field `"token"`).
- Changing `http.port` takes effect after a restart or config reload.

## 4. Precedence

1. `security.enabled=false` overrides everything except CLI.
2. Transport sections override individual source entries.
3. Command-line system property `-Ddespotes.port=NNNN` overrides `http.port` (for debugging).
