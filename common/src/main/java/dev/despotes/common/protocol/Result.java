package dev.despotes.common.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Execution outcome of a single command, rendered into the response envelope. */
public final class Result {

    private final boolean ok;
    private final JsonElement result;
    private final ProtocolError error;

    private Result(boolean ok, JsonElement result, ProtocolError error) {
        this.ok = ok;
        this.result = result;
        this.error = error;
    }

    public static Result ok(JsonElement result) {
        return new Result(true, result, null);
    }

    public static Result ok(String simpleValue) {
        JsonObject o = new JsonObject();
        o.addProperty("executed", simpleValue);
        return ok(o);
    }

    public static Result fail(ProtocolError err) {
        return new Result(false, null, err);
    }

    public boolean ok() {
        return ok;
    }

    public JsonElement result() {
        return result;
    }

    public ProtocolError error() {
        return error;
    }

    public String toJsonString(String requestId) {
        return ok ? Json.ok(requestId, result) : Json.error(requestId, error);
    }

    /** v26.2-Alpha.6: envelope carrying the command's queue wait and execution time. */
    public String toJsonString(String requestId, long waitedUs, long execUs) {
        return ok ? Json.ok(requestId, result, waitedUs, execUs) : Json.error(requestId, error);
    }
}
