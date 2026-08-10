package dev.despotes.common.transport;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.despotes.common.Despotes;
import dev.despotes.common.action.ScreenshotOptions;
import dev.despotes.common.platform.ShotHandle;
import dev.despotes.common.protocol.Json;
import dev.despotes.common.protocol.ProtocolError;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** Built-in HTTP control source. Binds loopback by default. */
public final class HttpTransport implements ControlTransport {

    private HttpServer server;
    private Despotes despotes;
    private SecurityGate gate;

    @Override
    public String id() {
        return "http";
    }

    @Override
    public void start(Despotes despotes) {
        this.despotes = despotes;
        this.gate = new SecurityGate(despotes);
        String host = despotes.config().http.host;
        int port = resolvePort(despotes.config().http.port);
        int workers = Math.max(1, despotes.config().http.maxWorkers);
        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 16);
            server.setExecutor(Executors.newFixedThreadPool(workers, r -> {
                Thread t = new Thread(r, "Despotes-HTTP");
                t.setDaemon(true);
                return t;
            }));
            server.createContext("/despotes/v1/actions", this::handleActions);
            server.createContext("/despotes/v1/query", this::handleQuery);
            server.createContext("/despotes/v1/status", this::handleStatus);
            server.createContext("/despotes/v1/screenshot", this::handleScreenshot);
            server.createContext("/despotes/v1/oplog", this::handleOplog);
            server.createContext("/despotes/v1/cancel", this::handleCancel);
            server.createContext("/despotes/v1/config/reload", this::handleReload);
            server.createContext("/despotes/v1/assistant", this::handleAssistant);
            server.createContext("/despotes/v1/events", this::handleEvents);
            server.start();
            despotes.platform().log("[Despotes] HTTP transport listening on " + host + ":" + port);
        } catch (IOException e) {
            despotes.platform().log("[Despotes] HTTP transport failed to start: " + e.getMessage());
        }
    }

    private static int resolvePort(int configured) {
        String override = System.getProperty("despotes.port");
        if (override != null && !override.isBlank()) {
            try {
                return Integer.parseInt(override.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return configured;
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    /** Alpha.9: poll captured game events (chat/system) since a sequence number. */
    private void handleEvents(HttpExchange ex) {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, Json.error(null, ProtocolError.badRequest("GET required")));
            return;
        }
        try {
            String token = peerToken(ex);
            gate.checkPeer(ex.getRemoteAddress(), token);
            long since = 0;
            String q = ex.getRequestURI().getQuery();
            if (q != null) {
                for (String kv : q.split("&")) {
                    int i = kv.indexOf('=');
                    if (i > 0 && "since".equals(kv.substring(0, i))) {
                        try {
                            since = Long.parseLong(kv.substring(i + 1).trim());
                        } catch (NumberFormatException e) {
                            sendJson(ex, 400, Json.error(null,
                                    ProtocolError.badRequest("'since' must be a number")));
                            return;
                        }
                    }
                }
            }
            var events = despotes.eventBus().since(since);
            JsonObject result = new JsonObject();
            result.addProperty("lastSeq", despotes.eventBus().lastSeq());
            result.add("events", dev.despotes.common.events.EventBus.toJsonArray(events));
            sendJson(ex, 200, Json.ok("", result));
        } catch (ProtocolError e) {
            sendJson(ex, 403, Json.error(null, e));
        }
    }

    private void send(HttpExchange ex, int status, String contentType, byte[] body) {
        try {
            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.sendResponseHeaders(status, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        } catch (IOException ignored) {
        } finally {
            ex.close();
        }
    }

    private void sendJson(HttpExchange ex, int status, String json) {
        send(ex, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    /** Conversational assistant (Alpha.8): LLM may reply and/or execute actions. */
    private void handleAssistant(HttpExchange ex) {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, Json.error(null, new dev.despotes.common.protocol.ProtocolError(
                    dev.despotes.common.protocol.ProtocolError.Code.BAD_REQUEST, "POST required")));
            return;
        }
        String requestId = "";
        try {
            String token = peerToken(ex);
            JsonObject req = Json.parseCommand(readBody(ex));
            requestId = Json.getStr(req, "requestId", "");
            gate.checkPeer(ex.getRemoteAddress(), token);
            var cfg = despotes.config().ai;
            if (!cfg.enabled) {
                sendJson(ex, 200, Json.error(requestId,
                        dev.despotes.common.protocol.ProtocolError.forbidden("ai is disabled")));
                return;
            }
            String message = Json.getStr(req, "message", "");
            String world = despotes.platform().awaitOnClientThread(() -> {
                var p = despotes.platform();
                JsonObject o = p.probeWorld();
                if (p.player() != null) o.add("player", p.player().statusJson());
                return o.toString();
            }, 3000);
            String system = "You are Despotes, an assistant controlling a Minecraft player via "
                    + "JSON action commands. Reply in JSON: {\"reply\": \"text\", "
                    + "\"actions\": [action objects]}. Allowed actions: key/move/look/click/"
                    + "use/function/hotbar. Be brief.";
            String raw;
            try {
                raw = dev.despotes.common.ai.AiClient.chat(cfg, system,
                        "World state: " + world + " Message: " + message);
            } catch (Exception e) {
                sendJson(ex, 200, Json.error(requestId,
                        dev.despotes.common.protocol.ProtocolError.internal("AI failed: " + e.getMessage())));
                return;
            }
            JsonObject out = new JsonObject();
            JsonObject parsed;
            try {
                parsed = com.google.gson.JsonParser.parseString(raw.trim()).getAsJsonObject();
            } catch (Exception e) {
                parsed = new JsonObject();
                parsed.addProperty("reply", raw);
            }
            out.addProperty("reply", Json.getStr(parsed, "reply", ""));
            com.google.gson.JsonArray results = new com.google.gson.JsonArray();
            if (parsed.has("actions") && parsed.get("actions").isJsonArray()) {
                int n = 0;
                for (var el : parsed.getAsJsonArray("actions")) {
                    if (n++ >= Math.max(1, cfg.maxActions)) break;
                    if (!el.isJsonObject()) continue;
                    JsonObject a = el.getAsJsonObject();
                    String type = Json.normalize(Json.getStr(a, "type", ""));
                    if (!java.util.Set.of("key", "move", "look", "click", "use", "function", "hotbar")
                            .contains(type)) continue;
                    dev.despotes.common.protocol.Result r = dev.despotes.common.action.Actions.execute(
                            new dev.despotes.common.action.ActionContext(despotes, requestId, "assistant", "assistant"), a);
                    results.add(com.google.gson.JsonParser.parseString(r.toJsonString(requestId)));
                }
            }
            out.add("results", results);
            sendJson(ex, 200, Json.ok(requestId, out));
        } catch (dev.despotes.common.protocol.ProtocolError e) {
            sendJson(ex, 403, Json.error(requestId, e));
        } catch (Exception e) {
            sendJson(ex, 500, Json.error(requestId,
                    dev.despotes.common.protocol.ProtocolError.internal(String.valueOf(e.getMessage()))));
        }
    }

    private String peerToken(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("X-Despotes-Key");
        if (h == null) {
            h = ex.getRequestHeaders().getFirst("X-Despotes-Token");
        }
        return h;
    }

    private void handleActions(HttpExchange ex) {
        try {
            String body = readBody(ex);
            JsonObject cmd = Json.parseCommand(body);
            String token = peerToken(ex);
            if (token != null && !cmd.has("token")) {
                cmd.addProperty("token", token);
            }
            InetSocketAddress remote = ex.getRemoteAddress();
            String resp;
            if (cmd.has("batch") && cmd.get("batch").isJsonArray()) {
                resp = gate.routeBatch("http", cmd, remote);
            } else {
                resp = gate.route("http", cmd, remote);
            }
            sendJson(ex, 200, resp);
        } catch (ProtocolError e) {
            sendJson(ex, 400, Json.error(null, e));
        } catch (Exception e) {
            sendJson(ex, 500, Json.error(null, ProtocolError.internal(e.getMessage())));
        }
    }

    private void handleQuery(HttpExchange ex) {
        handleActions(ex);
    }

    private void handleStatus(HttpExchange ex) {
        try {
            String token = peerToken(ex);
            gate.checkPeer(ex.getRemoteAddress(), token);
            JsonObject status = despotes.platform().awaitOnClientThread(
                    () -> despotes.statusJson(), despotes.config().http.screenshotTimeoutMs);
            sendJson(ex, 200, status == null ? Json.error(null, ProtocolError.timeout("status timed out"))
                    : Json.ok("", status));
        } catch (ProtocolError e) {
            sendJson(ex, 403, Json.error(null, e));
        }
    }

    private void handleScreenshot(HttpExchange ex) {
        try {
            gate.checkPeer(ex.getRemoteAddress(), peerToken(ex));
            ScreenshotOptions opts = new ScreenshotOptions();
            java.util.concurrent.CompletableFuture<ShotHandle> future = new java.util.concurrent.CompletableFuture<>();
            despotes.platform().beginCapture(opts, future::complete);
            ShotHandle shot = future.get(despotes.config().http.screenshotTimeoutMs,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            if (shot == null) {
                sendJson(ex, 504, Json.error(null, ProtocolError.timeout("screenshot capture failed")));
                return;
            }
            String ct = shot.format().equals("jpg") ? "image/jpeg" : "image/png";
            send(ex, 200, ct, shot.encoded());
        } catch (ProtocolError e) {
            sendJson(ex, 403, Json.error(null, e));
        } catch (Exception e) {
            sendJson(ex, 504, Json.error(null, ProtocolError.timeout("screenshot timed out")));
        }
    }

    private void handleOplog(HttpExchange ex) {
        try {
            gate.checkPeer(ex.getRemoteAddress(), peerToken(ex));
            String q = ex.getRequestURI().getQuery();
            int limit = 50;
            if (q != null && q.startsWith("limit=")) {
                try {
                    limit = Integer.parseInt(q.substring(6));
                } catch (NumberFormatException ignored) {
                }
            }
            com.google.gson.JsonArray arr = despotes.opLog().recent(limit);
            sendJson(ex, 200, Json.ok("", arr));
        } catch (ProtocolError e) {
            sendJson(ex, 403, Json.error(null, e));
        }
    }

    private void handleCancel(HttpExchange ex) {
        // v26.0: acknowledges; queued cancellation granularity is best-effort.
        sendJson(ex, 200, Json.ok("", new JsonObject()));
    }

    private void handleReload(HttpExchange ex) {
        try {
            gate.checkPeer(ex.getRemoteAddress(), peerToken(ex));
            boolean ok = despotes.reloadConfig();
            JsonObject r = new JsonObject();
            r.addProperty("reloaded", ok);
            sendJson(ex, 200, Json.ok("", r));
        } catch (ProtocolError e) {
            sendJson(ex, 403, Json.error(null, e));
        }
    }
}
