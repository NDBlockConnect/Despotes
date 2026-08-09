package dev.despotes.forge;

import dev.despotes.common.Despotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.LayeredDraw;

import java.util.List;

/** Overlay layer for Forge on the 1.21.x range (LayeredDraw.Layer). */
public final class ForgeHudOverlay {

    private ForgeHudOverlay() {
    }

    /** Registered via AddGuiOverlayLayersEvent; drawn every frame after vanilla layers. */
    public static final LayeredDraw.Layer LAYER = (graphics, deltaTracker) -> {
        Despotes d = Despotes.get();
        if (d != null) {
            d.frameEnd();
            draw(d, graphics);
        }
    };

    public static void draw(Despotes despotes, net.minecraft.client.gui.GuiGraphics graphics) {
        List<String> lines = despotes.overlay().buildLines(despotes);
        if (lines.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int x = 4;
        int y = 4;
        graphics.fill(x - 2, y - 2, x + 180, y + lines.size() * 10 + 2, 0x90000000);
        for (String line : lines) {
            graphics.drawString(font, stripColor(line), x, y, 0xFFEEEEEE, false);
            y += 10;
        }
    }

    private static String stripColor(String s) {
        return s.replace("§e", "").replace("§a", "").replace("§r", "").replace("§", "");
    }
}
