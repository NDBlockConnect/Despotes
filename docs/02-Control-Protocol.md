# Despotes Control Protocol

> Doc 2 / 4 | Despotes Docs > Version: v26.0

All commands are JSON objects. The same schema is used by HTTP, CLI (stdin, one JSON per line) and FileDrop.

## 1. Envelope

```json
{
  "v": 1,
  "requestId": "optional-client-id",
  "type": "<command type>",
  ...type-specific fields
}
```

Response envelope (HTTP body / CLI stdout line):

```json
{ "requestId": "...", "ok": true,  "result": { ... } }
{ "requestId": "...", "ok": false, "error": { "code": "...", "message": "..." } }
```

Error codes: `BAD_REQUEST`, `UNKNOWN_TYPE`, `NOT_IN_GAME`, `NOT_ON_SCREEN`, `QUEUE_FULL`, `FORBIDDEN`, `TIMEOUT`, `INTERNAL`.

## 2. Action commands (`POST /despotes/v1/actions`)

### 2.1 `key` — press/release game keys

```json
{ "type": "key", "keys": ["key.keyboard.w", "key.mouse.left"],
  "op": "press" | "release" | "tap",
  "holdTicks": 20, "repeat": 1, "intervalTicks": 0 }
```

- `keys` use Minecraft key names (`key.keyboard.*`, `key.mouse.left/right/middle`).
- `tap` = press then release after `holdTicks` (default 1).
- Mouse keys act through the same click pipeline as `click` when a screen is open, otherwise map to attack/use.

### 2.2 `type` — text input

```json
{ "type": "type", "text": "hello", "target": "chat" | "focused" | "command",
  "submit": true, "perTickChars": 8 }
```

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->

- `chat`: opens chat (if needed), types, optionally submits.
- `focused`: appends to the currently focused edit box.
- `command`: shorthand for chat text starting with `/`.

### 2.3 `move` — movement state

```json
{ "type": "move", "forward": 1, "left": 0, "back": 0, "right": 0,
  "jump": false, "sneak": false, "sprint": false, "durationTicks": 40 }
```

Applies synthetic movement for `durationTicks` (default 1, `0` = until cancelled by another `move`).

### 2.4 `look` — camera rotation

```json
{ "type": "look", "mode": "delta" | "absolute",
  "yaw": 90.0, "pitch": 0.0, "smoothTicks": 4 }
```

Delta mode adds to current rotation. Pitch is clamped to [-89.9, 89.9].

### 2.5 `click` — GUI interaction

```json
{ "type": "click", "x": 120.5, "y": 60, "button": 0,
  "op": "press" | "release" | "click" | "double", "shift": false }
```

Coordinates are GUI-scaled coordinates (same space as vanilla GUIs). When no screen is open, `click` falls back to world interaction (see 2.6).

### 2.6 `use` — world interaction

```json
{ "type": "use", "what": "attack" | "useItem" | "placeBlock" | "drop" | "pickBlock",
  "target": { "x": 0, "y": 64, "z": 0, "face": "up" },   // block coords for place
  "hand": "main" | "off" }
```

Without `target`, `attack`/`useItem` act on whatever the crosshair is over.

### 2.7 `screenshot`

```json
{ "type": "screenshot", "format": "png" | "jpg", "quality": 0.8,
  "save": false, "path": null, "maxWidth": 0 }
```

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->

Returns `{ "width": w, "height": h, "format": "png", "base64": "..." }` or, with `save: true`, `{ "path": "<absolute path>" }`.

### 2.8 `mouse` — mouse capture control

```json
{ "type": "mouse", "op": "grab" | "release" | "status" }
```

- `release` frees the captured cursor back to the OS desktop — use this when the game keeps the mouse locked while you are working in another window.
- `grab` re-captures the cursor for first-person camera control (only takes effect in-world with no screen open).
- `status` only reports the current state.

Returns `{ "executed": "mouse", "op": "<op>", "captured": true|false }`.

Note: independent of this command, Despotes automatically releases the mouse whenever the game window loses OS focus (see `focus.releaseMouseOnFocusLoss` in doc 03) and re-captures it on focus regain.

## 3. Query commands (`POST /despotes/v1/query`)

| type | result fields |
|---|---|
| `status` | `{ inGame, screen, player: {name, x, y, z, yaw, pitch, health, dimension}, fps, windowFocused, mouseCaptured, loader, mcVersion, despotesVersion }` |
| `screen` | `{ open, title, widgets: [{id/class, x, y, w, h, focused, label}] }` (widget tree, bounded depth 4) |
| `inventory` | `{ hotbar, selectedSlot, slots: [{slot, item, count}] }` |
| `pending` | `{ queueSize, executing: [...] }` |

## 4. Control endpoints

| Method & path | Body | Purpose |
|---|---|---|
| `POST /despotes/v1/actions` | single command **or** `{"batch": [ ... ]}` | execute actions |
| `POST /despotes/v1/query` | single query | read state |
| `GET /despotes/v1/screenshot` | — | binary image (`image/png`) |
| `GET /despotes/v1/status` | — | JSON status |
| `POST /despotes/v1/cancel` | `{"requestId": "..."} \| {"all": true}` | cancel queued/held operations |
| `GET /despotes/v1/oplog?limit=50` | — | recent visualization log entries |

Batches execute in order, one per tick per command by default; `{"batch": [...], "parallel": true}` allows independent commands to share a tick.

### 4.1 Batch control steps (v26.12+)

Batch arrays can mix ordinary action/query objects with control steps. Steps run on the
transport worker between command responses; their nested actions still use the normal
client-thread dispatcher path.

```json
{
  "batch": [
    {"type": "look", "mode": "absolute", "yaw": 90, "pitch": 0},
    {"step": "wait", "ms": 500},
    {
      "step": "condition",
      "if": {"type": "status", "field": "result.inGame", "op": "exists"},
      "then": [{"type": "ping"}],
      "else": [{"type": "screenshot", "save": true}]
    },
    {
      "step": "retry",
      "command": {"type": "ping"},
      "attempts": 3,
      "intervalMs": 250
    }
  ]
}
```

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->

| Step | Fields | Behaviour |
|---|---|---|
| `wait` | `ms` (0-30000) | Pauses batch execution for the requested wall-clock time. |
| `condition` | `if`, `then`, `else` | Evaluates the same query-field predicates as the `condition` action (`exists`, `eq`, `ne`, `gt`, `lt`, `contains`) then runs the selected branch inline. `value` belongs inside `if`. |
| `retry` | `command`, `attempts` (1-10), `intervalMs` | Repeats a command until it returns `ok:true` or attempts are exhausted. |

Errors may include optional structured `error.details` fields in addition to the stable
`error.code` and `error.message` envelope.

## 5. CLI (stdin)

Same JSON, one line each; responses are single JSON lines on stdout prefixed by nothing. Example session:

```
{"type":"status"}
{"ok":true,"result":{"inGame":true,...}}
{"type":"key","keys":["key.keyboard.w"],"op":"tap","holdTicks":20}
{"ok":true,"result":{"executed":"key"}}
```

## 6. FileDrop

Drop `<name>.json` (single command or batch) into `despotes-in/`. Each tick Despotes consumes up to `fileDrop.maxPerTick` files in filename order, executes them, and writes `<name>.result.json` next to them (files are not deleted; set `fileDrop.deleteAfter: true` to delete).
