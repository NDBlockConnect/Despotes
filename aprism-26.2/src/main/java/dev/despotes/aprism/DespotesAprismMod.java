package dev.despotes.aprism;

import com.aprism.api.AprismContext;
import com.aprism.api.IAprismMod;
import com.aprism.api.gameevent.ClientRenderEvent;
import com.aprism.api.gameevent.GameTickEvent;
import dev.despotes.common.Despotes;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Aprism Native entrypoint (.aje). Boots the shared control core on the Aprism platform.
 *
 * <p>v26.2-Alpha.5 — event-bus-driven control. The Aprism API exposes typed game events
 * ({@link GameTickEvent}, {@link ClientRenderEvent}), dispatched by the runtime's native
 * injector layer. The mod subscribes to them and drives the control core straight from the
 * game's own tick/render loop:
 *
 * <ul>
 *   <li>{@code GameTickEvent} END stage → {@code clientTick()} — exact 1:1 tick
 *       alignment, no scheduler latency.</li>
 *   <li>{@code ClientRenderEvent} → {@code frameEnd()} — frame-bound work (look
 *       smoothing, overlay) runs at render rate.</li>
 * </ul>
 *
 * <p>Boot timing: the Aprism INIT phase runs <em>before</em> the Minecraft instance is
 * constructed, so {@link Despotes#boot} is deferred until the game thread reports the
 * client object (the pump or an event handler performs the lazy boot). The legacy 20 Hz
 * pump remains as a fallback for runtimes whose injector hooks are not attached yet; it
 * suspends itself once the bus starts delivering.
 */
public final class DespotesAprismMod implements IAprismMod {

    private static volatile ScheduledExecutorService pump;
    /** Set once the Aprism event bus delivers ticks; the pump then stands by. */
    private static volatile boolean busDriven;

    @Override
    public void onInitialize(AprismContext ctx) {
        // Event-bus-driven path: tick + frame hooks from the Aprism injector layer.
        try {
            ctx.getEventBus().register(GameTickEvent.class, event -> {
                if (event.getStage() != GameTickEvent.Stage.END) {
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
                    return;
                }
                busDriven = true;
                Despotes core = ensureBooted();
                if (core != null) {
                    core.clientTick();
                }
            });
            ctx.getEventBus().register(ClientRenderEvent.class, event -> {
                busDriven = true;
                Despotes core = ensureBooted();
                if (core != null) {
                    core.frameEnd();
                }
            });
            ctx.getLogger().info("[Despotes] subscribed to Aprism game events (tick + render).");
        } catch (Throwable t) {
            ctx.getLogger().warning("[Despotes] game-event subscription failed: " + t
                    + " — pump-only mode.");
        }

        // Fallback pump: keeps the core alive when the runtime has not attached the
        // game-event hooks yet. Stands by once the bus delivers.
        if (pump == null) {
            pump = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Despotes-Aprism-Pump");
                t.setDaemon(true);
                return t;
            });
            pump.scheduleAtFixedRate(() -> {
                try {
                    if (busDriven) {
                        return;
                    }
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc != null) {
                        mc.execute(() -> {
                            Despotes core = ensureBooted();
                            if (core != null) {
                                core.clientTick();
                                core.frameEnd();
                            }
                        });
                    }
                } catch (Throwable ignored) {
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
                }
            }, 200, 50, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Lazily boot the control core on the game thread. {@code Despotes.boot} reads the
     * game directory from {@code Minecraft.getInstance()}, which is null during the Aprism
     * INIT phase — callers must therefore invoke this only once the client exists (pump
     * tick or dispatched game event). Safe to call repeatedly; boot happens once.
     */
    private static Despotes ensureBooted() {
        try {
            if (net.minecraft.client.Minecraft.getInstance() == null) {
                return null;
            }
            return Despotes.get() != null ? Despotes.get() : Despotes.boot(new AprismPlatform());
        } catch (Throwable t) {
            return null;
        }
    }
}
