package so.alaz.provouchers.reward;

import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.util.Durations;

import java.time.Duration;
import java.util.Locale;

/**
 * The parsed payload of a {@code group} reward: add the player to a group
 * (optionally for a duration) or remove them from it. Backed by a group-aware
 * permission provider (LuckPerms).
 */
public record GroupRewardPayload(Action action, String group, @Nullable Duration duration) {

    /** Whether the reward adds the player to or removes them from the group. */
    public enum Action { ADD, REMOVE }

    public GroupRewardPayload {
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("group reward is missing a group name");
        }
        if (action == Action.REMOVE) {
            duration = null;
        } else if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException("group reward duration must be positive");
        }
    }

    /** Whether this is a temporary (expiring) membership. */
    public boolean isTemporary() {
        return duration != null;
    }

    /** Parses {@code "<add|remove> <group> [duration]"}. */
    public static GroupRewardPayload parse(String payload) {
        String[] parts = payload.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                "group reward needs '<add|remove> <group> [duration]'");
        }
        Action action = action(parts[0]);
        if (action == null) {
            throw new IllegalArgumentException(
                "unknown group action '" + parts[0] + "' (use add or remove)");
        }
        Duration duration = null;
        if (parts.length > 2 && action == Action.ADD) {
            duration = Durations.parse(parts[2]);
        }
        return new GroupRewardPayload(action, parts[1], duration);
    }

    @Nullable
    private static Action action(String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "add", "give", "grant" -> Action.ADD;
            case "remove", "take", "revoke" -> Action.REMOVE;
            default -> null;
        };
    }
}
