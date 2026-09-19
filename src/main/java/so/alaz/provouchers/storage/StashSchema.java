package so.alaz.provouchers.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds the Stash table that backs virtual (itemless) vouchers: a per-player queue of deferred
 * voucher grants claimed through the Stash GUI. Drops the unused offline-give table.
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
            // Plain CREATE INDEX is the portable form; it runs once, with this migration.
            statement.executeUpdate(
                "CREATE INDEX idx_provouchers_stash_player ON provouchers_stash (player_uuid)");
            statement.executeUpdate("DROP TABLE IF EXISTS provouchers_offline_gives");
        }
    }
}
