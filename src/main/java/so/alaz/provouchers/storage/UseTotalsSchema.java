package so.alaz.provouchers.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds a running total per use key (a code, or {@code "voucher:<id>"}), so a global cap is reserved
 * with one conditional update on a single row, and backfills each total from the per-player counts.
 *
 * <p>The backfill replaces any rows already in the table: MySQL and MariaDB commit {@code CREATE TABLE}
 * implicitly, so the table can outlive a failed run, and servers sharing a database can run this
 * migration together.
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
