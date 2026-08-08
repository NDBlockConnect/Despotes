package dev.despotes.common.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Read-only view of the current screen and its widget tree, implemented per loader. */
public interface ScreenHandle {

    boolean open();

    String title();

    int width();

    int height();

    /** Bounded-depth widget dump for the `screen` query. */
    JsonArray widgetTree(int maxDepth);

    default JsonObject statusJson() {
        JsonObject o = new JsonObject();
        o.addProperty("open", open());
        o.addProperty("title", title());
        o.addProperty("width", width());
        o.addProperty("height", height());
        return o;
    }
}
