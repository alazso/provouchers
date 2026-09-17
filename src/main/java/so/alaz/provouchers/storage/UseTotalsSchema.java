package so.alaz.provouchers.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Fifth schema version. Adds a running total per use key (a code, or {@code "voucher:<id>"}) so a
 * global cap can be reserved with one conditional update on a single row instead of summing every
 * player's count, which cannot be made atomic portably. The total is backfilled from the per-player
 * counts already recorded, so existing caps carry over unchanged.
 */
public final class UseTotalsSchema implements Migration {

    @Override
    public int version() {
        return 5;
    }

    @Override
    public void up(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS provouchers_use_totals (
                    code VARCHAR(64) NOT NULL,
                    uses BIGINT NOT NULL,
                    PRIMARY KEY (code)
                )""");
            statement.executeUpdate(
                "INSERT INTO provouchers_use_totals (code, uses) "
                    + "SELECT code, SUM(uses) FROM provouchers_code_uses GROUP BY code");
        }
    }
}
