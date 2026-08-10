# Despotes v26.1-Alpha.6 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition.

## Highlights — API Key support (foundation for AI features)

1. **API Key authentication** — `security.apiKeys` (array). When non-empty, **every** request
   must carry one of the keys via the `X-Despotes-Key` HTTP header (or the CLI/filedrop
   `token` field). Loopback is **not** exempt once keys are configured.
2. **Backward compatible** — with an empty `apiKeys` the behavior is unchanged (loopback
   allowed, optional `requireToken` still honored).
3. Verified on Fabric 26.2: no key → `FORBIDDEN`; correct key → success; wrong key →
   `FORBIDDEN`.
4. **Fourteen artifacts**.

## Verification (tested before release)

- Fabric 26.2 runtime: API Key three-case matrix verified; regression of prior features.
- Other loaders rebuild-only.

## Assets (14)

- `Despotes-v26.1-Alpha.6-fabric-26.2.jar`
- `Despotes-v26.1-Alpha.6-fabric-1.21.4.jar`
- `Despotes-v26.1-Alpha.6-fabric-1.21.1.jar`
- `Despotes-v26.1-Alpha.6-fabric-1.20.1.jar`
- `Despotes-v26.1-Alpha.6-neoforge-26.2.jar`
- `Despotes-v26.1-Alpha.6-neoforge-1.21.4.jar`
- `Despotes-v26.1-Alpha.6-neoforge-1.21.1.jar`
- `Despotes-v26.1-Alpha.6-forge-1.21.1.jar`
- `Despotes-v26.1-Alpha.6-forge-1.20.1.jar`
- `Despotes-v26.1-Alpha.6-native-26.2.jar`
- `Despotes-v26.1-Alpha.6-native-1.21.4.jar`
- `Despotes-v26.1-Alpha.6-native-1.21.1.jar`
- `Despotes-v26.1-Alpha.6-native-1.20.1.jar`
- `Despotes-v26.1-Alpha.6-aprism-26.2.aje`

---

# Despotes v26.1-Alpha.6（预发布）中文说明

- **API Key 鉴权**：`security.apiKeys` 数组；非空时所有请求须携带其一（HTTP 头
  `X-Despotes-Key` 或 CLI/filedrop 的 `token` 字段）。配置后回环不再豁免。
- **向后兼容**：空 `apiKeys` 时行为不变。
- 实测（Fabric 26.2）：无 key→FORBIDDEN；正确 key→通过；错误 key→FORBIDDEN。
- 本周期十四个构件。
