package dev.despotes.fabric;

import dev.despotes.common.Despotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Overlay renderer for the 1.21.x legacy range (GuiGraphics path). */
public final class LegacyHudOverlay {

    private LegacyHudOverlay() {
    }

    public static void draw(Despotes despotes, GuiGraphics graphics) {
        List<String> lines = despotes.overlay().buildLines(despotes);
        if (lines.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int x = 4;
        int y = 4;
        graphics.fill(x - 2, y - 2, x + 180, y + lines.size() * 10 + 2, 0x90000000);
        for (String line : lines) {
            graphics.drawString(font, stripColor(line), x, y, 0xFFEEEEEE);
            y += 10;
        }
    }

    private static String stripColor(String s) {
        return s.replace("§e", "").replace("§a", "").replace("§r", "").replace("§", "");
    }
}
