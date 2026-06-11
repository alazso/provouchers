package so.alaz.provouchers.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpiryTest {

    private static final Instant NOW = Instant.parse("2026-06-05T00:00:00Z");

    @Test
    void blankOrNullNeverExpires() {
        assertThat(Expiry.resolve(null, NOW)).isNull();
        assertThat(Expiry.resolve("", NOW)).isNull();
        assertThat(Expiry.resolve("   ", NOW)).isNull();
    }

    @Test
    void parsesIsoInstant() {
        Instant expiry = Expiry.resolve("2026-12-31T23:59:59Z", NOW);
        assertThat(expiry).isEqualTo(Instant.parse("2026-12-31T23:59:59Z"));
    }

    @Test
    void parsesRelativeDurations() {
        assertThat(Expiry.resolve("30d", NOW)).isEqualTo(NOW.plusSeconds(30L * 86_400));
        assertThat(Expiry.resolve("12h", NOW)).isEqualTo(NOW.plusSeconds(12L * 3_600));
        assertThat(Expiry.resolve("90m", NOW)).isEqualTo(NOW.plusSeconds(90L * 60));
        assertThat(Expiry.resolve("45s", NOW)).isEqualTo(NOW.plusSeconds(45));
    }

    @Test
    void parsesPlainDateAsEndOfDay() {
        Instant expiry = Expiry.resolve("2026-06-10", NOW);
        Instant expected = LocalDate.parse("2026-06-10").plusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant();
        assertThat(expiry).isEqualTo(expected);
        // Usable through June 10, expired once it ticks into June 11.
        assertThat(Expiry.isExpired(expiry,
            LocalDate.parse("2026-06-10").atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant())).isFalse();
    }

    @Test
    void parsesLocalDateTime() {
        Instant expiry = Expiry.resolve("2026-06-10T15:30:00", NOW);
        assertThat(expiry).isEqualTo(
            LocalDateTime.parse("2026-06-10T15:30:00").atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> Expiry.resolve("not-a-date", NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void relativeExpiryIsAnchoredToTheReferenceInstant() {
        // Mirrors how RedeemHandler resolves "30d" against the item's give time
        // rather than the redeem moment, so the voucher can actually expire.
        Instant giveTime = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiry = Expiry.resolve("30d", giveTime);
        assertThat(expiry).isEqualTo(giveTime.plus(Duration.ofDays(30)));
        assertThat(Expiry.isExpired(expiry, giveTime.plus(Duration.ofDays(31)))).isTrue();
        assertThat(Expiry.isExpired(expiry, giveTime.plus(Duration.ofDays(29)))).isFalse();
    }

    @Test
    void isRelativeOnlyForDurations() {
        // Only a relative expiry needs the give-time anchor; blank and absolute do not, so they
        // must not stamp it (which would stop a stackable voucher from stacking across gives).
        assertThat(Expiry.isRelative("30d")).isTrue();
        assertThat(Expiry.isRelative(" 12h ")).isTrue();
        assertThat(Expiry.isRelative(null)).isFalse();
        assertThat(Expiry.isRelative("")).isFalse();
        assertThat(Expiry.isRelative("2026-12-31T23:59:59Z")).isFalse();
        assertThat(Expiry.isRelative("2026-12-31")).isFalse();
    }

    @Test
    void isExpiredComparesAgainstNow() {
        Instant past = NOW.minusSeconds(1);
        Instant future = NOW.plusSeconds(1);
        assertThat(Expiry.isExpired(past, NOW)).isTrue();
        assertThat(Expiry.isExpired(future, NOW)).isFalse();
        assertThat(Expiry.isExpired(null, NOW)).isFalse();
    }
}
