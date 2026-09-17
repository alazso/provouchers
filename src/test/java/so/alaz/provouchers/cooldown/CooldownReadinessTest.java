package so.alaz.provouchers.cooldown;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CooldownReadinessTest {

    private final CooldownReadiness readiness = new CooldownReadiness();
    private final UUID player = UUID.randomUUID();

    @Test
    void playerIsNotReadyUntilALoadSucceeds() {
        assertThat(readiness.isReady(player)).isFalse();
        CooldownReadiness.Load load = readiness.begin(player);
        assertThat(load).isNotNull();
        assertThat(load.player()).isEqualTo(player);
        assertThat(readiness.isReady(player)).isFalse();                  // loading is not ready
        assertThat(readiness.succeed(load, () -> { })).isTrue();
        assertThat(readiness.isReady(player)).isTrue();
    }

    @Test
    void onlyOneLoadRunsAtATime() {
        CooldownReadiness.Load load = readiness.begin(player);
        assertThat(readiness.begin(player)).isNull();
        assertThat(readiness.isCurrent(load)).isTrue();
    }

    @Test
    void successMergesBeforeMarkingReady() {
        CooldownReadiness.Load load = readiness.begin(player);
        AtomicBoolean readyDuringMerge = new AtomicBoolean(true);
        readiness.succeed(load, () -> readyDuringMerge.set(readiness.isReady(player)));
        assertThat(readyDuringMerge).isFalse();                           // never ready with memory unmerged
        assertThat(readiness.isReady(player)).isTrue();
        assertThat(readiness.isCurrent(load)).isFalse();
    }

    @Test
    void readyPlayerStartsNoFurtherLoad() {
        readiness.succeed(readiness.begin(player), () -> { });
        assertThat(readiness.begin(player)).isNull();
    }

    @Test
    void failedAttemptsBackOffUntilTheAttemptsRunOut() {
        CooldownReadiness.Load load = readiness.begin(player);
        assertThat(readiness.fail(load)).isEqualTo(Duration.ofSeconds(2));
        assertThat(readiness.fail(load)).isEqualTo(Duration.ofSeconds(4));
        assertThat(readiness.fail(load)).isEqualTo(Duration.ofSeconds(8));
        assertThat(readiness.fail(load)).isEqualTo(Duration.ofSeconds(16));
        assertThat(readiness.fail(load)).isNull();                        // fifth failure: out of attempts
        assertThat(readiness.isCurrent(load)).isFalse();
        assertThat(readiness.isReady(player)).isFalse();                  // still closed after giving up

        // A later request (the next redeem) starts a fresh load with the full attempts again.
        CooldownReadiness.Load next = readiness.begin(player);
        assertThat(next).isNotNull();
        assertThat(readiness.fail(next)).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void retryDelayDoublesFromTheFirst() {
        assertThat(CooldownReadiness.retryDelay(1)).isEqualTo(CooldownReadiness.FIRST_RETRY_DELAY);
        assertThat(CooldownReadiness.retryDelay(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(CooldownReadiness.retryDelay(4)).isEqualTo(Duration.ofSeconds(16));
    }

    @Test
    void forgettingAPlayerStopsTheirRetries() {
        CooldownReadiness.Load load = readiness.begin(player);
        assertThat(readiness.fail(load)).isNotNull();                     // a retry is pending
        readiness.forget(player, () -> { });                              // the player quits

        assertThat(readiness.isCurrent(load)).isFalse();                  // the retry sees this and stops
        assertThat(readiness.fail(load)).isNull();
        AtomicBoolean merged = new AtomicBoolean();
        assertThat(readiness.succeed(load, () -> merged.set(true))).isFalse();
        assertThat(merged).isFalse();
        assertThat(readiness.isReady(player)).isFalse();
    }

    @Test
    void lateResultFromBeforeAQuitIsDiscardedAfterRejoin() {
        CooldownReadiness.Load before = readiness.begin(player);
        readiness.forget(player, () -> { });                              // quit while the query runs
        CooldownReadiness.Load after = readiness.begin(player);           // rejoin starts a new load
        assertThat(after).isNotNull().isNotSameAs(before);

        AtomicBoolean staleMerged = new AtomicBoolean();
        assertThat(readiness.succeed(before, () -> staleMerged.set(true))).isFalse();
        assertThat(staleMerged).isFalse();
        assertThat(readiness.isReady(player)).isFalse();                  // only the new load can open it
        assertThat(readiness.fail(before)).isNull();                      // nor spend the new load's attempts
        assertThat(readiness.isCurrent(after)).isTrue();

        assertThat(readiness.succeed(after, () -> { })).isTrue();
        assertThat(readiness.isReady(player)).isTrue();
    }

    @Test
    void forgetClearsReadinessAndRunsTheClear() {
        readiness.succeed(readiness.begin(player), () -> { });
        AtomicBoolean cleared = new AtomicBoolean();
        readiness.forget(player, () -> cleared.set(true));
        assertThat(cleared).isTrue();
        assertThat(readiness.isReady(player)).isFalse();
        assertThat(readiness.begin(player)).isNotNull();                  // the next join loads again
    }

    @Test
    void playersAreTrackedSeparately() {
        UUID other = UUID.randomUUID();
        readiness.succeed(readiness.begin(player), () -> { });
        CooldownReadiness.Load otherLoad = readiness.begin(other);
        assertThat(readiness.isReady(other)).isFalse();

        readiness.forget(player, () -> { });
        assertThat(readiness.isCurrent(otherLoad)).isTrue();
        assertThat(readiness.succeed(otherLoad, () -> { })).isTrue();
        assertThat(readiness.isReady(other)).isTrue();
        assertThat(readiness.isReady(player)).isFalse();
    }
}
