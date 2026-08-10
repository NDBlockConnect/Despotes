# Despotes v26.1 (Stable Release)

> In-process local control channel for Minecraft: Java Edition.

This is the stable release of the v26.1 line, following Alpha.1–Alpha.9 (release candidate).

## What's new since v26.0

1. **Alpha.1 — look & focus fixes**: look actually applies (smooth frame-driven easing),
   function keys (F5 perspective, F3 debug, etc.) via semantic `function` action,
   startup no longer forces window focus (`window.grabFocusOnStart`, default off).
2. **Alpha.2 — focus-steal guard**: the game no longer steals OS focus when the user
   switches away; manual camera input is respected while focused.
3. **Alpha.3 — version matrix extended**: fabric 1.20.1/1.21.1/1.21.4/26.2, neoforge, forge,
   native (javaagent) and Aprism lines.
4. **Alpha.4 — extended loader matrix**: neoforge 1.21.4 runtime-verified, native 1.21.4.
5. **Alpha.5 — container & hotbar**: `container` query and `hotbar` action.
6. **Alpha.6 — API key auth**: `security.apiKeys` + `X-Despotes-Key` header.
7. **Alpha.7 — AI intent translation**: `ai` action backed by an OpenAI-compatible LLM.
8. **Alpha.8 — AI assistant**: conversational `POST /despotes/v1/assistant`.
9. **Alpha.9 — world event stream**: `/despotes/v1/events` + `chat` action.

## Verification (tested before release)

- Fabric 26.2 runtime (this cycle): `/events` endpoint, `chat` action round-trip,
  look/move applied while un-focused, focus-steal guard active, hotbar/inventory/container.
- Prior alphas verified their respective features on the corresponding loaders.

## Assets (14)

- `Despotes-v26.1-fabric-26.2.jar`
- `Despotes-v26.1-fabric-1.21.4.jar`
- `Despotes-v26.1-fabric-1.21.1.jar`
- `Despotes-v26.1-fabric-1.20.1.jar`
- `Despotes-v26.1-neoforge-26.2.jar`
- `Despotes-v26.1-neoforge-1.21.4.jar`
- `Despotes-v26.1-neoforge-1.21.1.jar`
- `Despotes-v26.1-forge-1.21.1.jar`
- `Despotes-v26.1-forge-1.20.1.jar`
- `Despotes-v26.1-native-26.2.jar`
- `Despotes-v26.1-native-1.21.4.jar`
- `Despotes-v26.1-native-1.21.1.jar`
- `Despotes-v26.1-native-1.20.1.jar`
- `Despotes-v26.1-aprism-26.2.aje`

---

# Despotes v26.1（正式版）中文说明

这是 v26.1 系列的正式稳定版，位于 Alpha.1–Alpha.9（发布候选）之后。

## 相比 v26.0 的更新

- **Alpha.1**：视角真正生效（帧驱动平滑缓动）；功能键（F5 视角、F3 调试等）
  语义化 `function` 动作；启动不再强制占用窗口焦点（`window.grabFocusOnStart`，默认关）。
- **Alpha.2**：用户切走窗口时游戏不再抢占 OS 焦点；有焦点时尊重用户手动视角。
- **Alpha.3**：版本矩阵扩展（fabric 1.20.1/1.21.1/1.21.4/26.2、neoforge、forge、
  native(javaagent)、Aprism 各线）。
- **Alpha.4**：加载器矩阵扩展（neoforge 1.21.4 运行时验证、native 1.21.4）。
- **Alpha.5**：`container` 查询与 `hotbar` 动作。
- **Alpha.6**：API Key 鉴权（`security.apiKeys` + `X-Despotes-Key` 请求头）。
- **Alpha.7**：AI 意图转译（`ai` 动作，接 OpenAI 兼容 LLM）。
- **Alpha.8**：对话式 AI 助手（`POST /despotes/v1/assistant`）。
- **Alpha.9**：世界事件流（`/despotes/v1/events`）与 `chat` 动作。

## 验证（发布前已测试）

- Fabric 26.2 运行时（本周期）：`/events` 端点、`chat` 动作往返、失焦时 look/move 应用、
  防抢焦点生效、hotbar/inventory/container。
- 此前各 Alpha 已在对应加载器上验证其特性。
