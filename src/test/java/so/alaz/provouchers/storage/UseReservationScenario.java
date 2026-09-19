package so.alaz.provouchers.storage;

import so.alaz.provouchers.storage.VoucherStorage.UseLimit;
import so.alaz.provouchers.storage.VoucherStorage.UseReservation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
import static so.alaz.provouchers.storage.VoucherStorage.UNCAPPED;

/**
 * Use-reservation cases run against each backend by the SQLite test and the Docker matrix. Every case
 * uses fresh keys, so all but {@link #upgradeBackfillsTotals} can share a database with other tests.
 */
final class UseReservationScenario {

    /** Racing threads per case; more than any test pool's connections, so they queue and interleave. */
    private static final int THREADS = 8;

    private UseReservationScenario() {
    }

    /** Every case that tolerates a database shared with other tests. */
    static void runShared(StorageConfig config) throws Exception {
        lastUseIsReservedOnce(config);
        perPlayerCapHoldsUnderContention(config);
        unlimitedReservationsCountExactly(config);
        batchReservesTheRemainingAllowance(config);
        releaseRestoresCapacityAndNeverGoesNegative(config);
        clearUsesKeepsTotalsConsistent(config);
        backfillSumsRecordedUses(config);
        backfillRerunReplacesExistingTotals(config);
    }

    static void lastUseIsReservedOnce(StorageConfig config) throws Exception {
        withStorage(config, (storage, provider) -> {
            String key = uniqueKey();
            List<UseReservation> results = concurrently(
                i -> () -> storage.reserveUses(key, UUID.randomUUID(), 1, 1, 1));

            assertThat(results).filteredOn(result -> result.reserved() == 1).hasSize(1);
            assertThat(results).filteredOn(result -> result.reserved() == 0)
                .hasSize(THREADS - 1)
                .allSatisfy(result -> assertThat(result.limit()).isEqualTo(UseLimit.GLOBAL));
            assertThat(storage.codeUsesTotal(key)).isEqualTo(1L);
            assertTotalMatchesPlayerCounts(storage, provider,key);
        });
    }

    static void perPlayerCapHoldsUnderContention(StorageConfig config) throws Exception {
        withStorage(config, (storage, provider) -> {
            String key = uniqueKey();
            UUID player = UUID.randomUUID();
            List<UseReservation> results = concurrently(
                i -> () -> storage.reserveUses(key, player, 1, 3, UNCAPPED));

            assertThat(results).filteredOn(result -> result.reserved() == 1).hasSize(3);
            assertThat(results).filteredOn(result -> result.reserved() == 0)
                .allSatisfy(result -> assertThat(result.limit()).isEqualTo(UseLimit.PER_PLAYER));
            assertThat(storage.codeUsesByPlayer(key, player)).isEqualTo(3);
            assertThat(storage.codeUsesTotal(key)).isEqualTo(3L);
        });
    }

    static void unlimitedReservationsCountExactly(StorageConfig config) throws Exception {
        withStorage(config, (storage, provider) -> {
            String key = uniqueKey();
            UUID[] players = {UUID.randomUUID(), UUID.randomUUID()};
            int perThread = 15;
            List<Integer> reservedPerThread = concurrently(i -> () -> {
                int reserved = 0;
                for (int n = 0; n < perThread; n++) {
                    reserved += storage.reserveUses(key, players[i % players.length], 1, UNCAPPED, UNCAPPED)
                        .reserved();
                }
                return reserved;
            });

            assertThat(reservedPerThread).containsOnly(perThread);
            int threadsPerPlayer = THREADS / players.length;
            for (UUID player : players) {
                assertThat(storage.codeUsesByPlayer(key, player)).isEqualTo(threadsPerPlayer * perThread);
            }
            assertThat(storage.codeUsesTotal(key)).isEqualTo((long) THREADS * perThread);
        });
    }

    static void batchReservesTheRemainingAllowance(StorageConfig config) throws Exception {
        withStorage(config, (storage, provider) -> {
            String key = uniqueKey();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            assertThat(storage.reserveUses(key, first, 4, UNCAPPED, 10))
                .isEqualTo(new UseReservation(4, UseLimit.NONE));
            assertThat(storage.reserveUses(key, second, 10, UNCAPPED, 10))
                .isEqualTo(new UseReservation(6, UseLimit.GLOBAL));
            assertThat(storage.reserveUses(key, second, 1, UNCAPPED, 10))
                .isEqualTo(new UseReservation(0, UseLimit.GLOBAL));

            String personal = uniqueKey();
            assertThat(storage.reserveUses(personal, first, 3, 5, 100))
                .isEqualTo(new UseReservation(3, UseLimit.NONE));
            assertThat(storage.reserveUses(personal, first, 5, 5, 100))
                .isEqualTo(new UseReservation(2, UseLimit.PER_PLAYER));

            String contested = uniqueKey();
            List<UseReservation> results = concurrently(
                i -> () -> storage.reserveUses(contested, UUID.randomUUID(), 4, UNCAPPED, 10));
            assertThat(results.stream().mapToInt(UseReservation::reserved).sum()).isEqualTo(10);
            assertThat(results).allSatisfy(result -> assertThat(result.reserved()).isBetween(0, 4));
            assertThat(storage.codeUsesTotal(contested)).isEqualTo(10L);
            assertTotalMatchesPlayerCounts(storage, provider,contested);
        });
    }

