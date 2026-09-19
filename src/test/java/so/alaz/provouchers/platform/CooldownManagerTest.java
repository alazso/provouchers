package so.alaz.provouchers.platform;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CooldownManagerTest {

    // Hour-scale margins make the assertions independent of the test's run time.
    private static final long HOUR = Duration.ofHours(1).toMillis();

    private final CooldownManager manager = new CooldownManager();

    @Test
    void extendKeepsTheLaterExpiry() {
        manager.setMillis("daily", 2 * HOUR);
        manager.extendMillis("daily", HOUR);
        assertThat(manager.remaining("daily")).isGreaterThan(Duration.ofMinutes(90));
        manager.extendMillis("daily", 4 * HOUR);
        assertThat(manager.remaining("daily")).isGreaterThan(Duration.ofHours(3));
    }

    @Test
    void extendStartsACooldownWhenNoneIsRunning() {
        manager.extendMillis("daily", HOUR);
        assertThat(manager.isOnCooldown("daily")).isTrue();
    }

    @Test
    void extendIgnoresANonPositiveLength() {
        manager.setMillis("daily", HOUR);
        manager.extendMillis("daily", -HOUR);
        manager.extendMillis("weekly", 0);
        assertThat(manager.isOnCooldown("daily")).isTrue();
        assertThat(manager.isOnCooldown("weekly")).isFalse();
    }
}
