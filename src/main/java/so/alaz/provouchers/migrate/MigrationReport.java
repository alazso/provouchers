package so.alaz.provouchers.migrate;

import java.util.List;

/**
 * The outcome of one migration run: what was imported, what was skipped (and why), and
 * every source key with no ProVouchers equivalent. Warnings are never truncated, so a
 * migration can be audited completely.
 *
 * @param imported display labels for each imported voucher or code
 * @param skipped  entries not imported, each with a reason (such as an id clash)
 * @param warnings keys or settings with no equivalent, reported rather than dropped
 */
public record MigrationReport(List<String> imported, List<String> skipped, List<String> warnings) {

    public MigrationReport {
        imported = List.copyOf(imported);
        skipped = List.copyOf(skipped);
        warnings = List.copyOf(warnings);
    }
}
