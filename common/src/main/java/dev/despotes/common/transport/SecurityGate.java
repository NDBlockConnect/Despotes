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
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
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
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
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

            // v26.12-Alpha.1: control-flow steps interleave with actions in a batch.
            if (c.has("step")) {
                String step = Json.normalize(Json.getStr(c, "step", ""));
                switch (step) {
                    case "wait" -> {
                        long ms = Math.min(30000, Math.max(0, Json.getLong(c, "ms", 0)));
                        try {
                            Thread.sleep(ms);
                        } catch (InterruptedException ignored) {
                        }
                        JsonObject w = new JsonObject();
                        w.addProperty("step", "wait");
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
                        w.addProperty("ms", ms);
                        w.addProperty("ok", true);
                        results.add(w);
                    }
                    case "condition" -> {
                        // Evaluate the condition, then splice the taken branch's commands
                        // into the remaining batch stream (flattened inline execution).
                        JsonArray branch = evaluateBranch(c);
                        JsonObject header = new JsonObject();
                        header.addProperty("step", "condition");
                        header.addProperty("matched", c.has("then") && branch != c.getAsJsonArray("else"));
                        header.addProperty("ok", true);
                        results.add(header);
                        if (branch != null) {
                            java.util.Deque<JsonArray> pending = new java.util.ArrayDeque<>();
                            pending.push(branch);
                            while (!pending.isEmpty()) {
                                JsonArray b = pending.pop();
                                for (var bel : b) {
                                    if (!bel.isJsonObject()) continue;
                                    JsonObject bc = bel.getAsJsonObject();
                                    if (bc.has("step") && Json.normalize(Json.getStr(bc, "step", "")).equals("condition")) {
                                        JsonArray sub = evaluateBranch(bc);
                                        if (sub != null) {
                                            pending.push(sub);
                                        }
                                        JsonObject h2 = new JsonObject();
                                        h2.addProperty("step", "condition");
                                        h2.addProperty("ok", true);
                                        results.add(h2);
                                        continue;
                                    }
                                    executeBatchElement(bc, requestId, transportId, remote, results, waitMs);
                                }
                            }
                        }
                    }
                    case "retry" -> {
                        JsonObject cmd = c.has("command") && c.get("command").isJsonObject()
                                ? c.getAsJsonObject("command") : null;
                        if (cmd == null) {
                            JsonObject bad = new JsonObject();
                            bad.addProperty("step", "retry");
                            bad.addProperty("ok", false);
                            bad.addProperty("error", "retry requires 'command'");
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
                            results.add(bad);
                            continue;
                        }
                        int attempts = Math.min(10, Math.max(1, Json.getInt(c, "attempts", 3)));
                        long intervalMs = Math.max(50, Json.getLong(c, "intervalMs", 250));
                        JsonObject last = null;
                        for (int a = 0; a < attempts; a++) {
                            if (a > 0) {
                                try {
                                    Thread.sleep(intervalMs);
                                } catch (InterruptedException ignored) {
                                }
                            }
                            last = executeBatchElement(cmd, requestId, transportId, remote, null, waitMs);
                            if (last != null && last.has("ok") && last.get("ok").getAsBoolean()) {
                                break;
                            }
                        }
                        JsonObject rj = new JsonObject();
                        rj.addProperty("step", "retry");
                        rj.addProperty("attempts", attempts);
                        if (last != null) {
                            rj.add("result", last);
                        }
                        results.add(rj);
                    }
                    default -> {
                        JsonObject bad = new JsonObject();
                        bad.addProperty("ok", false);
                        bad.addProperty("error", "unknown step: " + step);
                        results.add(bad);
                    }
                }
                continue;
            }

            executeBatchElement(c, requestId, transportId, remote, results, waitMs);
        }
        JsonObject out = new JsonObject();
        out.addProperty("batch", arr.size());
        out.add("results", results);
        return Json.ok(requestId, out);
    }

    /**
     * v26.12-Alpha.1: evaluate a condition step and return the branch to splice.
     * Reuses Actions.execute on the inner query and dot-path comparison identical
     * to the standalone {@code condition} action.
     */
    private JsonArray evaluateBranch(JsonObject c) {
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        JsonObject cond = c.has("if") && c.get("if").isJsonObject() ? c.getAsJsonObject("if") : null;
        if (cond == null) {
            return c.has("else") ? c.getAsJsonArray("else") : null;
        }
        boolean matched;
        try {
            String queryType = Json.normalize(Json.getStr(cond, "type", "status"));
            JsonObject qp = new JsonObject();
            qp.addProperty("type", queryType);
            // routeBatch runs on an HTTP worker; queries must hop to the client thread
            // just like ordinary batch queries do before reading Minecraft state.
            Result qr = despotes.platform().awaitOnClientThread(() -> {
                ActionContext qctx = new ActionContext(despotes, null, "batch", "batch");
                return Actions.execute(qctx, qp);
            }, despotes.config().http.screenshotTimeoutMs);
            if (qr == null) {
                matched = false;
                return c.has("else") ? c.getAsJsonArray("else") : null;
            }
            JsonObject state = com.google.gson.JsonParser.parseString(qr.toJsonString(null)).getAsJsonObject();
            String fieldPath = Json.getStr(cond, "field", "");
            com.google.gson.JsonElement fv = null;
            if (!fieldPath.isBlank()) {
                com.google.gson.JsonElement cur = state;
                for (String part : fieldPath.split("\\.")) {
                    if (cur != null && cur.isJsonObject() && cur.getAsJsonObject().has(part)) {
                        cur = cur.getAsJsonObject().get(part);
                    } else {
                        cur = null;
                        break;
                    }
                }
                fv = cur;
            }
            String op = Json.normalize(Json.getStr(cond, "op", "exists"));
            switch (op) {
                case "exists" -> matched = fv != null && !fv.isJsonNull();
                // v26.12: value belongs to the `if` object. Fall back to the
                // outer step for compatibility with the standalone condition action.
                case "eq" -> {
                    com.google.gson.JsonElement expected = cond.has("value") ? cond.get("value") : c.get("value");
                    matched = fv != null && expected != null && fv.equals(expected);
                }
                case "ne" -> {
                    com.google.gson.JsonElement expected = cond.has("value") ? cond.get("value") : c.get("value");
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
                    matched = fv == null || expected == null || !fv.equals(expected);
                }
                case "gt", "lt" -> {
                    if (fv == null || !fv.isJsonPrimitive()) {
                        matched = false;
                    } else {
                        double a = fv.getAsDouble();
                        double e = Json.getDouble(cond, "value", Json.getDouble(c, "value", 0));
                        matched = op.equals("gt") ? a > e : a < e;
                    }
                }
                case "contains" -> matched = fv != null && fv.isJsonPrimitive()
                        && fv.getAsString().toLowerCase().contains(
                                Json.getStr(cond, "value", Json.getStr(c, "value", "")).toLowerCase());
                default -> matched = false;
            }
        } catch (Throwable t) {
            matched = false;
        }
        return matched ? (c.has("then") ? c.getAsJsonArray("then") : null)
                : (c.has("else") ? c.getAsJsonArray("else") : null);
    }

    /**
     * Execute one batch element (query or action), appending the response to
     * {@code results} when non-null. Returns the parsed response object.
     */
    private JsonObject executeBatchElement(JsonObject c, String requestId, String transportId,
                                           InetSocketAddress remote, JsonArray results, long waitMs) {
        String rid = Json.getStr(c, "requestId", requestId);
        String type = Json.normalize(Json.getStr(c, "type", ""));
        JsonObject parsed;
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
                    parsed = JsonParser.parseString(Json.error(rid, ProtocolError.timeout("query timed out"))).getAsJsonObject();
                } else {
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
                    despotes.latency().record(0, execUs.get());
                    parsed = JsonParser.parseString(r.toJsonString(rid, 0, execUs.get())).getAsJsonObject();
                }
            } else {
                String resp = despotes.dispatcher().submit(
                        new dev.despotes.common.dispatcher.Dispatcher.Command(c, rid, transportId, transportId),
                        waitMs);
                parsed = JsonParser.parseString(resp).getAsJsonObject();
            }
        } catch (ProtocolError e) {
            parsed = JsonParser.parseString(Json.error(rid, e)).getAsJsonObject();
        }
        if (results != null) {
            results.add(parsed);
        }
        return parsed;
    }

    private static boolean isQuery(String type) {
        return type.equals("status") || type.equals("screen") || type.equals("pending")
                || type.equals("query") || type.equals("recipe") || type.equals("recipes")
                || type.equals("inventory") || type.equals("self") || type.equals("threats")
                || type.equals("world") || type.equals("blocks") || type.equals("entities")
                || type.equals("target") || type.equals("container")
                || type.equals("players") || type.equals("server") || type.equals("tablist")
                || type.equals("scoreboard") || type.equals("coords");
    }
}
