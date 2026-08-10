package dev.despotes.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.despotes.common.platform.ScreenHandle;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/** Legacy screen handle (official mappings, 1.20 – 1.21.11). */
public final class LegacyScreenHandle implements ScreenHandle {

    private final Screen screen;

    public LegacyScreenHandle(Screen screen) {
        this.screen = screen;
    }

    @Override
    public boolean open() {
        return true;
    }

    @Override
    public String title() {
        return screen.getTitle().getString();
    }

    @Override
    public int width() {
        return screen.width;
    }

    @Override
    public int height() {
        return screen.height;
    }

    @Override
    public JsonArray widgetTree(int maxDepth) {
        JsonArray arr = new JsonArray();
        if (maxDepth <= 0) {
            return arr;
        }
        int i = 0;
        for (GuiEventListener child : screen.children()) {
            if (i++ >= 200) {
                break;
            }
            JsonObject o = new JsonObject();
            o.addProperty("class", child.getClass().getSimpleName());
            o.addProperty("focused", child.isFocused());
            try {
                var rect = child.getRectangle();
                o.addProperty("x", rect.left());
                o.addProperty("y", rect.top());
                o.addProperty("w", rect.width());
                o.addProperty("h", rect.height());
            } catch (Throwable ignored) {
            }
            arr.add(o);
        }
        return arr;
    }
}
