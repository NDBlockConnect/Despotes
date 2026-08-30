package dev.despotes.common.viz;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.despotes.common.config.DespotesConfig;
import dev.despotes.common.protocol.Json;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;

/**
 * Visualization log: a bounded in-memory ring of recent operations (for the HTTP
 * {@code /oplog} endpoint and the overlay) plus an append-only JSONL file on disk.
 */
public final class OpLog {

    private final DespotesConfig config;
    private final ArrayDeque<OpEntry> ring = new ArrayDeque<>();
    private BufferedWriter writer;
    private Path file;

    public OpLog(DespotesConfig config) {
        this.config = config;
    }

    public synchronized void record(OpEntry entry) {
        int cap = Math.max(1, config.http.oplogLimit);
        ring.addLast(entry);
        while (ring.size() > cap) {
            ring.removeFirst();
        }
        if (!config.visualization.opLog) {
            return;
        }
        try {
            if (writer == null) {
                file = Paths.get(config.visualization.opLogFile);
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
            }
            writer.write(Json.stringify(entry.toJson()));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            // Never let logging break control; close and retry lazily next time.
            closeQuietly();
        }
    }

    public synchronized JsonArray recent(int limit) {
        JsonArray arr = new JsonArray();
        int skip = Math.max(0, ring.size() - limit);
        int i = 0;
        for (OpEntry e : ring) {
            if (i++ < skip) {
                continue;
            }
            arr.add(e.toJson());
        }
        return arr;
    }

    public synchronized java.util.List<String> recentLines(int limit) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int skip = Math.max(0, ring.size() - limit);
        int i = 0;
        for (OpEntry e : ring) {
            if (i++ < skip) {
                continue;
            }
            out.add(e.overlayLine());
        }
        return out;
    }

    public synchronized JsonObject stats() {
        JsonObject o = new JsonObject();
        o.addProperty("entries", ring.size());
        o.addProperty("file", file == null ? "" : file.toAbsolutePath().toString());
        return o;
    }

    public synchronized void close() {
        closeQuietly();
//GitHub@NDBlockConnect | BlockConnect@StarsailsClover
    }

    private void closeQuietly() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
    }
}
