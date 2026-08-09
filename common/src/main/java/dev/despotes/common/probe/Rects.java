package dev.despotes.common.probe;

import com.google.gson.JsonObject;

import java.lang.reflect.Method;

/**
 * Version-tolerant rectangle reader.
 *
 * <p>Different MC versions expose coordinates differently: 26.x {@code ScreenRectangle}
 * has {@code x()}/{@code y()} accessors; 1.21.x exposes {@code left()}/{@code top()} plus a
 * {@code position()} record with {@code getCoordinate(ScreenAxis)}. This helper reads all of
 * them reflectively so a single source compiles across versions.
 */
public final class Rects {

    private Rects() {
    }

    public static JsonObject rectJson(Object rect) {
        JsonObject o = new JsonObject();
        if (rect == null) {
            return o;
        }
        try {
            Integer x = callInt(rect, "x");
            Integer y = callInt(rect, "y");
            if (x == null) {
                x = callInt(rect, "left");
                y = callInt(rect, "top");
            }
            if (x == null) {
                Object pos = rect.getClass().getMethod("position").invoke(rect);
                x = callInt(pos, "x");
                y = callInt(pos, "y");
                if (x == null) {
                    x = coord(pos, "HORIZONTAL");
                    y = coord(pos, "VERTICAL");
                }
            }
            Integer w = callInt(rect, "width");
            Integer h = callInt(rect, "height");
            if (x != null) o.addProperty("x", x);
            if (y != null) o.addProperty("y", y);
            if (w != null) o.addProperty("w", w);
            if (h != null) o.addProperty("h", h);
        } catch (Throwable ignored) {
        }
        return o;
    }

    private static Integer coord(Object position, String axisName) {
        try {
            Class<?> axisCls = Class.forName("net.minecraft.client.gui.navigation.ScreenAxis");
            Object axis = axisCls.getField(axisName).get(null);
            Method m = position.getClass().getMethod("getCoordinate", axisCls);
            return ((Number) m.invoke(position, axis)).intValue();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Integer callInt(Object o, String method) {
        try {
            Method m = o.getClass().getMethod(method);
            return ((Number) m.invoke(o)).intValue();
        } catch (Throwable t) {
            return null;
        }
    }
}
