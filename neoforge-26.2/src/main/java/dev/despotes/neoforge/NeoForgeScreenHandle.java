package dev.despotes.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.despotes.common.platform.ScreenHandle;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/** NeoForge screen handle backed by the live Screen. */
public final class NeoForgeScreenHandle implements ScreenHandle {

    private final Screen screen;

    public NeoForgeScreenHandle(Screen screen) {
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
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
            if (i++ >= 200) {
                break;
            }
            JsonObject o = new JsonObject();
            o.addProperty("class", child.getClass().getSimpleName());
            o.addProperty("focused", child.isFocused());
            var rect = child.getRectangle();
            o.addProperty("x", rect.position().x());
            o.addProperty("y", rect.position().y());
            o.addProperty("w", rect.width());
            o.addProperty("h", rect.height());
            arr.add(o);
        }
        return arr;
    }
}
