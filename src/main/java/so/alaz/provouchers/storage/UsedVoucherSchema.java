package so.alaz.provouchers.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Replaces the batch and nonce stamp table with a used-voucher table keyed on each item's unique id.
 */
public final class UsedVoucherSchema implements Migration {

    @Override
    public int version() {
        return 3;
    }

    @Override
    public void up(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS provouchers_used_vouchers (
                    uid VARCHAR(36) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    redeemed_at BIGINT NOT NULL,
                    PRIMARY KEY (uid)
                )""");
            statement.executeUpdate("DROP TABLE IF EXISTS provouchers_redeemed_stamps");
        }
    }
}
