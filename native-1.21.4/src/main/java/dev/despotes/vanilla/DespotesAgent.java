package dev.despotes.vanilla;

import dev.despotes.common.Despotes;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Premain entrypoint for the Minecraft-native loader line.
 *
 * <p>Instead of bytecode weaving, the native line drives the shared control core from a
 * low-frequency poller that hops onto the client thread via
 * {@code Minecraft.getInstance().execute(...)}: the client tick is pumped at 20 Hz and the
 * HUD frame-end hook at ~30 Hz, which is sufficient for the dispatcher queue and keeps the
 * overlay/op-log visualization current. The agent jar is attached with
 * {@code -javaagent:Despotes-...-native-26.2.jar}.
 */
public final class DespotesAgent {

    private static final long PUMP_INTERVAL_MS = 50; // 20 Hz
    private static volatile boolean started;

    private DespotesAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
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

    /** One pump: boot Despotes on the client thread, then tick + frameEnd on it. */
    private static void pumpOnce() {
        try {
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
