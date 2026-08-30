package dev.despotes.common.transport;

import com.google.gson.JsonObject;
import dev.despotes.common.Despotes;
import dev.despotes.common.protocol.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * File-drop control source: consumes {@code <dir>/*.json} files in filename order on a
 * worker thread. Results are written next to the input as {@code <name>.result.json}.
 * Input files are deleted only when {@code fileDrop.deleteAfter} is true.
 */
public final class FileDropTransport implements ControlTransport {

    private Thread thread;
    private volatile boolean running;
    /** Fingerprint (size|lastModified) of already-consumed files, to avoid replaying them. */
    private final java.util.Map<String, String> consumed = new java.util.HashMap<>();

    @Override
    public String id() {
        return "filedrop";
    }

    @Override
    public void start(Despotes despotes) {
        SecurityGate gate = new SecurityGate(despotes);
        Path dir = despotes.platform().gameDir().resolve(despotes.config().fileDrop.dir);
        running = true;
        thread = new Thread(() -> {
            try {
                Files.createDirectories(dir);
            } catch (Exception e) {
                despotes.platform().log("[Despotes] filedrop dir create failed: " + e.getMessage());
            }
            while (running) {
                try {
                    int max = Math.max(1, despotes.config().fileDrop.maxPerTick);
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
                    List<Path> files = listJson(dir);
                    int processed = 0;
                    for (Path f : files) {
                        if (!running || processed >= max) {
                            break;
                        }
                        String key = f.getFileName().toString();
                        String sig = sig(f);
                        String seen = consumed.get(key);
                        if (seen != null && seen.equals(sig)) {
                            continue; // already consumed; re-consume only if the file changes
                        }
                        processOne(despotes, gate, f);
                        consumed.put(key, sig);
                        processed++;
                    }
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    despotes.platform().log("[Despotes] filedrop loop error: " + e.getMessage());
                }
            }
        }, "Despotes-FileDrop");
        thread.setDaemon(true);
        thread.start();
        despotes.platform().log("[Despotes] FileDrop transport watching " + dir);
    }

    private List<Path> listJson(Path dir) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (name.endsWith(".result.json")) {
                    continue;
                }
                out.add(p);
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out);
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        return out;
    }

    private static String sig(Path f) {
        try {
            return Files.size(f) + "|" + Files.getLastModifiedTime(f).toMillis();
        } catch (Exception e) {
            return "";
        }
    }

    private void processOne(Despotes despotes, SecurityGate gate, Path f) {
        String resp;
        try {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            JsonObject cmd = Json.parseCommand(text);
            if (cmd.has("batch") && cmd.get("batch").isJsonArray()) {
                resp = gate.routeBatch("filedrop", cmd, null);
            } else {
                resp = gate.route("filedrop", cmd, null);
            }
        } catch (Exception e) {
            resp = Json.error(null,
                    new dev.despotes.common.protocol.ProtocolError(
                            dev.despotes.common.protocol.ProtocolError.Code.BAD_REQUEST,
                            String.valueOf(e.getMessage())));
        }
        try {
            String name = f.getFileName().toString();
            String base = name.substring(0, name.length() - 5);
            Path result = f.resolveSibling(base + ".result.json");
            Files.writeString(result, resp + System.lineSeparator(), StandardCharsets.UTF_8);
            if (despotes.config().fileDrop.deleteAfter) {
                Files.deleteIfExists(f);
            }
        } catch (Exception e) {
            despotes.platform().log("[Despotes] filedrop result write failed: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        }
    }
}
