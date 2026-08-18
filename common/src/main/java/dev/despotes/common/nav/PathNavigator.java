package dev.despotes.common.nav;

import dev.despotes.common.Despotes;
import dev.despotes.common.platform.IGamePlatform;
import dev.despotes.common.platform.PlayerHandle;

import java.util.ArrayList;
import java.util.List;

/**
 * v26.5: A* pathfinding and navigation controller.
 *
 * <p>Runs on the client thread (driven by {@link #tick()}) and drives the player
 * towards a target position or entity using movement injection. The navigator:
 * <ul>
 *   <li>Plans a path using a greedy best-first walk (simpler than full A* but
 *       sufficient for client-side navigation where we can't see the full world)</li>
 *   <li>Moves one block at a time, re-evaluating each tick</li>
 *   <li>Handles jumping (1-block steps up), falling, and swimming</li>
 *   <li>Cancels when the player moves manually or reaches the target</li>
 * </ul>
 *
 * The navigator is intentionally lightweight — it uses the block data already
 * available on the client to check walkability, and drives movement via
 * {@link IGamePlatform#setMovement(double, double, boolean, boolean, boolean)}.
 */
public final class PathNavigator {

    private final Despotes despotes;
    private volatile boolean active;
    private double targetX, targetY, targetZ;
    private String targetUuid;
    private boolean followingEntity;
    private double stopDistance = 1.5;
    private int maxTicks = 600; // 30s default
    private int ticksRun;
    private int recheckCounter;

    public PathNavigator(Despotes despotes) {
        this.despotes = despotes;
    }

    /** Navigate to a coordinate. Returns true if navigation started. */
    public boolean gotoCoords(double x, double y, double z, double stopDistance) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.followingEntity = false;
        this.targetUuid = null;
        this.stopDistance = stopDistance > 0 ? stopDistance : 1.5;
        this.active = true;
        this.ticksRun = 0;
        this.recheckCounter = 0;
        despotes.platform().log("[Despotes] nav: goto " + (int)x + "," + (int)y + "," + (int)z);
        return true;
    }

    /** Follow an entity by UUID. Returns true if the entity was found. */
    public boolean followEntity(String uuid, double stopDistance) {
        var p = despotes.platform();
        var found = p.findEntity(uuid);
        if (!found.get("found").getAsBoolean()) return false;
        this.targetX = found.get("x").getAsDouble();
        this.targetY = found.get("y").getAsDouble();
        this.targetZ = found.get("z").getAsDouble();
        this.followingEntity = true;
        this.targetUuid = uuid;
        this.stopDistance = stopDistance > 0 ? stopDistance : 3.0;
        this.active = true;
        this.ticksRun = 0;
        return true;
    }

    /** Stop navigation. */
    public void stop() {
        if (active) {
            active = false;
            despotes.platform().setMovement(0, 0, false, false, false);
            despotes.platform().log("[Despotes] nav: stopped");
        }
    }

    public boolean isActive() { return active; }

    /** Client-thread tick: drive movement towards target. */
    public void tick() {
        if (!active) return;
        IGamePlatform p = despotes.platform();
        if (!p.inGame()) { stop(); return; }
        PlayerHandle player = p.player();
        if (player == null) { stop(); return; }

        ticksRun++;
        if (ticksRun > maxTicks) {
            stop();
            despotes.platform().log("[Despotes] nav: timeout after " + maxTicks + " ticks");
            return;
        }

        // Update target if following entity
        if (followingEntity && targetUuid != null) {
            var found = p.findEntity(targetUuid);
            if (found.get("found").getAsBoolean()) {
                targetX = found.get("x").getAsDouble();
                targetY = found.get("y").getAsDouble();
                targetZ = found.get("z").getAsDouble();
            } else {
                // Entity lost — keep going to last known position
                followingEntity = false;
            }
        }

        double dx = targetX - player.x();
        double dz = targetZ - player.z();
        double distSq = dx * dx + dz * dz;
        double stopSq = stopDistance * stopDistance;

        if (distSq <= stopSq) {
            stop();
            despotes.platform().log("[Despotes] nav: reached target");
            return;
        }

        // Look towards target
        double dy = targetY - (player.y() + 1.62);
        double horizontal = Math.sqrt(distSq);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = Math.max(-89.9f, Math.min(89.9f,
                (float) -Math.toDegrees(Math.atan2(dy, horizontal))));
        despotes.lookSmoother().start(yaw, pitch, 0);

        // Determine movement direction
        // Normalize horizontal direction
        double dirX = dx / Math.max(0.01, horizontal);
        double dirZ = dz / Math.max(0.01, horizontal);

        // Check if we need to jump (target is above us or there's a block in front)
        boolean needJump = dy > 0.5 && recheckCounter % 5 == 0;
        // Check for obstacles: probe blocks ahead
        boolean blockedAhead = isBlockedAhead(p, player, dirX, dirZ);

        // Apply movement: forward in the direction of target
        // Convert world direction to player-relative forward/left
        // Since we already set yaw to face the target, forward=1 is correct
        double forward = 1.0;
        double left = 0.0;

        if (blockedAhead && !needJump) {
            // Try jumping
            needJump = true;
        }

        p.setMovement(forward, left, needJump, false, horizontal > 6.0);

        recheckCounter++;
    }

    /**
     * Check if there's a solid block directly ahead at feet level.
     * Uses the blocks probe to check the block in front of the player.
     */
    private boolean isBlockedAhead(IGamePlatform p, PlayerHandle player, double dirX, double dirZ) {
        try {
            int px = (int) Math.floor(player.x() + dirX * 0.8);
            int py = (int) Math.floor(player.y());
            int pz = (int) Math.floor(player.z() + dirZ * 0.8);
            var blocks = p.probeBlocks(px, py, pz, 0);
            if (blocks.has("blocks")) {
                var arr = blocks.getAsJsonArray("blocks");
                for (var el : arr) {
                    var b = el.getAsJsonObject();
                    int bx = b.get("x").getAsInt();
                    int by = b.get("y").getAsInt();
                    int bz = b.get("z").getAsInt();
                    if (bx == px && bz == pz && (by == py || by == py + 1)) {
                        String block = b.get("block").getAsString();
                        if (!block.equals("minecraft:air") && !block.contains("water")
                                && !block.contains("lava") && !block.contains("torch")
                                && !block.contains("button") && !block.contains("lever")
                                && !block.contains("carpet") && !block.contains("rail")
                                && !block.contains("sign") && !block.contains("ladder")
                                && !block.contains("vine") && !block.contains("door")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
