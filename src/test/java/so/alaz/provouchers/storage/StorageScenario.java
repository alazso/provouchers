package so.alaz.provouchers.storage;

import so.alaz.provouchers.stash.StashEntry;
import so.alaz.provouchers.stash.StashSource;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared storage exercise run against each backend: open the pool, apply every migration, then a
 * CRUD round-trip touching all three tables. Used by both the SQLite test and the Docker matrix so
 * the same portable DDL and queries are proven identically on every backend.
 */
final class StorageScenario {

    private StorageScenario() {
    }

    /** Opens storage for {@code config} (running migrations), exercises every table, then shuts down. */
    static void migrateAndRoundTrip(StorageConfig config) throws Exception {
        StorageProvider provider = new StorageProvider(config);
        VoucherStorage storage = new VoucherStorage(provider);
        try {
            storage.init().get(60, TimeUnit.SECONDS);
            roundTrip(storage);
        } finally {
            storage.shutdown().get(30, TimeUnit.SECONDS);
        }
    }

    private static void roundTrip(VoucherStorage storage) throws Exception {
        UUID player = UUID.randomUUID();

        // Anti-dupe: the first record of a unique id sticks; a duplicate is rejected by the primary key.
        String uid = UUID.randomUUID().toString();
        assertThat(storage.isUsed(uid)).isFalse();
        assertThat(storage.recordUse(uid, player)).isTrue();
        assertThat(storage.isUsed(uid)).isTrue();
        assertThat(storage.recordUse(uid, player)).isFalse();

        // Code uses: increments accumulate per player and in total.
        String code = "WELCOME";
        assertThat(storage.incrementCodeUse(code, player)).isEqualTo(1);
        assertThat(storage.incrementCodeUse(code, player)).isEqualTo(2);
        assertThat(storage.codeUsesByPlayer(code, player)).isEqualTo(2);
        assertThat(storage.codeUsesTotal(code)).isEqualTo(2L);

        // Cooldowns: stored, replaced in place, and filtered out once expired.
        long now = System.currentTimeMillis();
        storage.setCooldown(player, "daily", now + 60_000);
        assertThat(storage.activeCooldowns(player, now)).containsEntry("daily", now + 60_000);
        storage.setCooldown(player, "daily", now + 120_000);
        assertThat(storage.activeCooldowns(player, now)).containsEntry("daily", now + 120_000);
        assertThat(storage.activeCooldowns(player, now + 200_000)).isEmpty();

        // Stash: entries queue, list live (filtering expiry), claim atomically once, and prune when lapsed.
        UUID kept = UUID.randomUUID();
        UUID expiring = UUID.randomUUID();
        storage.addStash(new StashEntry(kept, player, "crate", 2, "vip", StashSource.ADMIN, now, null));
        storage.addStash(new StashEntry(expiring, player, "gift", 1, null, StashSource.OFFLINE_GIVE,
            now, now + 60_000));
        assertThat(storage.listStash(player, now)).hasSize(2);
        assertThat(storage.countStash(player, now)).isEqualTo(2);
        assertThat(storage.claimStash(kept)).isTrue();
        assertThat(storage.claimStash(kept)).isFalse();           // already claimed
        assertThat(storage.listStash(player, now)).hasSize(1);
        // Past the expiry the lapsed entry is hidden from the live list, then pruned and unclaimable.
        assertThat(storage.listStash(player, now + 120_000)).isEmpty();
        assertThat(storage.countStash(player, now + 120_000)).isZero();
        assertThat(storage.pruneExpiredStash(now + 120_000)).isEqualTo(1);
        assertThat(storage.claimStash(expiring)).isFalse();

        // addOrMergeStash folds never-expiring entries of the same voucher and arg into one stack,
        // but keeps a differing arg, a differing voucher, and an expiring entry separate.
        UUID merger = UUID.randomUUID();
        storage.addOrMergeStash(new StashEntry(merger, merger, "loot", 10, null, StashSource.OVERFLOW, now, null));
        storage.addOrMergeStash(new StashEntry(UUID.randomUUID(), merger, "loot", 10, null,
            StashSource.OVERFLOW, now, null));
        assertThat(storage.listStash(merger, now)).singleElement()
            .satisfies(e -> assertThat(e.amount()).isEqualTo(20));
        storage.addOrMergeStash(new StashEntry(UUID.randomUUID(), merger, "loot", 5, "vip",
            StashSource.ADMIN, now, null));                       // different arg, stays separate
        storage.addOrMergeStash(new StashEntry(UUID.randomUUID(), merger, "loot", 5, null,
            StashSource.OVERFLOW, now, now + 60_000));            // expiring, stays separate
        assertThat(storage.listStash(merger, now)).hasSize(3);
    }
}
