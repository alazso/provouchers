package so.alaz.provouchers.reward;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The parsed payload of a {@code discord} reward: a webhook target and the message to post. The
 * target is either a Discord webhook URL or an {@code @name} reference into the configured
 * {@code webhooks} map. The message keeps its placeholders and MiniMessage; they are resolved (and
 * the formatting flattened to plain text) when the reward runs.
 */
public record DiscordRewardPayload(String target, String message) {

    /** A Discord webhook URL, allowing the canary/ptb hosts, the legacy discordapp.com, and an API version. */
    private static final Pattern WEBHOOK_URL = Pattern.compile(
        "https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api(?:/v\\d+)?/webhooks/\\d+/\\S+");

    public DiscordRewardPayload {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("discord reward is missing a webhook (a URL or @name)");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("discord reward is missing a message");
        }
    }

    /** Parses {@code "<webhook-url-or-@name> <message>"}, validating an inline URL as a Discord webhook. */
    public static DiscordRewardPayload parse(String payload) {
        String[] parts = payload.trim().split("\\s+", 2);
        if (parts.length < 2) {
            throw new IllegalArgumentException("discord reward needs a webhook and a message");
        }
        String target = parts[0];
        if (!target.startsWith("@") && !WEBHOOK_URL.matcher(target).matches()) {
            throw new IllegalArgumentException(
                "'" + target + "' is not a Discord webhook URL or an @name from the webhooks config");
        }
        return new DiscordRewardPayload(target, parts[1]);
    }

    /** Whether the target is an {@code @name} reference into the configured webhooks. */
    public boolean isNamedRef() {
        return target.startsWith("@");
    }

    /** The webhook name behind an {@code @name} reference, lower-cased for map lookup. */
    public String namedRef() {
        return target.substring(1).toLowerCase(Locale.ROOT);
    }
}
