package so.alaz.provouchers.migrate;

import java.util.List;
import java.util.Optional;

/** The registry of available {@link Migrator}s, looked up by id for {@code /voucher import}. */
public final class MigrationService {

    private final List<Migrator> migrators;

    public MigrationService(Migrator... migrators) {
        this.migrators = List.of(migrators);
    }

    /** The migrator with this id (case-insensitive), if registered. */
    public Optional<Migrator> byId(String id) {
        return migrators.stream().filter(m -> m.id().equalsIgnoreCase(id)).findFirst();
    }

    /** The ids of every source whose data is present. */
    public List<String> presentIds() {
        return migrators.stream().filter(Migrator::isPresent).map(Migrator::id).toList();
    }

    /** Every registered source id, whether or not its data is present. */
    public List<String> allIds() {
        return migrators.stream().map(Migrator::id).toList();
    }
}
