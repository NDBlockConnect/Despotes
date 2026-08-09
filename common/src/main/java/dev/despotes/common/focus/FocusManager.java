package dev.despotes.common.focus;

import dev.despotes.common.config.DespotesConfig;
import dev.despotes.common.platform.IGamePlatform;

/**
 * Keeps the OS mouse cursor free while the game window is unfocused.
 *
 * <p>Vanilla Minecraft never releases the captured (grabbed) cursor when its window loses
 * OS focus, so the game keeps locking the user's mouse even though they are working
 * elsewhere. This manager watches the window focus state each client tick and:
 *
 * <ul>
 *   <li>on focus loss — releases the captured cursor (if it was captured), remembering the
 *       previous state;</li>
 *   <li>on focus regain — re-captures the cursor automatically when it had been captured
 *       before the loss and no screen is open.</li>
 * </ul>
 *
 * Both behaviors are configurable via the {@code focus} section of {@code despotes.json}.
 */
public final class FocusManager {

    private final IGamePlatform platform;
    private Boolean lastFocused;
    private boolean capturedBeforeLoss;
    private boolean pauseSettingApplied;
    private Object userForeground;
    private long lostNanos;

    public FocusManager(IGamePlatform platform) {
        this.platform = platform;
    }

    /** Called once per client tick on the client thread. */
    public void tick(DespotesConfig config) {
        if (!pauseSettingApplied) {
            pauseSettingApplied = true;
            if (config.focus.preventPauseOnFocusLoss) {
                try {
                    platform.setPauseOnLostFocus(false);
                    platform.log("[Despotes] disabled vanilla pause-on-lost-focus "
                            + "(external control requires an unpaused unfocused game).");
                } catch (Throwable t) {
                    platform.log("[Despotes] failed to disable pause-on-lost-focus: " + t);
                }
            }
        }

        boolean focused;
        try {
            focused = platform.windowFocused();
        } catch (Throwable t) {
            return;
        }

        // While unfocused, vanilla can re-grab the cursor on some ticks. Keep it released
        // every tick so the game never locks/steals the user's mouse while they work
        // in another app.
        if (!focused && config.focus.keepReleasedWhileUnfocused
                && platform.isMouseCaptured()) {
            try {
                platform.releaseMouseCapture();
            } catch (Throwable t) {
                platform.log("[Despotes] keep-released failed: " + t);
            }
        }
        if (lastFocused == null) {
            lastFocused = focused;
            return;
        }
        if (focused == lastFocused) {
            return;
        }
        lastFocused = focused;

        if (!focused) {
            lostNanos = System.nanoTime();
            userForeground = dev.despotes.common.focus.Win32Focus.foregroundHandle();
            if (config.focus.releaseMouseOnFocusLoss && platform.isMouseCaptured()) {
                capturedBeforeLoss = true;
                try {
                    platform.releaseMouseCapture();
                    platform.log("[Despotes] window unfocused — mouse cursor released.");
                } catch (Throwable t) {
                    platform.log("[Despotes] mouse release failed: " + t);
                }
            }
        } else {
            // If the game stole focus back within 1.5s of the user leaving, hand the OS
            // focus back to the user's window (best-effort, optional JNA).
            long sinceLossMs = (System.nanoTime() - lostNanos) / 1_000_000L;
            if (config.focus.returnFocusOnSteal && userForeground != null
                    && sinceLossMs < 1500) {
                dev.despotes.common.focus.Win32Focus.setForeground(userForeground);
                platform.log("[Despotes] returned OS focus to user window (steal guard).");
            }
            userForeground = null;
            if (config.focus.regrabMouseOnFocusGain && capturedBeforeLoss
                    && !platform.isMouseCaptured()) {
                try {
                    platform.grabMouseCapture();
                } catch (Throwable t) {
                    platform.log("[Despotes] mouse re-capture failed: " + t);
                }
            }
            capturedBeforeLoss = false;
        }
    }
}
