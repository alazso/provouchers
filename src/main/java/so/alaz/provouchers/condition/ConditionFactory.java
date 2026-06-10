package so.alaz.provouchers.condition;

import org.bukkit.configuration.ConfigurationSection;

/** Builds a {@link Condition} from its config section (type-specific keys plus an optional {@code deny}). */
@FunctionalInterface
public interface ConditionFactory {
    Condition create(ConfigurationSection section);
}
