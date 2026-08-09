package dev.despotes.common.look;

import dev.despotes.common.platform.IGamePlatform;

/**
 * Frame-driven look easing.
 *
 * <p>Instead of discrete per-tick steps (which look steppy and can be overwritten by the
 * player's own input), this animator advances the camera every rendered frame with an
 * ease-in-out curve over a millisecond duration. It always interpolates from the camera's
 * <em>current</em> rotation, and cancels itself if the player moves the camera manually
 * (actual yaw/pitch drifts away from the value we last applied).
 */
public final class LookSmoother {

    private final IGamePlatform platform;

    private boolean active;
    private long startNanos;
    private long durationNanos;
    private float startYaw;
    private float startPitch;
    private float targetYaw;
    private float targetPitch;
    private float lastAppliedYaw;
    private float lastAppliedPitch;

    public LookSmoother(IGamePlatform platform) {
        this.platform = platform;
    }

    /** Begin an eased rotation. {@code durationMs <= 0} applies immediately. */
    public synchronized void start(float targetYaw, float targetPitch, long durationMs) {
        var player = platform.player();
        if (player == null) {
            return;
        }
        float curYaw = player.yaw();
        float curPitch = player.pitch();
        if (durationMs <= 0) {
            active = false;
            platform.setRotation(targetYaw, targetPitch);
            lastAppliedYaw = targetYaw;
            lastAppliedPitch = targetPitch;
            return;
        }
        this.active = true;
        this.startNanos = System.nanoTime();
        this.durationNanos = Math.max(1, durationMs) * 1_000_000L;
        this.startYaw = curYaw;
        this.startPitch = curPitch;
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.lastAppliedYaw = curYaw;
        this.lastAppliedPitch = curPitch;
    }

    /** Cancel any in-flight easing (e.g. a new look command supersedes it). */
    public synchronized void cancel() {
        active = false;
    }

    public synchronized boolean isActive() {
        return active;
    }

    /** Advance the animation one rendered frame. Call on the render thread. */
    public synchronized void frameEnd() {
        if (!active) {
            return;
        }
        var player = platform.player();
        if (player == null) {
            active = false;
            return;
        }
        // If the player moved the camera manually, our last applied value no longer matches
        // reality — cancel so we never yank the view back.
        if (Math.abs(player.yaw() - lastAppliedYaw) > 0.75f
                || Math.abs(player.pitch() - lastAppliedPitch) > 0.75f) {
            active = false;
            return;
        }
        float t = (System.nanoTime() - startNanos) / (float) durationNanos;
        if (t >= 1f) {
            active = false;
            platform.setRotation(targetYaw, targetPitch);
            lastAppliedYaw = targetYaw;
            lastAppliedPitch = targetPitch;
            return;
        }
        float e = easeInOutCubic(Math.max(0f, t));
        float y = startYaw + (targetYaw - startYaw) * e;
        float p = startPitch + (targetPitch - startPitch) * e;
        platform.setRotation(y, p);
        lastAppliedYaw = y;
        lastAppliedPitch = p;
    }

    private static float easeInOutCubic(float t) {
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }
}
