package dev.despotes.fabric;

import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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

        // Alpha.9: capture inbound chat / system messages into the event bus so
        // callers can poll GET /despotes/v1/events for what the game said.
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, instant) -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("message", message.getString());
            payload.addProperty("kind", "chat");
            payload.addProperty("sender", sender == null ? "" : sender.getName());
            despotes.eventBus().publish("chat", payload);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("message", message.getString());
            payload.addProperty("kind", "system");
            payload.addProperty("overlay", overlay);
            despotes.eventBus().publish(overlay ? "overlay" : "system", payload);
        });
    }
}
