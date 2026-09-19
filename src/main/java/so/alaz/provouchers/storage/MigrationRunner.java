package so.alaz.provouchers.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Applies {@link Migration}s in version order, tracking the highest applied version in a single-row
 * {@code provouchers_schema_version} table. Each migration runs in its own transaction; a failure
 * rolls that migration back and aborts the run.
 *
 * <p>An empty version table is seeded from the legacy {@code strata_schema_version} table of
 * pre-1.0.0 installs, when present, and the legacy table is then dropped.
 */
public final class MigrationRunner {

    private static final String VERSION_TABLE = "provouchers_schema_version";

    private static final String LEGACY_VERSION_TABLE = "strata_schema_version";

    private final StorageProvider storage;
    private final ExecutorService executor;
    private final SortedMap<Integer, Migration> migrations = new TreeMap<>();

    MigrationRunner(StorageProvider storage, ExecutorService executor) {
        this.storage = storage;
        this.executor = executor;
    }

    /** Registers a migration. Returns {@code this} for chaining. */
    public MigrationRunner register(Migration migration) {
        if (migrations.containsKey(migration.version())) {
            throw new IllegalStateException("Duplicate migration version " + migration.version());
        }
        migrations.put(migration.version(), migration);
        return this;
    }

    /** Applies all pending migrations in version order. Completes with the count applied. */
    public CompletableFuture<Integer> migrate() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = storage.dataSource().getConnection()) {
                ensureVersionTable(connection);
                return applyPending(connection);
            } catch (SQLException ex) {
                throw new RuntimeException("Migration failed", ex);
            }
        }, executor);
    }

    private int applyPending(Connection connection) throws SQLException {
        int current = readVersion(connection);
        int applied = 0;
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (Map.Entry<Integer, Migration> entry : migrations.entrySet()) {
                if (entry.getKey() <= current) {
                    continue;
                }
                entry.getValue().up(connection);
                writeVersion(connection, entry.getKey());
                connection.commit();
                applied++;
            }
        } catch (Exception ex) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // The migration's failure is rethrown below.
            }
            throw new RuntimeException("Migration failed at version > " + current, ex);
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
        return applied;
    }

    private void ensureVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + VERSION_TABLE + " (version INTEGER NOT NULL)");
        }
        boolean empty;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + VERSION_TABLE)) {
            result.next();
            empty = result.getInt(1) == 0;
        }
        if (!empty) {
            return;
        }
        // The count query above is closed first: SQLite refuses the INSERT and the DROP while a read
        // cursor is open. This runs in autocommit, so the legacy lookup failing on a fresh install
        // leaves no aborted transaction on PostgreSQL.
        int seed = legacyVersion(connection);
        try (PreparedStatement insert =
                 connection.prepareStatement("INSERT INTO " + VERSION_TABLE + " (version) VALUES (?)")) {
            insert.setInt(1, seed);
            insert.executeUpdate();
        }
        if (seed > 0) {
            try (Statement drop = connection.createStatement()) {
                drop.executeUpdate("DROP TABLE IF EXISTS " + LEGACY_VERSION_TABLE);
            }
        }
    }

    /** The version in the legacy table, or 0 when the table is absent. */
    private int legacyVersion(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT version FROM " + LEGACY_VERSION_TABLE + " LIMIT 1")) {
            return result.next() ? result.getInt(1) : 0;
        } catch (SQLException ex) {
            return 0;
        }
    }

    private int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT version FROM " + VERSION_TABLE + " LIMIT 1")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private void writeVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE " + VERSION_TABLE + " SET version = ?")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }
}
