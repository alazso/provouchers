package so.alaz.provouchers.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * First schema version. Creates the tables that back duplicate detection and code
 * redemption tracking. The DDL uses portable column types so it runs unchanged on
 * SQLite, MySQL, MariaDB, and PostgreSQL.
 */
public final class InitialSchema implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public void up(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS provouchers_redeemed_stamps (
                    batch_id VARCHAR(36) NOT NULL,
                    nonce VARCHAR(36) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    redeemed_at BIGINT NOT NULL,
                    PRIMARY KEY (batch_id, nonce)
                )""");
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS provouchers_code_uses (
                    code VARCHAR(64) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    uses INTEGER NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (code, player_uuid)
                )""");
        }
    }
}
