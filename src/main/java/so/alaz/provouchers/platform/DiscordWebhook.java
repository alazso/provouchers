package so.alaz.provouchers.platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Posts messages to Discord webhooks over HTTP. One shared, non-blocking client serves the whole
 * plugin; a failure is reported to a callback rather than thrown, so it never aborts a redemption or
 * stalls the calling thread. The client (and its executor) is closed on plugin disable.
 */
public final class DiscordWebhook {

    /** Discord caps a webhook message at 2000 characters. */
    private static final int MAX_CONTENT = 2000;

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /** Posts a plain message to {@code url} as the webhook {@code content}, asynchronously. */
    public void post(String url, String content, Consumer<String> onError) {
        postBody(url, "{\"content\":\"" + escape(truncate(content)) + "\"}", onError);
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
        return content.length() > MAX_CONTENT ? content.substring(0, MAX_CONTENT) : content;
    }

    /** Escapes a string for a JSON value (the webhook {@code content} field). */
    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
