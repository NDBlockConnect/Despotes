package dev.despotes.common.viz;

import com.google.gson.JsonObject;
import dev.despotes.common.protocol.Result;

/** One executed external operation, recorded for visualization and the op log. */
public final class OpEntry {

    public final long atMs;
    public final String sourceId;
    public final String transport;
    public final String requestId;
    public final String type;
    public final boolean ok;
    public final String codeOrExecuted;
    public final long durationUs;

    public OpEntry(String sourceId, String transport, String requestId, String type,
                   Result result, long durationUs) {
        this.atMs = System.currentTimeMillis();
        this.sourceId = sourceId;
        this.transport = transport;
        this.requestId = requestId;
        this.type = type;
        this.ok = result.ok();
        this.codeOrExecuted = result.ok() ? "ok" : result.error().code().name();
        this.durationUs = durationUs;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("at", atMs);
        o.addProperty("source", sourceId);
        o.addProperty("transport", transport);
        o.addProperty("requestId", requestId);
        o.addProperty("type", type);
        o.addProperty("ok", ok);
        o.addProperty("result", codeOrExecuted);
        o.addProperty("durationUs", durationUs);
        return o;
    }

    public String overlayLine() {
        return String.format("[%s#%s] %s %s", transport, requestId, type,
                ok ? "ok" : codeOrExecuted);
    }
}
