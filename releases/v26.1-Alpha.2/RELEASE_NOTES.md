# Despotes v26.1-Alpha.2 (Pre-Release)

> In-process local control channel for Minecraft: Java Edition.

## Fixes

1. **Focus stealing fixed** — the game no longer grabs OS focus while you work elsewhere:
   - a `MouseGrabMixin` cancels vanilla `grabMouse()` while the window is unfocused
     (the GLFW disabled-cursor mode that pulled the window to the front is neutralized);
   - the per-tick keep-released guard (Alpha.1) still releases the cursor every tick;
   - a best-effort steal-guard returns OS focus to your window (via optional JNA, when
     present on the classpath) if the game steals focus within 1.5 s of you leaving.
   Verified: after switching to another app, `windowFocused=false` and `mouseCaptured=false`
   persist over many ticks (previously the game re-grabbed and yanked focus).
2. **Look never yanks the user's view** — the smoother cancels whenever the window has OS
   focus (you drive the camera manually) and only interpolates while unfocused; manual look
   while unfocused still cancels the animation (threshold guard).

## Verification (tested before release)

- Fabric 26.2: unfocused look smooth-sampled (62.6 → 90), move while unfocused advances,
  focus-loss persistence (no steal), function/data queries from Alpha.1 re-verified.
- Other loaders: rebuild-only this cycle.

## Assets (11)

- `Despotes-v26.1-Alpha.2-fabric-26.2.jar`
- `Despotes-v26.1-Alpha.2-fabric-1.21.4.jar`
- `Despotes-v26.1-Alpha.2-fabric-1.21.1.jar`
- `Despotes-v26.1-Alpha.2-fabric-1.20.1.jar`
- `Despotes-v26.1-Alpha.2-neoforge-26.2.jar`
- `Despotes-v26.1-Alpha.2-neoforge-1.21.1.jar`
- `Despotes-v26.1-Alpha.2-forge-1.21.1.jar`
- `Despotes-v26.1-Alpha.2-native-26.2.jar`
- `Despotes-v26.1-Alpha.2-native-1.21.1.jar`
- `Despotes-v26.1-Alpha.2-native-1.20.1.jar`
- `Despotes-v26.1-Alpha.2-aprism-26.2.aje`

---

# Despotes v26.1-Alpha.2（预发布）中文说明

- **抢焦点修复**：`MouseGrabMixin` 从源头取消失焦时的 vanilla grabMouse（禁用光标模式不再
  把窗口拉到前台）；每 tick 保持释放；可选 JNA 守卫在游戏抢回焦点 1.5s 内归还用户窗口。
  实测：切到其他应用后 `windowFocused`/`mouseCaptured` 持续为 false，不再被抢回。
- **视角不再回正**：窗口有焦点时不覆写（用户手动优先），失焦时才平滑插值；失焦手动移动仍
  取消动画。
- 实测：26.2 失焦下 look 平滑采样、move 前进、数据查询/功能键回归通过；其余加载器构建验证。
