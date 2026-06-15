package so.alaz.provouchers.reward;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The parsed payload of a {@code discord} reward: a webhook target and an optional message. The
 * target is either a Discord webhook URL or an {@code @name} reference into the configured
 * {@code discord-webhooks} map. An inline URL must be followed by a message (posted as the webhook
 * content); an {@code @name} may omit the message, in which case the named webhook's own payload
 * template is used. The message keeps its placeholders and MiniMessage; they resolve when the reward
 * runs.
 */
public record DiscordRewardPayload(String target, @Nullable String message) {

    /** A Discord webhook URL, allowing the canary/ptb hosts, the legacy discordapp.com, and an API version. */
    private static final Pattern WEBHOOK_URL = Pattern.compile(
        "https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api(?:/v\\d+)?/webhooks/\\d+/\\S+");

    public DiscordRewardPayload {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("discord reward is missing a webhook (a URL or @name)");
        }
    }

    /**
     * Parses {@code "<webhook-url-or-@name> [message]"}. An inline URL is validated as a Discord
     * webhook and requires a message; an {@code @name} may stand alone.
     */
    public static DiscordRewardPayload parse(String payload) {
        String[] parts = payload.trim().split("\\s+", 2);
        String target = parts[0];
        String message = parts.length > 1 ? parts[1] : null;
        if (target.startsWith("@")) {
            return new DiscordRewardPayload(target, message);
        }
        if (!WEBHOOK_URL.matcher(target).matches()) {
            throw new IllegalArgumentException(
                "'" + target + "' is not a Discord webhook URL or an @name from the discord-webhooks config");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("discord reward needs a message after the webhook URL");
        }
        return new DiscordRewardPayload(target, message);
    }

    /** Whether the target is an {@code @name} reference into the configured webhooks. */
    public boolean isNamedRef() {
        return target.startsWith("@");
    }

    /** The webhook name behind an {@code @name} reference, lower-cased for map lookup. */
    public String namedRef() {
        return target.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Whether an inline message was given (for a content post). */
    public boolean hasMessage() {
        return message != null && !message.isBlank();
    }
}
