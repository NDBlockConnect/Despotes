package dev.despotes.common.platform;

import com.google.gson.JsonObject;

/** Read-only view of the local player, implemented per loader. */
public interface PlayerHandle {

    String name();

    double x();

    double y();

    double z();

    float yaw();

    float pitch();

    float health();

    String dimension();

    int selectedHotbarSlot();

    default JsonObject statusJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name());
        o.addProperty("x", x());
        o.addProperty("y", y());
        o.addProperty("z", z());
        o.addProperty("yaw", yaw());
        o.addProperty("pitch", pitch());
        o.addProperty("health", health());
        o.addProperty("dimension", dimension());
        o.addProperty("selectedSlot", selectedHotbarSlot());
        return o;
    }
}
