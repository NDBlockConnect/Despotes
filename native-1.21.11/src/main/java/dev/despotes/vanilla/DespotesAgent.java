package dev.despotes.vanilla;

import dev.despotes.common.Despotes;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Premain entrypoint for the Minecraft-native loader line.
 *
 * <p>v26.2-Alpha.3 — ASM dynamic instrumentation. On top of the legacy poll pump the agent
 * registers {@link DespotesTransformer}, which weaves hooks at class-load time:
 *
 * <ul>
 *   <li>{@code Minecraft.tick()} → {@link DespotesHooks#onClientTick()}: the control core is
 *       driven directly from the game's own tick loop (exact 1:1 alignment, zero scheduler
 *       latency). Once the hook fires the pump below suspends itself.</li>
 *   <li>{@code ClientPacketListener.handleSystemChat/handlePlayerChat/handleDisguisedChat} →
 *       chat and system messages are published onto the {@code /events} stream.</li>
 * </ul>
 *
 * <p>v26.2-Alpha.4 — JavaAgent For All (loader mixing). The agent jar may be attached to a
 * process that ALSO runs a mod loader (fabric / neoforge / forge / aprism) with the matching
 * Despotes mod installed. In that case the agent detects the loader-owned control core and
 * drops into companion mode: it never boots a second instance, never drives a second tick
 * loop, and keeps its own event publishing silent — the mod line remains the single owner.
 * This makes the native agent jar safe to attach alongside any loader artifact.
 *
 * <p>The pump remains as a fallback so the agent still works if instrumentation is skipped
 * (e.g. a future version renames the targets). The agent jar carries the ASM classes inside
 * and is attached with {@code -javaagent:Despotes-...-native-*.jar}.
 */
public final class DespotesAgent {

    private static final long PUMP_INTERVAL_MS = 50; // 20 Hz fallback
    private static volatile boolean started;

    private DespotesAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        registerTransformer(inst);
        agentmain(args, inst);
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
    }

    public static void agentmain(String args, Instrumentation inst) {
        if (started) {
            return;
        }
        started = true;
        ScheduledExecutorService pump = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Despotes-Native-Pump");
            t.setDaemon(true);
            return t;
        });
        pump.scheduleAtFixedRate(DespotesAgent::pumpOnce, 2000, PUMP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private static void registerTransformer(Instrumentation inst) {
        try {
            inst.addTransformer(new DespotesTransformer());
            System.out.println("[Despotes] ASM transformer registered (tick + chat hooks).");
        } catch (Throwable t) {
            System.out.println("[Despotes] transformer registration failed: " + t
                    + " — falling back to pump-only mode.");
        }
    }

    /** One pump: boot Despotes on the client thread, then tick + frameEnd on it. */
    private static void pumpOnce() {
        try {
            if (DespotesHooks.hookActive()) {
                // The ASM tick hook already drives the core at exact tick rate.
                return;
            }
            if (DespotesHooks.companionLoader() != null) {
                // Companion mode: a mod loader owns the core and drives it itself.
                return;
            }
            Despotes existing = Despotes.get();
            if (existing != null && !"native".equals(existing.platform().loaderId())) {
                // Companion mode: a mod loader owns the core and drives it itself.
                return;
            }
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null) {
                return;
            }
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
            mc.execute(() -> {
                Despotes d = Despotes.get() != null ? Despotes.get() : Despotes.boot(new NativePlatform());
                d.clientTick();
                d.frameEnd();
            });
        } catch (Throwable ignored) {
            // Minecraft not ready yet; retry on next pump.
        }
    }
}
