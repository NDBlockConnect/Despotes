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
 * now registers {@link DespotesTransformer}, which weaves two hooks at class-load time:
 *
 * <ul>
 *   <li>{@code Minecraft.tick()} → {@link DespotesHooks#onClientTick()}: the control core is
 *       driven directly from the game's own tick loop (exact 1:1 alignment, zero scheduler
 *       latency). Once the hook fires the pump below suspends itself.</li>
 *   <li>{@code ClientPacketListener.handleSystemChat/handlePlayerChat} → chat and system
 *       messages are published onto the {@code /events} stream, closing the perception gap
 *       the native line had versus the fabric line.</li>
 * </ul>
 *
 * <p>The pump remains as a fallback so the agent still works if instrumentation is skipped
 * (e.g. a future version renames the targets). The agent jar carries the ASM classes inside
 * (see the shadow configuration) and is attached with
 * {@code -javaagent:Despotes-...-native-26.2.jar}.
 */
public final class DespotesAgent {

    private static final long PUMP_INTERVAL_MS = 50; // 20 Hz fallback
    private static volatile boolean started;

    private DespotesAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        registerTransformer(inst);
        agentmain(args, inst);
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
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null) {
                return;
            }
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
