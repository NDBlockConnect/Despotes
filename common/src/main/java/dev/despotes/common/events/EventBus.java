package dev.despotes.common.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory ring buffer of captured game events (chat messages, system notices).
 *
 * <p>Producers call {@link #publish(String, JsonObject)} from the client thread;
 * consumers poll via {@code GET /despotes/v1/events?since=<seq>} with {@link #since(long)}.
 * Thread-safe; the buffer is bounded (default 500 entries, oldest evicted first).
 */
public final class EventBus {

    private static final int CAPACITY = 500;

    private final ArrayDeque<GameEvent> buffer = new ArrayDeque<>();
    private long nextSeq = 1;

    /** Publish an event; returns the assigned sequence number. */
    public synchronized long publish(String type, JsonObject payload) {
        long seq = nextSeq++;
        buffer.addLast(new GameEvent(seq, type, System.currentTimeMillis(), payload));
        while (buffer.size() > CAPACITY) {
            buffer.removeFirst();
        }
        return seq;
    }

    /** All events with seq strictly greater than {@code since}, oldest first. */
    public synchronized List<GameEvent> since(long since) {
        List<GameEvent> out = new ArrayList<>();
        for (GameEvent e : buffer) {
            if (e.seq() > since) {
                out.add(e);
            }
        }
        return out;
    }

    /** Highest sequence number assigned so far (0 when empty). */
    public synchronized long lastSeq() {
        return nextSeq - 1;
    }

    public static JsonObject toJson(GameEvent e) {
        JsonObject o = new JsonObject();
        o.addProperty("seq", e.seq());
        o.addProperty("type", e.type());
        o.addProperty("timestampMs", e.timestampMs());
        o.add("payload", e.payload() == null ? new JsonObject() : e.payload());
        return o;
    }

    public static JsonArray toJsonArray(List<GameEvent> events) {
        JsonArray arr = new JsonArray();
        for (GameEvent e : events) {
            arr.add(toJson(e));
        }
        return arr;
    }
}
