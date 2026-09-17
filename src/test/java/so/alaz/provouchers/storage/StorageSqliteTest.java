package so.alaz.provouchers.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Storage tests on the default SQLite backend, which needs no Docker, so they always run (including
 * on a Docker-less local build). The full networked matrix lives in {@link StorageMigrationMatrixTest}.
 */
class StorageSqliteTest {

    @Test
    void migratesAndRoundTripsOnSqlite(@TempDir Path dir) throws Exception {
        StorageConfig config = StorageConfig.sqlite(dir.resolve("data.db").toString());
        StorageScenario.migrateAndRoundTrip(config);
    }

    @Test
    void bridgesLegacyStrataSchemaVersion(@TempDir Path dir) throws Exception {
        StorageConfig config = StorageConfig.sqlite(dir.resolve("legacy.db").toString());
        StorageProvider provider = new StorageProvider(config);
        try {
            provider.init().get(30, TimeUnit.SECONDS);
            // Simulate a pre-1.0.0 install whose schema is already at v3, tracked by the
            // Strata-managed version table.
            try (Connection c = provider.dataSource().getConnection();
                 Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE strata_schema_version (version INTEGER NOT NULL)");
                s.executeUpdate("INSERT INTO strata_schema_version (version) VALUES (3)");
            }

            int applied = provider.migrations()
                .register(new InitialSchema())
                .register(new CooldownSchema())
                .register(new UsedVoucherSchema())
                .migrate()
                .get(30, TimeUnit.SECONDS);

            // Seeded from the legacy table, so nothing re-runs.
            assertThat(applied).isZero();
            try (Connection c = provider.dataSource().getConnection();
                 Statement s = c.createStatement()) {
                try (ResultSet r = s.executeQuery("SELECT version FROM provouchers_schema_version")) {
                    assertThat(r.next()).isTrue();
                    assertThat(r.getInt(1)).isEqualTo(3);
                }
                // The orphaned legacy table is dropped once it has been read.
                try (ResultSet r = s.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='strata_schema_version'")) {
                    assertThat(r.next()).isFalse();
                }
            }
        } finally {
            provider.shutdown().get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void stashDeliversClaimsAndRestoresExactlyOnSqlite(@TempDir Path dir) throws Exception {
        StashStorageScenario.deliverClaimAndRestore(StorageConfig.sqlite(dir.resolve("stash.db").toString()));
    }

    @Test
    void reservesTheLastUseOnceOnSqlite(@TempDir Path dir) throws Exception {
        StorageConfig config = StorageConfig.sqlite(dir.resolve("data.db").toString());
        UseReservationScenario.lastUseIsReservedOnce(config);
    }

    @Test
    void holdsThePerPlayerCapUnderContentionOnSqlite(@TempDir Path dir) throws Exception {
        StorageConfig config = StorageConfig.sqlite(dir.resolve("data.db").toString());
        UseReservationScenario.perPlayerCapHoldsUnderContention(config);
    }

    @Test
    void countsConcurrentUnlimitedReservationsExactlyOnSqlite(@TempDir Path dir) throws Exception {
        StorageConfig config = StorageConfig.sqlite(dir.resolve("data.db").toString());
        UseReservationScenario.unlimitedReservationsCountExactly(config);
    }

    @Test
    void reservesTheRemainingAllowanceForABatchOnSqlite(@TempDir Path dir) throws Exception {
        StorageConfig config = StorageConfig.sqlite(dir.resolve("data.db").toString());
        UseReservationScenario.batchReservesTheRemainingAllowance(config);
    }

    @Test
    void releasesUsesWithoutGoingNegativeOnSqlite(@TempDir Path dir) throws Exception {
        StorageConfig config = StorageConfig.sqlite(dir.resolve("data.db").toString());
        UseReservationScenario.releaseRestoresCapacityAndNeverGoesNegative(config);
    }

    @Test
    void keepsTotalsConsistentWhenClearingUsesOnSqlite(@TempDir Path dir) throws Exception {
        StorageConfig config = StorageConfig.sqlite(dir.resolve("data.db").toString());
        UseReservationScenario.clearUsesKeepsTotalsConsistent(config);
    }

    @Test
    void backfillsUseTotalsFromRecordedUsesOnSqlite(@TempDir Path dir) throws Exception {
        StorageConfig config = StorageConfig.sqlite(dir.resolve("data.db").toString());
        UseReservationScenario.backfillSumsRecordedUses(config);
    }

    @Test
    void backfillsUseTotalsWhenUpgradingFromVersion4OnSqlite(@TempDir Path dir) throws Exception {
        StorageConfig config = StorageConfig.sqlite(dir.resolve("data.db").toString());
        UseReservationScenario.upgradeBackfillsTotals(config);
    }

    @Test
    void rerunsTheUseTotalsBackfillOverExistingTotalsOnSqlite(@TempDir Path dir) throws Exception {
        StorageConfig config = StorageConfig.sqlite(dir.resolve("data.db").toString());
        UseReservationScenario.backfillRerunReplacesExistingTotals(config);
    }

    @Test
    void refusesQueriesUntilMigratedAndAfterShutdown(@TempDir Path dir) throws Exception {
        StorageProvider provider = new StorageProvider(StorageConfig.sqlite(dir.resolve("data.db").toString()));
        VoucherStorage storage = new VoucherStorage(provider);
        UUID player = UUID.randomUUID();
        try {
            // Only the pool is open, as while the migrations still run: queries are refused.
            provider.init().get(30, TimeUnit.SECONDS);
            assertThat(storage.isReady()).isFalse();
            assertThatThrownBy(() -> storage.reserveUses("WELCOME", player, 1, 1, 1))
                .isInstanceOf(SQLException.class);

            storage.init().get(60, TimeUnit.SECONDS);
            assertThat(storage.isReady()).isTrue();
            assertThat(storage.reserveUses("WELCOME", player, 1, 1, 1).reserved()).isEqualTo(1);
        } finally {
            storage.shutdown().get(30, TimeUnit.SECONDS);
        }
        assertThat(storage.isReady()).isFalse();
        assertThatThrownBy(() -> storage.isUsed("anything")).isInstanceOf(SQLException.class);
    }

    @Test
    void staysNotReadyWhenAMigrationFails(@TempDir Path dir) throws Exception {
        StorageProvider provider = new StorageProvider(StorageConfig.sqlite(dir.resolve("data.db").toString()));
        VoucherStorage storage = new VoucherStorage(provider);
        try {
            provider.init().get(30, TimeUnit.SECONDS);
            try (Connection c = provider.dataSource().getConnection();
                 Statement s = c.createStatement()) {
                // A table in the way of the version 5 backfill, so that migration fails.
                s.executeUpdate("CREATE TABLE provouchers_use_totals (unexpected INTEGER)");
            }
            assertThatThrownBy(() -> storage.init().get(60, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
            assertThat(storage.isReady()).isFalse();
            assertThatThrownBy(() -> storage.isUsed("anything")).isInstanceOf(SQLException.class);
        } finally {
            storage.shutdown().get(30, TimeUnit.SECONDS);
        }
    }
}
