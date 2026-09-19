package so.alaz.provouchers.storage;

import so.alaz.provouchers.stash.StashEntry;
import so.alaz.provouchers.stash.StashSource;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Migration and CRUD round-trip run against each backend by the SQLite test and the Docker matrix. */
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

        String uid = UUID.randomUUID().toString();
        long claimedAt = System.currentTimeMillis();
        assertThat(storage.isUsed(uid)).isFalse();
        assertThat(storage.recordUse(uid, player, claimedAt)).isTrue();
        assertThat(storage.isUsed(uid)).isTrue();
        assertThat(storage.recordUse(uid, UUID.randomUUID(), claimedAt)).isFalse();
        assertThat(storage.recordUse(uid, player, claimedAt + 1)).isFalse();
        // The repeated exact claim fails on the primary key, and the read-back finds this claim.
        assertThat(storage.recordUse(uid, player, claimedAt)).isTrue();

        assertThat(storage.deleteUse(uid, UUID.randomUUID(), claimedAt)).isFalse();
        assertThat(storage.deleteUse(uid, player, claimedAt + 1)).isFalse();
        assertThat(storage.isUsed(uid)).isTrue();
        assertThat(storage.deleteUse(uid, player, claimedAt)).isTrue();
        assertThat(storage.isUsed(uid)).isFalse();
        assertThat(storage.deleteUse(uid, player, claimedAt)).isFalse();

        String code = "WELCOME";
        int uncapped = VoucherStorage.UNCAPPED;
        assertThat(storage.reserveUses(code, player, 1, uncapped, uncapped).reserved()).isEqualTo(1);
        assertThat(storage.reserveUses(code, player, 1, uncapped, uncapped).reserved()).isEqualTo(1);
        assertThat(storage.codeUsesByPlayer(code, player)).isEqualTo(2);
        assertThat(storage.codeUsesTotal(code)).isEqualTo(2L);

        long now = System.currentTimeMillis();
        storage.setCooldown(player, "daily", now + 60_000);
        assertThat(storage.activeCooldowns(player, now)).containsEntry("daily", now + 60_000);
        storage.setCooldown(player, "daily", now + 120_000);
        assertThat(storage.activeCooldowns(player, now)).containsEntry("daily", now + 120_000);
        assertThat(storage.activeCooldowns(player, now + 200_000)).isEmpty();

        StashEntry kept = new StashEntry(UUID.randomUUID(), player, "crate", 2, "vip", StashSource.ADMIN, now, null);
        StashEntry expiring = new StashEntry(UUID.randomUUID(), player, "gift", 1, null, StashSource.OFFLINE_GIVE,
            now, now + 60_000);
        storage.addStash(kept);
        storage.addStash(expiring);
        assertThat(storage.listStash(player, now)).hasSize(2);
        assertThat(storage.claimStash(kept)).isTrue();
        assertThat(storage.claimStash(kept)).isFalse();
        assertThat(storage.listStash(player, now)).hasSize(1);
        assertThat(storage.listStash(player, now + 120_000)).isEmpty();
        assertThat(storage.pruneExpiredStash(now + 120_000)).isEqualTo(1);
        assertThat(storage.claimStash(expiring)).isFalse();
    }
}
