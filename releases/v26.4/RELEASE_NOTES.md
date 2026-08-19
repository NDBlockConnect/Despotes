# Despotes v26.4 (Stable Release)

> In-process local control channel for Minecraft: Java Edition.

Stable release of the v26.4 line — Multiplayer Server Perception.

## What's new since v26.3

### Multiplayer Server Perception

- **`players`** query: nearby players (name/UUID/distance/health/armor/state)
- **`server`** query: MOTD, IP, ping, online count
- **`tablist`** query: player list with latency and gamemode
- **`scoreboard`** query: teams and objectives
- **`coords`** query: spawn point, world border, player position
- **`whisper`** action: private message via `/msg`

## Verification (fabric-26.2, MC 26.2, singleplayer)

- players: count=0 ✓, server: connected=true ✓, tablist: count=1 ✓, scoreboard: teams=0 ✓, coords: executed ✓

## Assets (12 of 13; forge-1.21.1 pending)

---

**中文**

v26.4 正式版 — 多人服务器感知。新增玩家、服务器、Tab列表、计分板、坐标查询及私聊功能。
