package dev.despotes.common.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.despotes.common.config.DespotesConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal OpenAI-compatible chat-completions client (Alpha.7 AI feature).
 *
 * <p>The endpoint config ({@code ai.endpoint}) must be a full chat-completions URL, e.g.
 * {@code https://api.example.com/v1/chat/completions}. The API key is sent as a
 * {@code Authorization: Bearer} header. When unset/unreachable the {@code ai} action
 * returns a clear error so the feature degrades gracefully.
 */
public final class AiClient {

    private AiClient() {
    }

    /** Sends one chat-completions request; returns the assistant text. */
    public static String chat(DespotesConfig.Ai ai, String system, String user) throws Exception {
        if (ai.endpoint == null || ai.endpoint.isBlank()) {
            throw new IllegalStateException("ai.endpoint not configured");
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", ai.model);
        JsonArray msgs = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", system);
        msgs.add(sys);
        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", user);
        msgs.add(usr);
        body.add("messages", msgs);
        body.addProperty("temperature", 0.2);

        HttpRequest.Builder hb = HttpRequest.newBuilder(URI.create(ai.endpoint))
                .timeout(Duration.ofMillis(ai.timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (ai.apiKey != null && !ai.apiKey.isBlank()) {
            hb.header("Authorization", "Bearer " + ai.apiKey);
        }
        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(hb.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("LLM endpoint returned HTTP " + resp.statusCode());
        }
        JsonObject out = JsonParser.parseString(resp.body()).getAsJsonObject();
        return out.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }
}
