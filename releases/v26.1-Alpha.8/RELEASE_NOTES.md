# Despotes v26.1-Alpha.8 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition.

## Highlights — AI assistant (conversational control)

1. **Conversational assistant endpoint** — `POST /despotes/v1/assistant` with
   `{"message":"..."}`. Despotes sends the message plus live world/player state to the
   configured LLM (`ai.*` config) and executes the returned `actions` array (whitelisted),
   returning `{"reply":"...", "results":[...]}`.
2. **Verified** on Fabric 26.2 with a local mock LLM: reply text returned and the look action
   executed; `ai` disabled path → `FORBIDDEN`.
3. **Fourteen artifacts**.

## Verification (tested before release)

- Fabric 26.2 runtime: assistant end-to-end (reply + executed look), ai disabled error path.
- Regression of prior features (Alpha.1–Alpha.7).
- Other loaders rebuild-only.

## Assets (14)

- `Despotes-v26.1-Alpha.8-fabric-26.2.jar`
- `Despotes-v26.1-Alpha.8-fabric-1.21.4.jar`
- `Despotes-v26.1-Alpha.8-fabric-1.21.1.jar`
- `Despotes-v26.1-Alpha.8-fabric-1.20.1.jar`
- `Despotes-v26.1-Alpha.8-neoforge-26.2.jar`
- `Despotes-v26.1-Alpha.8-neoforge-1.21.4.jar`
- `Despotes-v26.1-Alpha.8-neoforge-1.21.1.jar`
- `Despotes-v26.1-Alpha.8-forge-1.21.1.jar`
- `Despotes-v26.1-Alpha.8-forge-1.20.1.jar`
- `Despotes-v26.1-Alpha.8-native-26.2.jar`
- `Despotes-v26.1-Alpha.8-native-1.21.4.jar`
- `Despotes-v26.1-Alpha.8-native-1.21.1.jar`
- `Despotes-v26.1-Alpha.8-native-1.20.1.jar`
- `Despotes-v26.1-Alpha.8-aprism-26.2.aje`

---

# Despotes v26.1-Alpha.8（预发布）中文说明

- **对话式 AI 助手**：`POST /despotes/v1/assistant`（`{"message":"..."}`）把消息+实时世界/玩家
  状态发给配置的 LLM，执行返回的 `actions`（白名单），返回 `{reply, results}`。
- 实测（Fabric 26.2 + 本地 mock LLM）：返回 reply 并执行 look；禁用→FORBIDDEN。
- 本周期十四个构件；Alpha.1–Alpha.7 功能回归。
