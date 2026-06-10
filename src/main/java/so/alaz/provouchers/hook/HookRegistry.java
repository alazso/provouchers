package so.alaz.provouchers.hook;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of {@link Hook}s keyed by capability type. Multiple providers may register for the same
 * capability (the custom-item hooks); {@link #get} resolves the first one that is currently
 * {@link Hook#isAvailable() available} and returns {@code null} when none are, so callers degrade
 * gracefully rather than crashing. Availability is re-checked on every call, so a provider that
 * goes away is simply skipped.
 */
public final class HookRegistry {

    private final Map<Class<? extends Hook>, List<Hook>> byType = new ConcurrentHashMap<>();

    /** Registers a hook under a capability type, in registration order. */
    public <T extends Hook> void register(Class<T> type, T hook) {
        byType.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>()).add(hook);
    }

    /** The first currently-available hook for the type, or {@code null} if none are available. */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends Hook> T get(Class<T> type) {
        for (Hook hook : byType.getOrDefault(type, List.of())) {
            if (hook.isAvailable()) {
                return (T) hook;
            }
        }
        return null;
    }

    /** All registered providers for the type, in registration order, regardless of availability. */
    @SuppressWarnings("unchecked")
    public <T extends Hook> List<T> all(Class<T> type) {
        return (List<T>) List.copyOf(byType.getOrDefault(type, List.of()));
    }
}
