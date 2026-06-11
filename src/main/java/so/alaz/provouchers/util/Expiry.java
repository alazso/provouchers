package so.alaz.provouchers.util;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Parses the optional {@code expiry} field of a voucher. An empty or blank value
 * means "never expires"; otherwise the value is a relative duration ({@code 30d},
 * {@code 12h}, {@code 90m}), a plain date ({@code 2026-12-31}, which expires at the
 * end of that day in the server's time zone), a local date-time
 * ({@code 2026-12-31T23:59:59}), or a full ISO-8601 instant ({@code 2026-12-31T23:59:59Z}).
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
        Duration relative = Durations.parseOrNull(value);
        if (relative != null) {
            return now.plus(relative);
        }
        Instant absolute = parseAbsolute(value);
        if (absolute != null) {
            return absolute;
        }
        throw new IllegalArgumentException("Invalid expiry '" + raw + "': expected a date (2026-12-31), a "
            + "date-time (2026-12-31T23:59:59), an ISO-8601 instant (2026-12-31T23:59:59Z), or a relative "
            + "duration such as 30d, 12h, or 90m");
    }

    /** Parses an absolute moment: a full instant, a local date-time, or a plain date (end of day). */
    @Nullable
    private static Instant parseAbsolute(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // Not a full instant; try a zone-less date-time next.
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
            // Not a date-time; try a plain calendar date next.
        }
        try {
            // A plain date expires at the end of that day (the start of the next) in the server's zone.
            return LocalDate.parse(value).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /** Returns {@code true} if {@code expiry} is non-null and not after {@code now}. */
    public static boolean isExpired(@Nullable Instant expiry, Instant now) {
        return expiry != null && !expiry.isAfter(now);
    }

    /**
     * Whether {@code raw} is a relative duration (such as {@code 30d}), as opposed to blank or an
     * absolute instant. Only a relative expiry needs the per-item give time as its anchor, so this
     * is what decides whether to stamp that time (stamping it otherwise stops stackable vouchers
     * from stacking across separate gives).
     */
    public static boolean isRelative(@Nullable String raw) {
        return raw != null && !raw.isBlank() && Durations.parseOrNull(raw.trim()) != null;
    }
}
