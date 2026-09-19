package so.alaz.provouchers.platform;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PendingSettlementsTest {

    @Test
    void settlesOnlyWhatNoCallbackStarted() {
        PendingSettlements settlements = new PendingSettlements();
        AtomicInteger delivered = new AtomicInteger();
        AtomicInteger settled = new AtomicInteger();
        PendingSettlements.Entry deliveredEntry = settlements.track(delivered::incrementAndGet);
        settlements.track(settled::incrementAndGet);

        assertThat(deliveredEntry.start()).isTrue();
        assertThat(settlements.settleAll(ex -> { })).isEqualTo(1);
        assertThat(delivered).hasValue(0);
        assertThat(settled).hasValue(1);
    }

    @Test
    void startsEachEntryOnce() {
        PendingSettlements settlements = new PendingSettlements();
        PendingSettlements.Entry entry = settlements.track(() -> { });
        assertThat(entry.start()).isTrue();
        assertThat(entry.start()).isFalse();
        assertThat(settlements.settleAll(ex -> { })).isZero();
    }

    @Test
    void aSettledEntryIsNeitherStartedNorSettledAgain() {
        PendingSettlements settlements = new PendingSettlements();
        AtomicInteger settled = new AtomicInteger();
        PendingSettlements.Entry entry = settlements.track(settled::incrementAndGet);

        assertThat(settlements.settleAll(ex -> { })).isEqualTo(1);
        assertThat(entry.start()).isFalse();
        assertThat(settlements.settleAll(ex -> { })).isZero();
        assertThat(settled).hasValue(1);
    }

    @Test
    void aFailingSettlementDoesNotStopTheRest() {
        PendingSettlements settlements = new PendingSettlements();
        AtomicInteger settled = new AtomicInteger();
        List<RuntimeException> failures = new ArrayList<>();
        settlements.track(settled::incrementAndGet);
        settlements.track(() -> {
            throw new IllegalStateException("storage is gone");
        });
        settlements.track(settled::incrementAndGet);

        assertThat(settlements.settleAll(failures::add)).isEqualTo(3);
        assertThat(settled).hasValue(2);
        assertThat(failures).singleElement().isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tracksTheSameWorkTwiceAsTwoEntries() {
        PendingSettlements settlements = new PendingSettlements();
        AtomicInteger settled = new AtomicInteger();
        Runnable same = settled::incrementAndGet;
        settlements.track(same);
        settlements.track(same);

        assertThat(settlements.settleAll(ex -> { })).isEqualTo(2);
        assertThat(settled).hasValue(2);
    }

    @Test
    void eachEntryRunsExactlyOnceWhenCallbacksRaceTheSettlement() throws Exception {
        PendingSettlements settlements = new PendingSettlements();
        int entries = 1_000;
        int callbacks = 4;
        AtomicInteger delivered = new AtomicInteger();
        AtomicInteger settled = new AtomicInteger();
        List<PendingSettlements.Entry> tracked = new ArrayList<>();
        for (int i = 0; i < entries; i++) {
            tracked.add(settlements.track(settled::incrementAndGet));
        }
        CyclicBarrier start = new CyclicBarrier(callbacks + 1);
        ExecutorService pool = Executors.newFixedThreadPool(callbacks + 1);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (int i = 0; i < callbacks; i++) {
                runs.add(pool.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    for (PendingSettlements.Entry entry : tracked) {
                        if (entry.start()) {
                            delivered.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            Future<Integer> settling = pool.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                return settlements.settleAll(ex -> { });
            });
            for (Future<?> run : runs) {
                run.get(30, TimeUnit.SECONDS);
            }
            assertThat(settling.get(30, TimeUnit.SECONDS)).isEqualTo(settled.get());
            assertThat(delivered.get() + settled.get()).isEqualTo(entries);
            assertThat(settlements.settleAll(ex -> { })).isZero();
        } finally {
            pool.shutdownNow();
        }
    }
}
