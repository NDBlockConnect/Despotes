package dev.despotes.common.transport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.despotes.common.Despotes;
import dev.despotes.common.action.ActionContext;
import dev.despotes.common.action.Actions;
import dev.despotes.common.protocol.Json;
import dev.despotes.common.protocol.ProtocolError;
import dev.despotes.common.protocol.Result;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * Security gate + shared command routing for transports.
 *
 * <ul>
 *   <li>Loopback peers are always allowed.</li>
 *   <li>Non-loopback peers must be listed in {@code security.allowSources}.</li>
 *   <li>{@code security.requireToken} demands a matching token on every request.</li>
 * </ul>
 */
public final class SecurityGate {

    private final Despotes despotes;

    public SecurityGate(Despotes despotes) {
        this.despotes = despotes;
    }

    /** Validates peer address + token. Throws ProtocolError(FORBIDDEN) on denial. */
    public void checkPeer(InetSocketAddress remote, String token) {
        if (!despotes.config().security.enabled) {
            throw ProtocolError.forbidden("security switch is off; control disabled");
        }
        if (remote != null) {
            InetAddress addr = remote.getAddress();
            boolean loopback = addr != null && (addr.isLoopbackAddress());
            if (!loopback) {
                List<String> allow = despotes.config().security.allowSources;
                String host = addr == null ? "" : addr.getHostAddress();
                if (!allow.contains(host)) {
                    throw ProtocolError.forbidden("source not allowed: " + host);
                }
            }
        }
        if (despotes.config().security.requireToken) {
            String expected = despotes.config().security.token;
            if (expected == null || expected.isBlank() || !expected.equals(token)) {
                throw ProtocolError.forbidden("missing or invalid token");
            }
        }
        // API keys (Alpha.6): when configured, every request must present one of them.
        List<String> keys = despotes.config().security.apiKeys;
        if (!keys.isEmpty()) {
            if (token == null || !keys.contains(token)) {
                throw ProtocolError.forbidden("missing or invalid API key");
            }
        }
    }

    /**
     * Routes one parsed command object. Query-type commands take a read-only snapshot on
     * the client thread; action-type commands are queued through the dispatcher and this
     * call blocks until the client thread executes them.
     */
    public String route(String transportId, JsonObject cmd, InetSocketAddress remote) {
        String requestId = Json.getStr(cmd, "requestId", "");
        String token = Json.getStr(cmd, "token", null);
        try {
            checkPeer(remote, token);
            String type = Json.normalize(Json.getStr(cmd, "type", ""));
            if (isQuery(type)) {
                // v26.2-Alpha.6: queries run inline on the client thread (never queued), so
                // their queue wait is always 0; measure the snapshot execution time and
                // surface it in the envelope for parity with queued actions.
                java.util.concurrent.atomic.AtomicLong execUs = new java.util.concurrent.atomic.AtomicLong();
                Result r = despotes.platform().awaitOnClientThread(() -> {
                    long start = System.nanoTime();
                    ActionContext ctx = new ActionContext(despotes, requestId, transportId, transportId);
                    Result res = Actions.execute(ctx, cmd);
                    execUs.set((System.nanoTime() - start) / 1000);
                    return res;
                }, despotes.config().http.screenshotTimeoutMs);
                if (r == null) {
                    return Json.error(requestId, ProtocolError.timeout("query timed out"));
                }
                despotes.latency().record(0, execUs.get());
                return r.toJsonString(requestId, 0, execUs.get());
            }
            return despotes.dispatcher().submit(
                    new dev.despotes.common.dispatcher.Dispatcher.Command(cmd, requestId, transportId, transportId),
                    despotes.config().http.screenshotTimeoutMs);
        } catch (ProtocolError e) {
            return Json.error(requestId, e);
        }
    }

    /** Routes a batch envelope {"batch":[...], "parallel":bool}. */
    public String routeBatch(String transportId, JsonObject envelope, InetSocketAddress remote) {
        String requestId = Json.getStr(envelope, "requestId", "");
        String token = Json.getStr(envelope, "token", null);
        try {
            checkPeer(remote, token);
        } catch (ProtocolError e) {
            return Json.error(requestId, e);
        }
        JsonArray arr = envelope.getAsJsonArray("batch");
        JsonArray results = new JsonArray();
        long waitMs = despotes.config().http.screenshotTimeoutMs;
        for (var el : arr) {
            if (!el.isJsonObject()) {
                JsonObject bad = new JsonObject();
                bad.addProperty("ok", false);
                bad.addProperty("error", "batch element is not an object");
                results.add(bad);
                continue;
            }
            JsonObject c = el.getAsJsonObject();
            String rid = Json.getStr(c, "requestId", requestId);
            String type = Json.normalize(Json.getStr(c, "type", ""));
            try {
                if (isQuery(type)) {
                    java.util.concurrent.atomic.AtomicLong execUs = new java.util.concurrent.atomic.AtomicLong();
                    Result r = despotes.platform().awaitOnClientThread(() -> {
                        long start = System.nanoTime();
                        ActionContext ctx = new ActionContext(despotes, rid, transportId, transportId);
                        Result res = Actions.execute(ctx, c);
                        execUs.set((System.nanoTime() - start) / 1000);
                        return res;
                    }, waitMs);
                    if (r == null) {
                        results.add(JsonParser.parseString(Json.error(rid, ProtocolError.timeout("query timed out"))));
                    } else {
                        despotes.latency().record(0, execUs.get());
                        results.add(JsonParser.parseString(r.toJsonString(rid, 0, execUs.get())));
                    }
                } else {
                    String resp = despotes.dispatcher().submit(
                            new dev.despotes.common.dispatcher.Dispatcher.Command(c, rid, transportId, transportId),
                            waitMs);
                    results.add(JsonParser.parseString(resp));
                }
            } catch (ProtocolError e) {
                results.add(JsonParser.parseString(Json.error(rid, e)));
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("batch", arr.size());
        out.add("results", results);
        return Json.ok(requestId, out);
    }

    private static boolean isQuery(String type) {
        return type.equals("status") || type.equals("screen") || type.equals("pending")
                || type.equals("query") || type.equals("recipe") || type.equals("recipes")
                || type.equals("inventory") || type.equals("self") || type.equals("threats")
                || type.equals("world") || type.equals("blocks") || type.equals("entities")
                || type.equals("target") || type.equals("container");
    }
}
