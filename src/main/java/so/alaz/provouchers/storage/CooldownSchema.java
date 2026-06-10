package so.alaz.provouchers.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Second schema version. Adds the per-player cooldown table so voucher cooldowns
 * survive restarts. Portable column types, like the initial schema.
 */
public final class CooldownSchema implements Migration {

    @Override
    public int version() {
        return 2;
    }

    @Override
    public void up(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS provouchers_cooldowns (
                    player_uuid VARCHAR(36) NOT NULL,
                    voucher_id VARCHAR(64) NOT NULL,
                    expires_at BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid, voucher_id)
                )""");
        }
    }
}
