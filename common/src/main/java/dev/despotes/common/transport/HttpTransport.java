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

    private String peerToken(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("X-Despotes-Token");
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
