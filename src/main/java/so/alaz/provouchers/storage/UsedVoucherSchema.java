package so.alaz.provouchers.storage;

import so.alaz.strata.api.storage.Migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Third schema version. Replaces the batch/nonce stamp table with a single-column
 * used-voucher table keyed on each item's unique id, matching the simpler anti-dupe
 * model (one unique id per anti-dupe item, recorded once on redeem). Portable DDL.
 */
public final class UsedVoucherSchema implements Migration {

    @Override
    public int version() {
        return 3;
    }

    @Override
    public String description() {
        return "Add the used-voucher table and drop the old stamp table";
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
