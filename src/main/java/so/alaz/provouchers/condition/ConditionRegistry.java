package so.alaz.provouchers.condition;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.hook.HookRegistry;
import so.alaz.provouchers.platform.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps condition {@code type} keys to {@link ConditionFactory}s and builds {@link Condition}s from
 * config. All built-ins are registered on construction: the hook-free ones (permission, world,
 * gamemode, exp, expiry, papi, playerstat) and the hook-backed ones (economy, rank, region), which
 * resolve their provider per evaluation and deny gracefully when it is missing.
 *
 * <p>Config shape: each condition is a map with a {@code type} key plus type-specific keys and an
 * optional {@code deny} message.
 */
public final class ConditionRegistry {

    private final Map<String, ConditionFactory> factories = new ConcurrentHashMap<>();

    public ConditionRegistry(Text text, HookRegistry hooks) {
        BuiltinConditions.registerAll(this, text);
        HookConditions.registerAll(this, text, hooks);
    }

    /** Registers a factory under a type (case-insensitive). Replaces any existing factory. */
    public void register(String type, ConditionFactory factory) {
        factories.put(type.toLowerCase(Locale.ROOT), factory);
    }

    /** Whether a factory is registered for the type (case-insensitive). */
    public boolean isRegistered(String type) {
        return factories.containsKey(type.toLowerCase(Locale.ROOT));
    }

    /** Builds conditions from a YAML list-of-maps, skipping unknown types. */
    public List<Condition> buildFromMaps(List<Map<String, Object>> maps) {
        List<Condition> built = new ArrayList<>();
        for (Map<String, Object> map : maps) {
            Condition condition = build(toSection(map));
            if (condition != null) {
                built.add(condition);
            }
        }
        return built;
    }

    @Nullable
    private Condition build(ConfigurationSection section) {
        String type = section.getString("type");
        if (type == null) {
            return null;
        }
        ConditionFactory factory = factories.get(type.toLowerCase(Locale.ROOT));
        return factory == null ? null : factory.create(section);
    }

    private static ConfigurationSection toSection(Map<String, Object> map) {
        MemoryConfiguration section = new MemoryConfiguration();
        map.forEach((key, value) -> {
            if (key != null) {
                section.set(key, value);
            }
        });
        return section;
    }
}
