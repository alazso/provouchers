package so.alaz.provouchers.storage;

import java.sql.Connection;

/**
 * A single forward schema change, identified by an increasing {@link #version()}. The
 * {@link MigrationRunner} applies pending migrations in version order, each in its own transaction,
 * tracking the highest applied version in a {@code provouchers_schema_version} table. The SQL must
 * run unchanged on SQLite, MySQL, MariaDB, and PostgreSQL.
 */
public interface Migration {

    /** Strictly increasing version number, unique within the runner. */
    int version();

    /**
     * Applies the change in the runner's transaction, which it must not commit or roll back. Throwing
     * rolls the migration back and aborts the run.
     */
    void up(Connection connection) throws Exception;
}
