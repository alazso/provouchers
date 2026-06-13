package so.alaz.provouchers.migrate;

/**
 * Converts another voucher plugin's configuration into ProVouchers files. One implementation
 * per source plugin; registered with {@link MigrationService} and invoked by
 * {@code /voucher import <source>}.
 */
public interface Migrator {

    /** The id typed after {@code /voucher import} (lower case, no spaces), e.g. {@code crazyvouchers}. */
    String id();

    /** The human name shown in messages, e.g. {@code CrazyVouchers}. */
    String displayName();

    /** Whether the source plugin's data is present to import from. */
    boolean isPresent();

    /** Converts the source data, writing ProVouchers files and returning what happened. */
    MigrationReport migrate();
}
