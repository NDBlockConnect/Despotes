package dev.despotes.fabric;

import dev.despotes.common.Despotes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Fabric client entrypoint for the legacy obfuscated range (1.20 – 1.21.11).
 *
 * <p>Uses fabric-api client events for the tick/HUD hooks; private Minecraft members are
 * reached through {@link MinecraftKeyAccess} reflection instead of an access widener so
 * the artifact stays loadable across mapping namespaces.
 */
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
