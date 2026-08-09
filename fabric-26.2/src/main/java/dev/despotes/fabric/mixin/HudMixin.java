package dev.despotes.fabric.mixin;

import dev.despotes.fabric.HudOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Injects the Despotes overlay at the end of HUD render-state extraction. */
@Mixin(Hud.class)
public abstract class HudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void despotes$overlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        dev.despotes.common.Despotes d = dev.despotes.common.Despotes.get();
        if (d != null) {
            d.frameEnd();
        }
        HudOverlay.draw(graphics, Minecraft.getInstance().font);
    }
}
