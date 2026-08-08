package dev.despotes.common.action;

import dev.despotes.common.protocol.Json;
import com.google.gson.JsonObject;

/** Screenshot request parameters. */
public final class ScreenshotOptions {

    public String format = "png";
    public double quality = 0.8;
    public boolean save = false;
    public String path = null;
    public int maxWidth = 0;

    public static ScreenshotOptions fromJson(JsonObject o) {
        ScreenshotOptions s = new ScreenshotOptions();
        if (o == null) {
            return s;
        }
        s.format = Json.normalize(Json.getStr(o, "format", "png"));
        if (!s.format.equals("png") && !s.format.equals("jpg") && !s.format.equals("jpeg")) {
            s.format = "png";
        }
        if (s.format.equals("jpeg")) {
            s.format = "jpg";
        }
        s.quality = Math.min(1.0, Math.max(0.1, Json.getDouble(o, "quality", 0.8)));
        s.save = Json.getBool(o, "save", false);
        s.path = Json.getStr(o, "path", null);
        s.maxWidth = Math.max(0, Json.getInt(o, "maxWidth", 0));
        return s;
    }
}
