package dev.despotes.common.transport;

import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import dev.despotes.common.protocol.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * v26.8: WebSocket transport for real-time bidirectional control.
 *
 * <p>Implements a minimal RFC 6455 WebSocket server on a dedicated thread. Each connected
 * client receives a stream of JSON event lines (one per frame); commands are received as
 * text frames containing JSON command objects, routed through the same {@link SecurityGate}
 * as HTTP/CLI.
 *
 * <p>The server only accepts loopback connections (or {@code security.allowSources}).
 * Token/API-key validation is handled by {@link SecurityGate}.
 */
public final class WsTransport implements ControlTransport {

    private Thread acceptThread;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private final ConcurrentLinkedQueue<Socket> clients = new ConcurrentLinkedQueue<>();

    @Override
    public String id() {
        return "ws";
    }

    @Override
    public void start(Despotes despotes) {
        int port = despotes.config().http.port + 1; // default 25586
        SecurityGate gate = new SecurityGate(despotes);
        running = true;
        acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.bind(new InetSocketAddress(port));
                despotes.platform().log("[Despotes] WebSocket transport listening on ws://127.0.0.1:" + port);
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        // Security check
                        InetSocketAddress remote = (InetSocketAddress) client.getRemoteSocketAddress();
                        try {
                            gate.checkPeer(remote, null);
                        } catch (Exception e) {
                            client.close();
                            continue;
                        }
                        clients.add(client);
                        Thread t = new Thread(() -> handleClient(despotes, gate, client), "Despotes-WS-Client");
                        t.setDaemon(true);
                        t.start();
                    } catch (IOException e) {
                        if (running) {
                            despotes.platform().log("[Despotes] WS accept error: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                despotes.platform().log("[Despotes] WS transport failed: " + e.getMessage());
            }
        }, "Despotes-WS-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void handleClient(Despotes despotes, SecurityGate gate, Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // --- WebSocket Handshake ---
            byte[] buf = new byte[8192];
            int n = in.read(buf);
            if (n <= 0) { socket.close(); return; }
            String request = new String(buf, 0, n, StandardCharsets.UTF_8);

            // Check for WebSocket upgrade
            if (!request.contains("Upgrade: websocket") && !request.contains("Upgrade: WebSocket")) {
                out.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                socket.close();
                return;
            }

            // Extract Sec-WebSocket-Key
            String key = null;
            for (String line : request.split("\r\n")) {
                if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                    key = line.substring(line.indexOf(':') + 1).trim();
                    break;
                }
            }
            if (key == null) { socket.close(); return; }

            // Compute Sec-WebSocket-Accept
            String accept = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest(
                            (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8)));

            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // --- WebSocket frame loop ---
            while (running && !socket.isClosed()) {
                String msg = readFrame(in);
                if (msg == null) break;

                // Route command
                try {
                    JsonObject cmd = Json.parseCommand(msg);
                    String resp;
                    if (cmd.has("batch") && cmd.get("batch").isJsonArray()) {
                        resp = gate.routeBatch("ws", cmd, (InetSocketAddress) socket.getRemoteSocketAddress());
                    } else {
                        resp = gate.route("ws", cmd, (InetSocketAddress) socket.getRemoteSocketAddress());
                    }
                    sendFrame(out, resp);
                } catch (Exception e) {
                    sendFrame(out, Json.error(null,
                            new dev.despotes.common.protocol.ProtocolError(
                                    dev.despotes.common.protocol.ProtocolError.Code.BAD_REQUEST,
                                    String.valueOf(e.getMessage()))));
                }
            }
        } catch (Exception e) {
            // Client disconnected
        } finally {
            clients.remove(socket);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    /** Read a single WebSocket text frame and return its payload as UTF-8 string. */
    private String readFrame(InputStream in) throws IOException {
        int b0 = in.read();
        if (b0 < 0) return null;
        int b1 = in.read();
        if (b1 < 0) return null;

        boolean masked = (b1 & 0x80) != 0;
        int payloadLen = b1 & 0x7F;

        if (payloadLen == 126) {
            int hi = in.read(); int lo = in.read();
            payloadLen = (hi << 8) | lo;
        } else if (payloadLen == 127) {
            byte[] ext = new byte[8];
            int read = 0;
            while (read < 8) { int r = in.read(ext, read, 8 - read); if (r < 0) return null; read += r; }
            payloadLen = 0;
            for (int i = 0; i < 8; i++) payloadLen = (payloadLen << 8) | (ext[i] & 0xFF);
        }

        byte[] mask = null;
        if (masked) {
            mask = new byte[4];
            int read = 0;
            while (read < 4) { int r = in.read(mask, read, 4 - read); if (r < 0) return null; read += r; }
        }

        byte[] payload = new byte[payloadLen];
        int read = 0;
        while (read < payloadLen) { int r = in.read(payload, read, payloadLen - read); if (r < 0) return null; read += r; }

        if (masked) {
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
        }

        // Check opcode: 0x1 = text, 0x8 = close
        int opcode = b0 & 0x0F;
        if (opcode == 0x8) return null; // close frame
        return new String(payload, StandardCharsets.UTF_8);
    }

    /** Send a text frame. */
    private void sendFrame(OutputStream out, String message) throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        int len = data.length;
        out.write(0x81); // FIN + text frame
        if (len <= 125) {
            out.write(len);
        } else if (len <= 65535) {
            out.write(126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(127);
            byte[] ext = new byte[8];
            long l = len;
            for (int i = 7; i >= 0; i--) { ext[i] = (byte)(l & 0xFF); l >>= 8; }
            out.write(ext);
        }
        out.write(data);
        out.flush();
    }

    @Override
    public void stop() {
        running = false;
        for (Socket s : clients) { try { s.close(); } catch (IOException ignored) {} }
        clients.clear();
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }
}
