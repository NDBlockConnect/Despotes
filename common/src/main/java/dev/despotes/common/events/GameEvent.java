package dev.despotes.common.events;

import com.google.gson.JsonObject;

/**
 * A single captured game event. {@code seq} is a strictly increasing id assigned by
 * {@link EventBus}; {@code timestampMs} is the wall-clock capture time.
 */
public record GameEvent(long seq, String type, long timestampMs, JsonObject payload) {
}
