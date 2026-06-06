package so.alaz.provouchers.util;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Parses short relative durations such as {@code 30d}, {@code 12h}, {@code 90m},
 * and {@code 45s} (a number followed by a unit of seconds, minutes, hours, or
 * days). Shared by voucher expiry and temporary group rewards.
 */
public final class Durations {

    private Durations() {
    }

    /**
     * Parses a duration, returning {@code null} if {@code value} is not a valid
     * relative duration (so callers can fall through to another format).
     */
    @Nullable
    public static Duration parseOrNull(@Nullable String value) {
        if (value == null || value.length() < 2) {
            return null;
        }
        char unit = Character.toLowerCase(value.charAt(value.length() - 1));
        long amount;
        try {
            amount = Long.parseLong(value.substring(0, value.length() - 1));
        } catch (NumberFormatException ex) {
            return null;
        }
        return switch (unit) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> null;
        };
    }

    /** Parses a duration, throwing when {@code value} is not a valid relative duration. */
    public static Duration parse(String value) {
        Duration duration = parseOrNull(value);
        if (duration == null) {
            throw new IllegalArgumentException("invalid duration '" + value
                + "': expected a number followed by s, m, h, or d (for example 7d)");
        }
        return duration;
    }
}
