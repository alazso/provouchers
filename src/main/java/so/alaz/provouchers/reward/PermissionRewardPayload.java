package so.alaz.provouchers.reward;

import java.util.Locale;

/**
 * The parsed payload of a {@code permission} reward: set a permission node to true
 * or false, or clear a directly-set node. Backed by a permission provider that
 * supports writes (LuckPerms).
 */
public record PermissionRewardPayload(Action action, String node, boolean value) {

    /** Whether the reward sets a node's value or clears it. */
    public enum Action { SET, UNSET }

    public PermissionRewardPayload {
        if (node == null || node.isBlank()) {
            throw new IllegalArgumentException("permission reward is missing a node");
        }
    }

    /** Parses {@code "<add|deny|remove|set> <node> [true|false]"}. */
    public static PermissionRewardPayload parse(String payload) {
        String[] parts = payload.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                "permission reward needs '<add|deny|remove|set> <node> [value]'");
        }
        String verb = parts[0].toLowerCase(Locale.ROOT);
        String node = parts[1];
        return switch (verb) {
            case "add", "grant", "give" -> new PermissionRewardPayload(Action.SET, node, true);
            case "deny" -> new PermissionRewardPayload(Action.SET, node, false);
            case "remove", "unset", "revoke", "take" -> new PermissionRewardPayload(Action.UNSET, node, false);
            case "set" -> {
                if (parts.length < 3) {
                    throw new IllegalArgumentException("permission set needs '<node> <true|false>'");
                }
                yield new PermissionRewardPayload(Action.SET, node, parseValue(parts[2]));
            }
            default -> throw new IllegalArgumentException(
                "unknown permission action '" + parts[0] + "' (use add, deny, remove, or set)");
        };
    }

    private static boolean parseValue(String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on" -> true;
            case "false", "no", "off" -> false;
            default -> throw new IllegalArgumentException(
                "permission value '" + token + "' must be true or false");
        };
    }
}
