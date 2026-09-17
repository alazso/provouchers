package so.alaz.provouchers.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Fifth schema version. Adds a running total per use key (a code, or {@code "voucher:<id>"}) so a
 * global cap can be reserved with one conditional update on a single row instead of summing every
 * player's count, which cannot be made atomic portably. The total is backfilled from the per-player
 * counts already recorded, so existing caps carry over unchanged.
 *
 * <p>The backfill replaces whatever the table holds. MySQL and MariaDB commit a {@code CREATE TABLE}
 * on their own, so a backfill that failed or was interrupted leaves the table behind, and servers
 * sharing a database can start this migration together, one of them failing on the other's work;
 * either way the next run (the retry {@link VoucherStorage#init()} makes, or a later start) recomputes
 * every total, or skips the migration once the other server has recorded it, instead of failing on
 * rows that are already there.
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
            statement.executeUpdate("DELETE FROM provouchers_use_totals");
            statement.executeUpdate(
                "INSERT INTO provouchers_use_totals (code, uses) "
                    + "SELECT code, SUM(uses) FROM provouchers_code_uses GROUP BY code");
        }
    }
}
