package dev.despotes.common.transport;

import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import dev.despotes.common.protocol.Json;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * stdin control source: one JSON command per line on the game process stdin; responses are
 * single JSON lines on stdout. Only works when the process actually has an attached
 * console/pipe (e.g. launched from a terminal or an agent launcher).
 */
public final class CliTransport implements ControlTransport {

    private Thread thread;
    private volatile boolean running;

    @Override
    public String id() {
        return "cli";
    }

    @Override
    public void start(Despotes despotes) {
        SecurityGate gate = new SecurityGate(despotes);
        running = true;
        thread = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
                String line;
                while (running && (line = in.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    String resp;
                    try {
                        JsonObject cmd = Json.parseCommand(trimmed);
                        if (cmd.has("batch") && cmd.get("batch").isJsonArray()) {
                            resp = gate.routeBatch("cli", cmd, null);
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
                        } else {
                            resp = gate.route("cli", cmd, null);
                        }
                    } catch (Exception e) {
                        resp = Json.error(null,
                                new dev.despotes.common.protocol.ProtocolError(
                                        dev.despotes.common.protocol.ProtocolError.Code.BAD_REQUEST,
                                        String.valueOf(e.getMessage())));
                    }
                    out.println(resp);
                    if (despotes.config().cli.echo) {
                        despotes.platform().log("[Despotes][CLI] " + trimmed);
                    }
                }
            } catch (Exception e) {
                despotes.platform().log("[Despotes] CLI transport stopped: " + e.getMessage());
            }
        }, "Despotes-CLI");
        thread.setDaemon(true);
        thread.start();
        despotes.platform().log("[Despotes] CLI transport attached to stdin.");
    }

    @Override
    public void stop() {
        running = false;
    }
}
