package so.alaz.provouchers.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
}
