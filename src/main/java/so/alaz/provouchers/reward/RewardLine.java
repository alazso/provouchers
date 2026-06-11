package so.alaz.provouchers.reward;

/**
 * A single parsed reward action: its {@link RewardType} and the raw payload that
 * follows the keyword (a command, message, sound key, and so on). The payload
 * may still contain placeholders and MiniMessage markup; those are resolved at
 * redemption time.
 */
public record RewardLine(RewardType type, String payload) {

    public RewardLine {
        if (type == null) {
            throw new IllegalArgumentException("Reward type must not be null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("Reward payload must not be null");
        }
    }
}
