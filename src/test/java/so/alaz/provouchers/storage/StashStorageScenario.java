package so.alaz.provouchers.storage;

import so.alaz.provouchers.stash.StashCard;
import so.alaz.provouchers.stash.StashEntry;
import so.alaz.provouchers.stash.StashSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stash storage cases run against each backend. Each case uses its own player and leaves no rows
 * behind, so the cases can share a database.
 */
final class StashStorageScenario {

    private static final int THREADS = 8;

    private StashStorageScenario() {
    }

    /** Opens storage for {@code config} (running migrations), runs every stash case, then shuts down. */
    static void deliverClaimAndRestore(StorageConfig config) throws Exception {
        StorageProvider provider = new StorageProvider(config);
        VoucherStorage storage = new VoucherStorage(provider);
        try {
            storage.init().get(60, TimeUnit.SECONDS);
            concurrentDeliveriesSumExactly(storage);
            concurrentClaimsOfOneRowSucceedOnce(storage);
            concurrentCardClaimsSplitTheRows(storage);
            staleCardClaimsOnlyItsOwnRows(storage);
            restoreReinsertsTheIdenticalRowOnce(storage);
            rowMergedByAnOlderServerIsNotClaimedAtTheReadAmount(provider, storage);
        } finally {
            storage.shutdown().get(30, TimeUnit.SECONDS);
        }
    }

    /** Concurrent deliveries of one voucher each insert their own row, and the card sums them. */
    private static void concurrentDeliveriesSumExactly(VoucherStorage storage) throws Exception {
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();
        storage.addStash(entry(player, "loot", 1, null, now));
        storage.addStash(entry(player, "loot", 1, null, now));
        int perThread = 5;
        race(THREADS, thread -> () -> {
            for (int i = 0; i < perThread; i++) {
                storage.addStash(entry(player, "loot", thread + 1, null, now));
            }
            return null;
        });
        List<StashEntry> rows = storage.listStash(player, now);
        assertThat(rows).hasSize(2 + THREADS * perThread);
        int delivered = 2 + perThread * THREADS * (THREADS + 1) / 2;   // thread t delivers t + 1 each time
        assertThat(StashCard.group(rows)).singleElement()
            .satisfies(card -> assertThat(card.amount()).isEqualTo(delivered));
        clear(storage, player);
    }

    private static void concurrentClaimsOfOneRowSucceedOnce(VoucherStorage storage) throws Exception {
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();
        StashEntry row = entry(player, "crate", 3, "vip", now);
        storage.addStash(row);
        assertThat(race(THREADS, thread -> () -> storage.claimStash(row))).containsOnlyOnce(true);
        assertThat(storage.listStash(player, now)).isEmpty();
    }

