package dev.despotes.fabric.mixin;

import dev.despotes.common.Despotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents the game from stealing OS focus while the window is unfocused (issue: the game
 * kept grabbing the cursor on unfocused ticks, and GLFW's disabled-cursor mode pulled the
 * window to the front). Vanilla re-grab is cancelled at the source while unfocused.
 */
@Mixin(MouseHandler.class)
public abstract class MouseGrabMixin {

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void despotes$cancelGrabWhenUnfocused(CallbackInfo ci) {
        Despotes d = Despotes.get();
        if (d != null && !Minecraft.getInstance().isWindowActive()
                && d.config().focus.keepReleasedWhileUnfocused) {
            ci.cancel();
        }
    }
}
