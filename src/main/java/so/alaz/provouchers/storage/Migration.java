package so.alaz.provouchers.storage;

import java.sql.Connection;

/**
 * A single forward schema change, identified by an increasing {@link #version()}. The
 * {@link MigrationRunner} applies pending migrations in version order, each in its own transaction,
 * tracking the highest applied version in a {@code provouchers_schema_version} table.
 *
 * <p>Written against plain JDBC. Do not commit or roll back inside {@link #up}; the runner owns the
 * transaction. Throwing aborts and rolls back the migration.
 */
public interface Migration {

    /** Strictly increasing version number, unique within the runner. */
    int version();

    /** Applies the change. */
    void up(Connection connection) throws Exception;
}
