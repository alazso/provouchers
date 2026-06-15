package so.alaz.provouchers.reward;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A configured Discord webhook: its URL and an optional JSON {@code payload} template. When the
 * template is present, a {@code discord: @name} reward renders it (resolving placeholders) and posts
 * it; otherwise the reward must supply a message that is posted as the webhook content.
 *
 * @param url     the webhook URL
 * @param payload the payload template (a nested map mirroring the Discord webhook JSON), or
 *                {@code null} for a content-only webhook
 */
public record WebhookSpec(String url, @Nullable Map<String, Object> payload) {

    public WebhookSpec {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("webhook needs a url");
        }
    }
}