    static void releaseRestoresCapacityAndNeverGoesNegative(StorageConfig config) throws Exception {
        withStorage(config, (storage, provider) -> {
            String key = uniqueKey();
            UUID holder = UUID.randomUUID();
            UUID waiting = UUID.randomUUID();
            assertThat(storage.reserveUses(key, holder, 1, 1, 1).reserved()).isEqualTo(1);
            assertThat(storage.reserveUses(key, waiting, 1, 1, 1).reserved()).isZero();
            assertThat(storage.releaseUses(key, holder, 1)).isEqualTo(1);
            assertThat(storage.codeUsesTotal(key)).isZero();
            assertThat(storage.reserveUses(key, waiting, 1, 1, 1).reserved()).isEqualTo(1);

            assertThat(storage.releaseUses(key, waiting, 5)).isEqualTo(1);
            assertThat(storage.releaseUses(key, waiting, 1)).isZero();
            assertThat(storage.releaseUses(key, UUID.randomUUID(), 1)).isZero();
            assertThat(storage.codeUsesByPlayer(key, waiting)).isZero();
            assertThat(storage.codeUsesTotal(key)).isZero();

            String raced = uniqueKey();
            assertThat(storage.reserveUses(raced, holder, 3, UNCAPPED, UNCAPPED).reserved()).isEqualTo(3);
            List<Integer> released = concurrently(i -> () -> storage.releaseUses(raced, holder, 1));
            assertThat(released.stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
            assertThat(storage.codeUsesByPlayer(raced, holder)).isZero();
            assertThat(storage.codeUsesTotal(raced)).isZero();

            String drifted = uniqueKey();
            assertThat(storage.reserveUses(drifted, holder, 2, UNCAPPED, UNCAPPED).reserved()).isEqualTo(2);
            // Puts the total behind the player's count of two.
            execute(provider, "UPDATE provouchers_use_totals SET uses = 1 WHERE code = ?", drifted);
            assertThat(storage.releaseUses(drifted, holder, 2)).isEqualTo(2);
            assertThat(storage.codeUsesByPlayer(drifted, holder)).isZero();
            assertThat(storage.codeUsesTotal(drifted)).isZero();
        });
    }

    static void clearUsesKeepsTotalsConsistent(StorageConfig config) throws Exception {
        withStorage(config, (storage, provider) -> {
            String key = uniqueKey();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            storage.reserveUses(key, first, 2, UNCAPPED, 10);
            storage.reserveUses(key, second, 3, UNCAPPED, 10);
            assertThat(storage.codeUsesTotal(key)).isEqualTo(5L);

            storage.clearUses(key, first);
            assertThat(storage.codeUsesByPlayer(key, first)).isZero();
            assertThat(storage.codeUsesTotal(key)).isEqualTo(3L);
            storage.clearUses(key, UUID.randomUUID());
            assertThat(storage.codeUsesTotal(key)).isEqualTo(3L);
            assertThat(storage.reserveUses(key, first, 10, UNCAPPED, 10))
                .isEqualTo(new UseReservation(7, UseLimit.GLOBAL));

            storage.clearUses(key);
            assertThat(storage.codeUsesByPlayer(key, first)).isZero();
            assertThat(storage.codeUsesByPlayer(key, second)).isZero();
            assertThat(storage.codeUsesTotal(key)).isZero();
            assertThat(storage.reserveUses(key, second, 10, UNCAPPED, 10))
                .isEqualTo(new UseReservation(10, UseLimit.NONE));

            // Only thread 0 clears the whole key: concurrent whole-key clears can deadlock on PostgreSQL.
            String busy = uniqueKey();
            UUID[] players = {UUID.randomUUID(), UUID.randomUUID()};
            concurrently(i -> () -> {
                UUID player = players[i % players.length];
                for (int n = 0; n < 12; n++) {
                    switch ((i + n) % 6) {
                        case 0 -> storage.clearUses(busy, player);
                        case 1 -> storage.releaseUses(busy, player, 1);
                        case 2 -> {
                            if (i == 0) {
                                storage.clearUses(busy);
                            } else {
                                storage.reserveUses(busy, player, 1, UNCAPPED, UNCAPPED);
                            }
                        }
                        default -> storage.reserveUses(busy, player, 2, UNCAPPED, UNCAPPED);
                    }
                }
                return null;
            });
            assertTotalMatchesPlayerCounts(storage, provider,busy);
        });
    }

    static void backfillSumsRecordedUses(StorageConfig config) throws Exception {
        withStorage(config, (storage, provider) -> {
            String busy = uniqueKey();
            String single = uniqueKey();
            recordUsesDirectly(provider, busy, UUID.randomUUID(), 2);
            recordUsesDirectly(provider, busy, UUID.randomUUID(), 3);
            recordUsesDirectly(provider, single, UUID.randomUUID(), 1);

            // Restores the pre-version-5 schema; the migration rebuilds every key's total.
            try (Connection connection = provider.dataSource().getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DROP TABLE provouchers_use_totals");
                }
                connection.setAutoCommit(false);   // as the migration runner applies it
                new UseTotalsSchema().up(connection);
                connection.commit();
                connection.setAutoCommit(true);
            }

            assertBackfilled(storage, busy, single);
        });
    }

