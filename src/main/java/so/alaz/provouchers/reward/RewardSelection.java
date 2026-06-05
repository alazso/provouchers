package so.alaz.provouchers.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Combines a voucher's always-run rewards with one weighted random set into the
 * final list of reward lines granted by a single redemption.
 */
public final class RewardSelection {

    private RewardSelection() {
    }

    /** Gathers the rewards to grant, picking one weighted set with {@code random}. */
    public static List<RewardLine> gather(List<RewardLine> always, List<RewardSet> random, Random rng) {
        List<RewardLine> granted = new ArrayList<>(always);
        RewardSet chosen = WeightedRewardPicker.pick(random, rng);
        if (chosen != null) {
            granted.addAll(chosen.rewards());
        }
        return granted;
    }
}
