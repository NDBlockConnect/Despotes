package dev.despotes.fabric;

import dev.despotes.common.Despotes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Fabric client entrypoint: boots Despotes and wires the client tick hook. */
public final class DespotesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Despotes despotes = Despotes.boot(new FabricPlatform());
        ClientTickEvents.END_CLIENT_TICK.register(client -> despotes.clientTick());
    }
}
