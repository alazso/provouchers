package so.alaz.provouchers.reward;

/**
 * The parsed payload of an {@code item} reward: an item reference (a vanilla
 * material or a {@code provider:id} custom item) and an amount that defaults to 1.
 */
public record RewardItemPayload(String reference, int amount) {

    public RewardItemPayload {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("item reward is missing an item reference");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("item reward amount must be at least 1, was " + amount);
        }
    }

    /** Parses {@code "<reference> [amount]"}; a trailing integer token is the amount. */
    public static RewardItemPayload parse(String payload) {
        String[] parts = payload.trim().split("\\s+");
        String reference = parts[0];
        int amount = 1;
        if (parts.length > 1) {
            String last = parts[parts.length - 1];
            try {
                amount = Integer.parseInt(last);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("item reward amount '" + last + "' is not a number");
            }
        }
        return new RewardItemPayload(reference, amount);
    }
}