    static void backfillRerunReplacesExistingTotals(StorageConfig config) throws Exception {
        withStorage(config, (storage, provider) -> {
            String busy = uniqueKey();
            String single = uniqueKey();
            recordUsesDirectly(provider, busy, UUID.randomUUID(), 2);
            recordUsesDirectly(provider, busy, UUID.randomUUID(), 3);
            recordUsesDirectly(provider, single, UUID.randomUUID(), 1);
            // A stale total already in the table before the rerun.
            execute(provider, "INSERT INTO provouchers_use_totals (code, uses) VALUES (?, 0)", busy);

            try (Connection connection = provider.dataSource().getConnection()) {
                connection.setAutoCommit(false);   // as the migration runner applies it
                new UseTotalsSchema().up(connection);
                connection.commit();
                connection.setAutoCommit(true);
            }

            assertBackfilled(storage, busy, single);
        });
    }

    /** Needs a database never migrated past version 4. */
    static void upgradeBackfillsTotals(StorageConfig config) throws Exception {
        String busy = uniqueKey();
        String single = uniqueKey();
        StorageProvider legacy = new StorageProvider(config);
        try {
            legacy.init().get(60, TimeUnit.SECONDS);
            legacy.migrations()
                .register(new InitialSchema())
                .register(new CooldownSchema())
                .register(new UsedVoucherSchema())
                .register(new StashSchema())
                .migrate()
                .get(60, TimeUnit.SECONDS);
            recordUsesDirectly(legacy, busy, UUID.randomUUID(), 2);
            recordUsesDirectly(legacy, busy, UUID.randomUUID(), 3);
            recordUsesDirectly(legacy, single, UUID.randomUUID(), 1);
        } finally {
            legacy.shutdown().get(30, TimeUnit.SECONDS);
        }

        withStorage(config, (storage, provider) -> assertBackfilled(storage, busy, single));
    }

    private static void assertBackfilled(VoucherStorage storage, String busy, String single) throws SQLException {
        assertThat(storage.codeUsesTotal(busy)).isEqualTo(5L);
        assertThat(storage.codeUsesTotal(single)).isEqualTo(1L);
        assertThat(storage.codeUsesTotal(uniqueKey())).isZero();
        assertThat(storage.reserveUses(busy, UUID.randomUUID(), 5, UNCAPPED, 6))
            .isEqualTo(new UseReservation(1, UseLimit.GLOBAL));
    }

    @FunctionalInterface
    private interface StorageCase {
        void run(VoucherStorage storage, StorageProvider provider) throws Exception;
    }

    /** Opens storage for {@code config} (running migrations), runs {@code body}, then shuts down. */
    private static void withStorage(StorageConfig config, StorageCase body) throws Exception {
        StorageProvider provider = new StorageProvider(config);
        VoucherStorage storage = new VoucherStorage(provider);
        try {
            storage.init().get(60, TimeUnit.SECONDS);
            body.run(storage, provider);
        } finally {
            storage.shutdown().get(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Runs {@link #THREADS} tasks (built from each thread's index) that start together at a barrier, and
     * returns their results in index order. A task's failure fails the case.
     */
    private static <T> List<T> concurrently(IntFunction<Callable<T>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CyclicBarrier start = new CyclicBarrier(THREADS);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                Callable<T> body = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await(60, TimeUnit.SECONDS);
                    return body.call();
                }));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(120, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** The invariant every writer maintains: a key's total equals the sum of its player counts. */
    private static void assertTotalMatchesPlayerCounts(VoucherStorage storage, StorageProvider provider, String key)
        throws SQLException {
        long summed;
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT COALESCE(SUM(uses), 0) FROM provouchers_code_uses WHERE code = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                summed = result.getLong(1);
            }
        }
        assertThat(storage.codeUsesTotal(key)).isEqualTo(summed);
    }

    /** Inserts a per-player count directly, without touching the key's total. */
    private static void recordUsesDirectly(StorageProvider provider, String key, UUID player, int uses)
        throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO provouchers_code_uses (code, player_uuid, uses, updated_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, player.toString());
            statement.setInt(3, uses);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static void execute(StorageProvider provider, String sql, String key) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.executeUpdate();
        }
    }

    /** A use key no other case or run touches, within the 64-character column. */
    private static String uniqueKey() {
        return "test:" + UUID.randomUUID();
    }
}
