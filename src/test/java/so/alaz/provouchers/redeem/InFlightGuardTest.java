package so.alaz.provouchers.redeem;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class InFlightGuardTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void refusesASecondAttemptUntilReleased() {
        InFlightGuard guard = new InFlightGuard(60);
        InFlightGuard.Ticket first = guard.acquire(player, "vip");
        assertThat(first).isNotNull();
        assertThat(guard.acquire(player, "vip")).isNull();   // still in flight
        guard.release(first);
        assertThat(guard.acquire(player, "vip")).isNotNull();
    }

    @Test
    void holdsPerPlayerAndVoucher() {
        InFlightGuard guard = new InFlightGuard(60);
        assertThat(guard.acquire(player, "vip")).isNotNull();
        assertThat(guard.acquire(player, "daily")).isNotNull();            // another voucher
        assertThat(guard.acquire(UUID.randomUUID(), "vip")).isNotNull();   // another player
    }

    @Test
    void staleEntryExpiresAfterItsLifetime() {
        AtomicLong now = new AtomicLong(0);
        InFlightGuard guard = new InFlightGuard(60, now::get);
        assertThat(guard.acquire(player, "vip")).isNotNull();   // never released, as after a bug
        now.set(59_999);
        assertThat(guard.acquire(player, "vip")).isNull();      // still live
        now.set(60_000);
        assertThat(guard.acquire(player, "vip")).isNotNull();   // lapsed, so no longer blocks
    }

    @Test
    void lifetimeIsAtLeastOneSecond() {
        AtomicLong now = new AtomicLong(0);
        InFlightGuard guard = new InFlightGuard(0, now::get);
        assertThat(guard.acquire(player, "vip")).isNotNull();
        now.set(999);
        assertThat(guard.acquire(player, "vip")).isNull();
        now.set(1_000);
        assertThat(guard.acquire(player, "vip")).isNotNull();
    }

    @Test
    void releasingALapsedTicketLeavesTheNewerHolder() {
        AtomicLong now = new AtomicLong(0);
        InFlightGuard guard = new InFlightGuard(60, now::get);
        InFlightGuard.Ticket stale = guard.acquire(player, "vip");
        now.set(60_000);
        InFlightGuard.Ticket newer = guard.acquire(player, "vip");
        assertThat(stale).isNotNull();
        assertThat(newer).isNotNull();

        guard.release(stale);                                    // a late release from the lapsed redeem
        assertThat(guard.acquire(player, "vip")).isNull();      // the newer redeem still holds it

        guard.release(newer);
        guard.release(newer);                                    // releasing twice is harmless
        assertThat(guard.acquire(player, "vip")).isNotNull();
    }

    @Test
    void admitsExactlyOneOfManySimultaneousAttempts() throws Exception {
        InFlightGuard guard = new InFlightGuard(60);
        int threads = 16;
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<InFlightGuard.Ticket>> attempts = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                attempts.add(pool.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    return guard.acquire(player, "vip");
                }));
            }
            int admitted = 0;
            for (Future<InFlightGuard.Ticket> attempt : attempts) {
                if (attempt.get(30, TimeUnit.SECONDS) != null) {
                    admitted++;
                }
            }
            assertThat(admitted).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
