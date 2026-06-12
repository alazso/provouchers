package so.alaz.provouchers.reward;

import java.util.Locale;

/**
 * The parsed payload of an {@code item} reward: an item reference (a vanilla
 * material or a {@code provider:id} custom item) and an amount that defaults to 1.
 *
 * <p>The amount may be a literal number or a placeholder such as {@code %random:1-3%}
 * that is substituted before the reward runs, so a placeholder amount is accepted at
 * load and only resolved to a number at redeem time (mirroring {@code currency}).
 */
public record RewardItemPayload(String reference, String amount) {

    public RewardItemPayload {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("item reward is missing an item reference");
        }
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("item reward is missing an amount");
        }
    }

    /** Parses {@code "<reference> [amount]"}; the trailing amount is a number or a placeholder. */
    public static RewardItemPayload parse(String payload) {
        String[] parts = payload.trim().split("\\s+");
        String reference = parts[0];
        String amount = "1";
        if (parts.length > 1) {
            amount = parts[parts.length - 1];
            if (!amount.contains("{") && !amount.contains("%")) {
                requirePositiveInt(amount);
            }
        }
        return new RewardItemPayload(reference, amount);
    }

    /** Resolves the (possibly placeholder-substituted) amount to a positive count. */
    public int resolveAmount() {
        return requirePositiveInt(amount);
    }

    /** Whether the reference is an {@code @name} pointer into the file's defined-items map. */
    public boolean isDefinedRef() {
        return reference.startsWith("@");
    }

    /** The defined-item name behind an {@code @name} reference, lower-cased for map lookup. */
    public String definedName() {
        return reference.substring(1).toLowerCase(Locale.ROOT);
    }

    private static int requirePositiveInt(String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("item reward amount '" + value + "' is not a number");
        }
        if (parsed < 1) {
            throw new IllegalArgumentException("item reward amount must be at least 1, was " + parsed);
        }
        return parsed;
    }
}
