# Despotes v26.4-Alpha.1 — Multiplayer Server Perception / 多人服务器感知

**English**

Adds multiplayer server perception: players, server info, tablist, scoreboard, coords, whisper.

## New queries

### `players` — nearby players
```json
{"type":"players","radius":64}
```
Returns name/UUID/position/distance/health/armor/state for nearby players.

### `server` — server info
```json
{"type":"server"}
```
Returns MOTD, IP, ping, online player count.

### `tablist` — player list with latency
```json
{"type":"tablist"}
```
Returns player names, latency (ms), gamemode from the tab list.

### `scoreboard` — teams and objectives
```json
{"type":"scoreboard"}
```
Returns team names/displayNames/colors and objective names/displayNames/criteria.

### `coords` — spawn and world border
```json
{"type":"coords"}
```
Returns spawn point, world border center/size/damage, player position.

## New action

### `whisper` — private message
```json
{"type":"whisper","target":"PlayerName","message":"hello"}
```
Sends `/msg <target> <message>`.

## Verification (fabric-26.2, MC 26.2, singleplayer)

- players: count=0 (no other players nearby) ✓
- server: connected=true ✓
- coords: query executed ✓
- tablist: count=1 (self) ✓
- scoreboard: teams=0 (no teams) ✓

## Assets (12 of 13; forge-1.21.1 pending)

---

**中文**

新增多人服务器感知功能：玩家查询、服务器信息、Tab列表、计分板、坐标查询、私聊。