    private static void concurrentCardClaimsSplitTheRows(VoucherStorage storage) throws Exception {
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) {
            storage.addStash(entry(player, "loot", i + 1, "vip", now + i));
        }
        StashCard card = onlyCard(storage, player, now);
        List<StashEntry> taken = race(THREADS, thread -> () -> claimRows(storage, card)).stream()
            .flatMap(List::stream)
            .toList();
        assertThat(taken).containsExactlyInAnyOrderElementsOf(card.entries());
        assertThat(storage.listStash(player, now)).isEmpty();
    }

    private static void staleCardClaimsOnlyItsOwnRows(VoucherStorage storage) throws Exception {
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();
        StashEntry merged = entry(player, "loot", 4, null, now - 2_000);
        StashEntry duplicate = entry(player, "loot", 1, null, now - 1_000);
        storage.addStash(merged);
        storage.addStash(duplicate);
        StashCard card = onlyCard(storage, player, now);
        assertThat(card.amount()).isEqualTo(5);

        StashEntry later = entry(player, "loot", 4, null, now);
        storage.addStash(later);

        List<StashEntry> taken = claimRows(storage, card);
        assertThat(taken).containsExactly(merged, duplicate);
        assertThat(new StashCard(taken).amount()).isEqualTo(5);
        assertThat(claimRows(storage, card)).isEmpty();
        StashCard next = onlyCard(storage, player, now);
        assertThat(next.entries()).containsExactly(later);
        assertThat(claimRows(storage, next)).containsExactly(later);
        assertThat(storage.listStash(player, now)).isEmpty();
    }

    private static void restoreReinsertsTheIdenticalRowOnce(VoucherStorage storage) throws Exception {
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();
        // The expiry is a day out so a case sharing this database never prunes it.
        StashEntry expiring = new StashEntry(UUID.randomUUID(), player, "crate", 3, "vip",
            StashSource.OVERFLOW, now - 5_000, now + TimeUnit.DAYS.toMillis(1));
        StashEntry lasting = new StashEntry(UUID.randomUUID(), player, "gift", 2, null,
            StashSource.ADMIN, now - 4_000, null);
        storage.addStash(expiring);
        storage.addStash(lasting);
        assertThat(storage.listStash(player, now)).containsExactlyInAnyOrder(expiring, lasting);
        assertThat(storage.claimStash(expiring)).isTrue();
        assertThat(storage.claimStash(lasting)).isTrue();

        assertThat(storage.restoreStash(expiring)).isTrue();
        assertThat(storage.restoreStash(expiring)).isFalse();
        assertThat(race(THREADS, thread -> () -> storage.restoreStash(lasting))).containsOnlyOnce(true);
        assertThat(storage.listStash(player, now)).containsExactlyInAnyOrder(expiring, lasting);
        clear(storage, player);
    }

    private static void rowMergedByAnOlderServerIsNotClaimedAtTheReadAmount(StorageProvider provider,
                                                                            VoucherStorage storage) throws Exception {
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();
        StashEntry read = entry(player, "loot", 1, null, now);
        storage.addStash(read);
        // An older version sharing the database merges a delivery into the row after it was read.
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement merge = connection.prepareStatement(
                 "UPDATE provouchers_stash SET amount = amount + 4 WHERE id = ?")) {
            merge.setString(1, read.id().toString());
            merge.executeUpdate();
        }
        assertThat(storage.claimStash(read)).isFalse();
        StashCard current = onlyCard(storage, player, now);
        assertThat(current.amount()).isEqualTo(5);
        assertThat(claimRows(storage, current)).hasSize(1);
        assertThat(storage.listStash(player, now)).isEmpty();
    }

    private static StashEntry entry(UUID player, String voucherId, int amount, String arg, long createdAt) {
        return new StashEntry(UUID.randomUUID(), player, voucherId, amount, arg, StashSource.API, createdAt, null);
    }

    /** The player's one card, asserting the rows group into exactly one. */
    private static StashCard onlyCard(VoucherStorage storage, UUID player, long now) throws SQLException {
        List<StashCard> cards = StashCard.group(storage.listStash(player, now));
        assertThat(cards).hasSize(1);
        return cards.getFirst();
    }

    /** Claims a card as the Stash does: one atomic delete per row, keeping the rows this call removed. */
    private static List<StashEntry> claimRows(VoucherStorage storage, StashCard card) throws SQLException {
        List<StashEntry> removed = new ArrayList<>();
        for (StashEntry entry : card.entries()) {
            if (storage.claimStash(entry)) {
                removed.add(entry);
            }
        }
        return removed;
    }

    /** Removes every row the player still has, expired or not. */
    private static void clear(VoucherStorage storage, UUID player) throws SQLException {
        for (StashEntry entry : storage.listStash(player, Long.MIN_VALUE)) {
            storage.claimStash(entry);
        }
    }

    /**
     * Runs one task per thread, all released together by a barrier, and returns their results in thread
     * order. A failed task fails the call.
     */
    private static <T> List<T> race(int threads, IntFunction<Callable<T>> task) throws Exception {
        CyclicBarrier start = new CyclicBarrier(threads);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<T>> futures = new ArrayList<>(threads);
            for (int thread = 0; thread < threads; thread++) {
                Callable<T> work = task.apply(thread);
                futures.add(pool.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    return work.call();
                }));
            }
            List<T> results = new ArrayList<>(threads);
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
