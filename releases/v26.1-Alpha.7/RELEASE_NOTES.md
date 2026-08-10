# Despotes v26.1-Alpha.7 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition.

## Highlights — AI feature layer (depends on Alpha.6 API Key)

1. **AI intent translation** — `POST /actions {"type":"ai","intent":"..."}` sends the intent plus
   live world/player state to a configurable OpenAI-compatible `chat/completions` endpoint
   (`ai.*` config), then executes the returned JSON action plan restricted to a whitelist
   (`key/move/look/click/use/function/hotbar`, capped by `ai.maxActions`).
2. **Config** — `ai.enabled`, `ai.endpoint`, `ai.model`, `ai.apiKey` (Bearer), `ai.timeoutMs`,
   `ai.maxActions`. Disabled by default; graceful error when unset/unreachable.
3. Verified on Fabric 26.2 with a local mock LLM: intent → plan → executed actions
   (`move` + `look` results returned); disabled path → `FORBIDDEN`.
4. **Fourteen artifacts**.

## Verification (tested before release)

- Fabric 26.2 runtime: AI end-to-end (mock LLM), disabled error path; regression of prior
  features.
- Other loaders rebuild-only.

## Assets (14)

- `Despotes-v26.1-Alpha.7-fabric-26.2.jar`
- `Despotes-v26.1-Alpha.7-fabric-1.21.4.jar`
- `Despotes-v26.1-Alpha.7-fabric-1.21.1.jar`
- `Despotes-v26.1-Alpha.7-fabric-1.20.1.jar`
- `Despotes-v26.1-Alpha.7-neoforge-26.2.jar`
- `Despotes-v26.1-Alpha.7-neoforge-1.21.4.jar`
- `Despotes-v26.1-Alpha.7-neoforge-1.21.1.jar`
- `Despotes-v26.1-Alpha.7-forge-1.21.1.jar`
- `Despotes-v26.1-Alpha.7-forge-1.20.1.jar`
- `Despotes-v26.1-Alpha.7-native-26.2.jar`
- `Despotes-v26.1-Alpha.7-native-1.21.4.jar`
- `Despotes-v26.1-Alpha.7-native-1.21.1.jar`
- `Despotes-v26.1-Alpha.7-native-1.20.1.jar`
- `Despotes-v26.1-Alpha.7-aprism-26.2.aje`

---

# Despotes v26.1-Alpha.7（预发布）中文说明

- **AI 意图转译**：`ai` 动作把意图+实时世界/玩家状态发给可配置 OpenAI 兼容端点，执行返回的
  JSON 动作计划（白名单 key/move/look/click/use/function/hotbar，受 `ai.maxActions` 限制）。
- 配置：`ai.enabled/endpoint/model/apiKey/timeoutMs/maxActions`，默认禁用；未配置/不可达
  优雅报错。
- 实测（Fabric 26.2 + 本地 mock LLM）：意图→计划→执行（move/look 结果返回）；禁用→FORBIDDEN。
- 本周期十四个构件。
