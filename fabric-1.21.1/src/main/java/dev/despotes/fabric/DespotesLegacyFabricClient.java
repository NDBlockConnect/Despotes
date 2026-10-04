package dev.despotes.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.despotes.common.Despotes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;

/**
 * Fabric client entrypoint for the legacy obfuscated range (1.20 – 1.21.11).
 *
 * <p>Uses fabric-api client events for the tick/HUD hooks; private Minecraft members are
 * reached through {@link MinecraftKeyAccess} reflection instead of an access widener so
 * the artifact stays loadable across mapping namespaces.
 */
public final class DespotesLegacyFabricClient implements ClientModInitializer {

    /** v26.13-Alpha.1: HUD visibility toggle; default from despotes.json, rebindable in Controls. */
    private static KeyMapping toggleHudKey;

    @Override
    public void onInitializeClient() {
        Despotes despotes = Despotes.boot(new LegacyFabricPlatform());

        // v26.13-Alpha.1: HUD toggle key bind. The default key comes from
        // visualization.toggleKey in despotes.json; after the first launch the vanilla
        // Controls screen (options.txt) owns the binding.
        try {
            toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.despotes.toggle_hud",
                    InputConstants.Type.KEYSYM,
                    resolveDefaultKey(despotes),
                    "key.categories.despotes"));
        } catch (Throwable t) {
            despotes.platform().log("[Despotes] HUD toggle key bind registration failed: " + t);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            despotes.clientTick();
            if (toggleHudKey != null) {
                while (toggleHudKey.consumeClick()) {
                    despotes.overlay().toggle();
                }
            }
        });
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            despotes.frameEnd();
            LegacyHudOverlay.draw(despotes, graphics);
        });
    }

    /** v26.13-Alpha.1: resolves the configured default key, falling back to F8. */
    private static int resolveDefaultKey(Despotes despotes) {
        try {
            InputConstants.Key key = InputConstants.getKey(despotes.config().visualization.toggleKey);
            if (key != null && key != InputConstants.UNKNOWN) {
                return key.getValue();
            }
        } catch (Throwable t) {
            despotes.platform().log("[Despotes] invalid visualization.toggleKey; falling back to F8");
        }
        return org.lwjgl.glfw.GLFW.GLFW_KEY_F8;
    }
}
