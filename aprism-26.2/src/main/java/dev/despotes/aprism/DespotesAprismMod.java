package dev.despotes.aprism;

import com.aprism.api.AprismContext;
import com.aprism.api.IAprismMod;
import dev.despotes.common.Despotes;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Aprism Native entrypoint (.aje). Boots the shared control core on the Aprism platform and
 * drives it from a 20 Hz poller hopping onto the client thread (the Aprism API surface does
 * not expose a client-tick event in this pre-release).
 */
public final class DespotesAprismMod implements IAprismMod {

    private static volatile ScheduledExecutorService pump;

    @Override
    public void onInitialize(AprismContext ctx) {
        Despotes.boot(new AprismPlatform());
        if (pump == null) {
            pump = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Despotes-Aprism-Pump");
                t.setDaemon(true);
                return t;
            });
            pump.scheduleAtFixedRate(() -> {
                try {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc != null) {
                        mc.execute(() -> {
                            Despotes d = Despotes.get();
                            if (d != null) {
                                d.clientTick();
                                d.frameEnd();
                            }
                        });
                    }
                } catch (Throwable ignored) {
                }
            }, 200, 50, TimeUnit.MILLISECONDS);
        }
    }
}
