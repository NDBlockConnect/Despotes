# Despotes v26.1-Alpha.9 (Pre-Release / Release Candidate)

> In-process local control channel for Minecraft: Java Edition.

## Highlights — world event stream + chat sending

1. **Event bus & `/despotes/v1/events`** — Despotes now captures inbound game messages
   (player chat, system messages, action-bar overlay) into an in-process event bus.
   Callers poll `GET /despotes/v1/events` to read `{"lastSeq":N,"events":[{seq,type,timestampMs,payload}]}`
   so external controllers can observe what the game says without pixel-scraping the chat.
2. **`chat` action** — `POST /despotes/v1/actions {"type":"chat","text":"..."}` opens the chat
   screen, types the message and submits it (`/` prefixes work as commands). Returns
   `{"executed":"chat","via":"chat_screen","chars":N,"submitted":true}`.
3. **Verified** on Fabric 26.2: chat action round-trips (sent message reappears as a captured
   chat event), `/events` returns the captured chat; look/move respect the un-focused design.
4. **Fourteen artifacts.**

## Verification (tested before release)

- Fabric 26.2 runtime: `chat` action (submitted=true), `/events` captured the round-tripped
  chat message, look/move applied while un-focused (design semantics).
- Regression of prior features (Alpha.1–Alpha.8).
- Other loaders rebuild-only.

## Assets (14)

- `Despotes-v26.1-Alpha.9-fabric-26.2.jar`
- `Despotes-v26.1-Alpha.9-fabric-1.21.4.jar`
- `Despotes-v26.1-Alpha.9-fabric-1.21.1.jar`
- `Despotes-v26.1-Alpha.9-fabric-1.20.1.jar`
- `Despotes-v26.1-Alpha.9-neoforge-26.2.jar`
- `Despotes-v26.1-Alpha.9-neoforge-1.21.4.jar`
- `Despotes-v26.1-Alpha.9-neoforge-1.21.1.jar`
- `Despotes-v26.1-Alpha.9-forge-1.21.1.jar`
- `Despotes-v26.1-Alpha.9-forge-1.20.1.jar`
- `Despotes-v26.1-Alpha.9-native-26.2.jar`
- `Despotes-v26.1-Alpha.9-native-1.21.4.jar`
- `Despotes-v26.1-Alpha.9-native-1.21.1.jar`
- `Despotes-v26.1-Alpha.9-native-1.20.1.jar`
- `Despotes-v26.1-Alpha.9-aprism-26.2.aje`

---

# Despotes v26.1-Alpha.9（预发布／发布候选）中文说明

- **事件总线与 `/despotes/v1/events`**：Despotes 现会把游戏内消息（玩家聊天、系统消息、
  动作栏 overlay）采集到进程内事件总线；外部控制方轮询该端点即可读取
  `{lastSeq, events:[{seq,type,timestampMs,payload}]}`，无需像素级读取聊天。
- **`chat` 动作**：`POST /despotes/v1/actions {"type":"chat","text":"..."}` 会打开聊天框、
  输入并提交（`/` 前缀可作为命令执行），返回 `{executed,via,chars,submitted}`。
- 实测（Fabric 26.2）：chat 动作往返成功（发出的消息被事件流捕获），`/events` 正常；
  失焦时 look/move 按设计应用。
- 本周期十四个构件；Alpha.1–Alpha.8 功能回归。
