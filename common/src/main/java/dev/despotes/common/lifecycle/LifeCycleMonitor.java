package dev.despotes.common.lifecycle;

import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import dev.despotes.common.platform.PlayerHandle;

/**
 * v26.2 death & lifecycle awareness (known issue: the agent could not tell whether it had
 * died). Runs on the client thread once per tick and maintains a three-state machine:
 *
 * <pre>
 *   menu ── world load ──▶ playing ── health depleted ──▶ dead
 *     ▲                        ▲                              │
 *     └──── world left ────────┴──────── respawn ─────────────┘
 * </pre>
 *
 * Every transition publishes an event onto the shared {@code /events} stream
 * ({@code world_joined}, {@code death}, {@code respawn}, {@code world_left}) and the
 * current snapshot is embedded in {@code /status} as {@code lifecycle}, so an agent can
 * both poll and observe. Death detection is data-level ({@link PlayerHandle#dead()}),
 * independent of which screen is open.
 */
public final class LifeCycleMonitor {

    /** Coarse game state exposed to agents. */
    public enum State {
        MENU, PLAYING, DEAD
    }

    private final Despotes despotes;
    private State state = State.MENU;
    private int deathCount;
    private long lastDeathMs;
    private long lastRespawnMs;

    public LifeCycleMonitor(Despotes despotes) {
        this.despotes = despotes;
    }

    /** Client-thread tick: observe state and publish transition events. */
    public void tick() {
        var platform = despotes.platform();
        boolean inGame = platform.inGame();
        PlayerHandle player = inGame ? platform.player() : null;
        State observed;
        if (!inGame || player == null) {
            observed = State.MENU;
        } else if (player.dead()) {
            observed = State.DEAD;
        } else {
            observed = State.PLAYING;
        }

        if (observed == state) {
            return;
        }
        State previous = state;
        state = observed;
        publishTransition(previous, observed, player);
    }

    private void publishTransition(State from, State to, PlayerHandle player) {
        JsonObject payload = new JsonObject();
        payload.addProperty("from", from.name().toLowerCase());
        payload.addProperty("to", to.name().toLowerCase());
        if (player != null) {
            payload.addProperty("name", player.name());
            payload.addProperty("x", player.x());
            payload.addProperty("y", player.y());
            payload.addProperty("z", player.z());
            payload.addProperty("health", player.health());
            payload.addProperty("dimension", player.dimension());
        }
        long now = System.currentTimeMillis();
        String type;
        switch (to) {
            case DEAD -> {
                deathCount++;
                lastDeathMs = now;
                type = "death";
                payload.addProperty("deathCount", deathCount);
            }
            case PLAYING -> {
                type = (from == State.DEAD) ? "respawn" : "world_joined";
                if (from == State.DEAD) {
                    lastRespawnMs = now;
                }
            }
            default -> type = "world_left";
        }
        despotes.eventBus().publish(type, payload);
        despotes.platform().log("[Despotes] lifecycle: " + from + " -> " + to);
    }

    /** Snapshot for the {@code lifecycle} field of /status. */
    public JsonObject snapshot() {
        JsonObject o = new JsonObject();
        o.addProperty("state", state.name().toLowerCase());
        o.addProperty("dead", state == State.DEAD);
        o.addProperty("deathCount", deathCount);
        if (lastDeathMs > 0) {
            o.addProperty("lastDeathMs", lastDeathMs);
        }
        if (lastRespawnMs > 0) {
            o.addProperty("lastRespawnMs", lastRespawnMs);
        }
        return o;
    }

    public State state() {
        return state;
    }
}
