package so.alaz.provouchers.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

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
    void rejectsGarbage() {
        assertThatThrownBy(() -> Expiry.resolve("not-a-date", NOW))
            .isInstanceOf(IllegalArgumentException.class);
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
