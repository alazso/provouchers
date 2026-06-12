package so.alaz.provouchers.reward;

import java.util.Locale;

/**
 * The parsed payload of an {@code xp} reward: an amount and whether it grants levels or
 * raw experience points (the default). The amount may be a literal number or a
 * placeholder such as {@code %random:1-3%} that is substituted before the reward runs,
 * so a placeholder amount is accepted at load and only resolved at redeem time.
 */
public record XpRewardPayload(String amount, boolean levels) {

    public XpRewardPayload {
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("xp reward is missing an amount");
        }
    }

    /** Parses {@code "<amount> [levels|points]"}; the unit defaults to points. */
    public static XpRewardPayload parse(String payload) {
        String[] parts = payload.trim().split("\\s+");
        String amount = parts.length > 0 ? parts[0] : "";
        boolean levels = false;
        if (parts.length > 1) {
            levels = switch (parts[1].toLowerCase(Locale.ROOT)) {
                case "levels", "level", "lvl", "l" -> true;
                case "points", "point", "xp" -> false;
                default -> throw new IllegalArgumentException(
                    "xp unit '" + parts[1] + "' is not 'levels' or 'points'");
            };
        }
        if (!amount.contains("{") && !amount.contains("%")) {
            int value;
            try {
                value = Integer.parseInt(amount);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("xp amount '" + amount + "' is not a number");
            }
            if (value <= 0) {
                throw new IllegalArgumentException("xp amount must be positive, was " + value);
            }
        }
        return new XpRewardPayload(amount, levels);
    }

    /** Resolves the (possibly placeholder-substituted) amount to a positive count. */
    public int resolveAmount() {
        int value = Integer.parseInt(amount);
        if (value <= 0) {
            throw new IllegalArgumentException("xp amount must be positive, was " + value);
        }
        return value;
    }
}
