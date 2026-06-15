package so.alaz.provouchers.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds the Stash table that backs virtual (itemless) vouchers: a per-player queue of deferred
 * voucher grants claimed through the Stash GUI. It generalises the never-wired offline-give queue
 * (adding a source and an optional expiry), so that unused table is dropped here. The DDL keeps to a
 * single primary key and portable column types so it runs unchanged on every supported backend.
 */
public final class StashSchema implements Migration {

    @Override
    public int version() {
        return 4;
    }

    @Override
    public void up(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS provouchers_stash (
                    id VARCHAR(36) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    voucher_id VARCHAR(64) NOT NULL,
                    amount INTEGER NOT NULL,
                    arg VARCHAR(255),
                    source VARCHAR(16) NOT NULL,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT,
                    PRIMARY KEY (id)
                )""");
            // Reads filter by player; a plain CREATE INDEX is portable and runs once with this migration.
            statement.executeUpdate(
                "CREATE INDEX idx_provouchers_stash_player ON provouchers_stash (player_uuid)");
            // The offline-give queue was never wired; the Stash supersedes it.
            statement.executeUpdate("DROP TABLE IF EXISTS provouchers_offline_gives");
        }
    }
}
