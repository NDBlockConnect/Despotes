package dev.despotes.fabric;

import dev.despotes.common.Despotes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/** Fabric client entrypoint for the 1.21.x legacy range. */
public final class DespotesLegacyFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Despotes despotes = Despotes.boot(new LegacyFabricPlatform());
        ClientTickEvents.END_CLIENT_TICK.register(client -> despotes.clientTick());
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            despotes.frameEnd();
            LegacyHudOverlay.draw(despotes, graphics);
        });
    }
}
