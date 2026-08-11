package dev.despotes.common.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

/** JSON helpers shared by all transports. */
public final class Json {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {
    }

    public static Gson gson() {
        return GSON;
    }

    public static String stringify(JsonElement e) {
        return GSON.toJson(e);
    }

    /** Parses a command line; throws ProtocolError on malformed input. */
    public static JsonObject parseCommand(String line) {
        if (line == null || line.isBlank()) {
            throw ProtocolError.badRequest("empty command");
        }
        JsonElement el;
        try {
            el = JsonParser.parseString(line);
        } catch (Exception e) {
            throw ProtocolError.badRequest("malformed JSON: " + e.getMessage());
        }
        if (!el.isJsonObject()) {
            throw ProtocolError.badRequest("command must be a JSON object");
        }
        return el.getAsJsonObject();
    }

    public static String ok(String requestId, JsonElement result) {
        JsonObject o = new JsonObject();
        o.addProperty("requestId", requestId == null ? "" : requestId);
        o.addProperty("ok", true);
        o.add("result", result);
        return stringify(o);
    }

    /**
     * v26.2-Alpha.6: success envelope carrying the command's own latency breakdown, so
     * callers do not need a second /status round-trip to know how long the command waited
     * in the queue and how long it took to execute.
     */
    public static String ok(String requestId, JsonElement result, long waitedUs, long execUs) {
        JsonObject o = new JsonObject();
        o.addProperty("requestId", requestId == null ? "" : requestId);
        o.addProperty("ok", true);
        o.add("result", result);
        o.addProperty("waitedMs", Math.round(waitedUs / 100.0) / 10.0);
        o.addProperty("execMs", Math.round(execUs / 100.0) / 10.0);
        return stringify(o);
    }

    public static String error(String requestId, ProtocolError err) {
        JsonObject o = new JsonObject();
        o.addProperty("requestId", requestId == null ? "" : requestId);
        o.addProperty("ok", false);
        JsonObject e = new JsonObject();
        e.addProperty("code", err.code().name());
        e.addProperty("message", err.getMessage());
        o.add("error", e);
        return stringify(o);
    }

    public static String getStr(JsonObject o, String key, String def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    public static int getInt(JsonObject o, String key, int def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : def;
    }

    public static long getLong(JsonObject o, String key, long def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : def;
    }

    public static double getDouble(JsonObject o, String key, double def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : def;
    }

    public static boolean getBool(JsonObject o, String key, boolean def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : def;
    }

    public static JsonObject getObj(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonObject() ? o.get(key).getAsJsonObject() : null;
    }

    public static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
