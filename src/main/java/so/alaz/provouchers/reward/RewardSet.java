package so.alaz.provouchers.reward;

import java.util.List;

/**
 * A weighted bundle of reward lines. A voucher may declare several reward sets
 * under {@code random-rewards}; exactly one set is chosen per redemption, with
 * probability proportional to its {@link #weight()} relative to the total.
 */
public record RewardSet(double weight, List<RewardLine> rewards) {

    public RewardSet {
        if (weight <= 0) {
            throw new IllegalArgumentException("Reward set weight must be positive, was " + weight);
        }
        rewards = List.copyOf(rewards);
    }
}
