package so.alaz.provouchers.redeem;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimLedgerTest {

    @Test
    void refusesAnyOtherEntryUntilTheOwnerEnds() {
        ClaimLedger ledger = new ClaimLedger();
        Object owner = new Object();
        Object other = new Object();
        assertThat(ledger.begin("uid", owner)).isTrue();
        assertThat(ledger.holds("uid")).isTrue();
        assertThat(ledger.begin("uid", other)).isFalse();
        assertThat(ledger.begin("uid", owner)).isFalse();   // not reentrant, even for the same owner

        ledger.end("uid", owner);
        assertThat(ledger.holds("uid")).isFalse();
        assertThat(ledger.begin("uid", other)).isTrue();
    }

    @Test
    void onlyTheOwnerEndsItsEntry() {
        ClaimLedger ledger = new ClaimLedger();
        Object owner = new Object();
        assertThat(ledger.begin("uid", owner)).isTrue();

        ledger.end("uid", new Object());                    // a redeem that never entered the id
        assertThat(ledger.holds("uid")).isTrue();

        ledger.end("uid", owner);
        ledger.end("uid", owner);                           // ending twice is harmless
        assertThat(ledger.holds("uid")).isFalse();
    }

    @Test
    void comparesOwnersByIdentity() {
        record Owner(String name) {
        }
        ClaimLedger ledger = new ClaimLedger();
        Owner owner = new Owner("first");
        assertThat(ledger.begin("uid", owner)).isTrue();

        ledger.end("uid", new Owner("first"));              // equal, but not the owner
        assertThat(ledger.holds("uid")).isTrue();

        ledger.end("uid", owner);
        assertThat(ledger.holds("uid")).isFalse();
    }

    @Test
    void anEndedEntryLeavesALaterOwnerInPlace() {
        ClaimLedger ledger = new ClaimLedger();
        Object earlier = new Object();
        Object later = new Object();
        assertThat(ledger.begin("uid", earlier)).isTrue();
        ledger.end("uid", earlier);
        assertThat(ledger.begin("uid", later)).isTrue();

        ledger.end("uid", earlier);                         // a late, repeated end from the settled redeem
        assertThat(ledger.holds("uid")).isTrue();
        assertThat(ledger.begin("uid", earlier)).isFalse();
    }

    @Test
    void holdsEachIdSeparately() {
        ClaimLedger ledger = new ClaimLedger();
        Object owner = new Object();
        assertThat(ledger.begin("first", owner)).isTrue();
        assertThat(ledger.begin("second", owner)).isTrue();

        ledger.end("first", owner);
        assertThat(ledger.holds("first")).isFalse();
        assertThat(ledger.holds("second")).isTrue();
    }

    @Test
    void admitsExactlyOneOfManySimultaneousEntries() throws Exception {
        ClaimLedger ledger = new ClaimLedger();
        int threads = 16;
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                attempts.add(pool.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    return ledger.begin("uid", new Object());
                }));
            }
            int admitted = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(30, TimeUnit.SECONDS)) {
                    admitted++;
                }
            }
            assertThat(admitted).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
