package dev.despotes.fabric;

import dev.despotes.common.Despotes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Draws the Despotes operation overlay via the fabric-api HudElementRegistry path. */
public final class HudOverlay {

    private HudOverlay() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font) {
        Despotes despotes = Despotes.get();
        if (despotes == null) {
            return;
        }
        List<String> lines = despotes.overlay().buildLines(despotes);
        if (lines.isEmpty()) {
            return;
        }
        int x = 4;
        int y = 4;
        graphics.fill(x - 2, y - 2, x + 180, y + lines.size() * 10 + 2, 0x90000000);
        for (String line : lines) {
            graphics.text(font, stripColor(line), x, y, 0xFFEEEEEE);
            y += 10;
        }
    }

    /** Strips § color codes; the extractor text path takes plain strings. */
    private static String stripColor(String s) {
        return s.replace("§e", "").replace("§a", "").replace("§r", "").replace("§", "");
    }
}
