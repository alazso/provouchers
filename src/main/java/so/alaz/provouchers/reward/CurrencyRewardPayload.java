package so.alaz.provouchers.reward;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The parsed payload of a {@code currency} reward: an action (give or take) and an
 * amount. The amount may be a literal number or a placeholder such as
 * {@code %random:100-500%} that is substituted before the reward runs, so a placeholder
 * amount is accepted at load and only resolved to a number at redeem time.
 */
public record CurrencyRewardPayload(Action action, String amount) {

    /** Whether the reward deposits to or withdraws from the player's balance. */
    public enum Action { GIVE, TAKE }

    public CurrencyRewardPayload {
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("currency reward is missing an amount");
        }
    }

    /** Parses {@code "[give|take] <amount>"}; the action defaults to {@code give}. */
    public static CurrencyRewardPayload parse(String payload) {
        String[] parts = payload.trim().split("\\s+");
        Action action = Action.GIVE;
        String amount = "";
        if (parts.length >= 2) {
            Action verb = action(parts[0]);
            if (verb != null) {
                action = verb;
                amount = parts[1];
            } else {
                amount = parts[0];
            }
        } else if (parts.length == 1) {
            amount = parts[0];
        }
        if (!amount.contains("{") && !amount.contains("%")) {
            double value;
            try {
                value = Double.parseDouble(amount);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("currency amount '" + amount + "' is not a number");
            }
            if (value <= 0) {
                throw new IllegalArgumentException("currency amount must be positive, was " + value);
            }
        }
        return new CurrencyRewardPayload(action, amount);
    }

    /** Resolves the (possibly placeholder-substituted) amount to a positive double. */
    public double resolveAmount() {
        return Double.parseDouble(amount);
    }

    @Nullable
    private static Action action(String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "give", "add", "deposit" -> Action.GIVE;
            case "take", "remove", "withdraw", "cost" -> Action.TAKE;
            default -> null;
        };
    }
}
