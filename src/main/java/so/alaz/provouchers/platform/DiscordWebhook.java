package so.alaz.provouchers.platform;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Posts messages to Discord webhooks over HTTP. One shared, non-blocking client serves the whole
 * plugin; a failure is reported to a callback rather than thrown, so it never aborts a redemption or
 * stalls the calling thread. The client (and its executor) is closed on plugin disable.
 */
public final class DiscordWebhook {

    /** Discord caps a webhook message at 2000 characters. */
    private static final int MAX_CONTENT = 2000;

    private static final Gson GSON = new Gson();

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /** Posts a plain message to {@code url} as the webhook {@code content}, asynchronously. */
    public void post(String url, String content, Consumer<String> onError) {
        postBody(url, GSON.toJson(Map.of("content", truncate(content))), onError);
    }

    /** Posts a ready JSON {@code body} to {@code url} asynchronously; {@code onError} gets a reason on failure. */
    public void postBody(String url, String body, Consumer<String> onError) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        } catch (IllegalArgumentException ex) {
            onError.accept("invalid webhook URL");
            return;
        }
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenAccept(response -> {
                if (response.statusCode() >= 300) {
                    onError.accept("Discord returned HTTP " + response.statusCode());
                }
            })
            .exceptionally(ex -> {
                onError.accept(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                return null;
            });
    }

    /** Closes the client and its executor; call on plugin disable. */
    public void close() {
        client.close();
    }

    private static String truncate(String content) {
        if (content.length() <= MAX_CONTENT) {
            return content;
        }
        // Back off by one if the cut would land between a surrogate pair, leaving a lone surrogate.
        int end = Character.isHighSurrogate(content.charAt(MAX_CONTENT - 1)) ? MAX_CONTENT - 1 : MAX_CONTENT;
        return content.substring(0, end);
    }
}
