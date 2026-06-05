package so.alaz.provouchers.util;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Parses the optional {@code expiry} field of a voucher. An empty or blank value
 * means "never expires"; otherwise the value is an ISO-8601 instant
 * (for example {@code 2026-12-31T23:59:59Z}) or a relative duration
 * (for example {@code 30d}, {@code 12h}, {@code 90m}).
 */
public final class Expiry {

    private Expiry() {
    }

    /**
     * Resolves the configured expiry into an absolute instant, relative to {@code now}.
     *
     * @return the expiry instant, or {@code null} when the voucher never expires
     */
    @Nullable
    public static Instant resolve(@Nullable String raw, Instant now) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        Duration relative = tryParseRelative(value);
        if (relative != null) {
            return now.plus(relative);
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid expiry '" + raw + "': expected ISO-8601 or a "
                + "relative duration such as 30d, 12h, or 90m", ex);
        }
    }

    /** Returns {@code true} if {@code expiry} is non-null and not after {@code now}. */
    public static boolean isExpired(@Nullable Instant expiry, Instant now) {
        return expiry != null && !expiry.isAfter(now);
    }

    @Nullable
    private static Duration tryParseRelative(String value) {
        int splitAt = value.length() - 1;
        if (splitAt <= 0) {
            return null;
        }
        char unit = Character.toLowerCase(value.charAt(splitAt));
        String number = value.substring(0, splitAt);
        long amount;
        try {
            amount = Long.parseLong(number);
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
}
