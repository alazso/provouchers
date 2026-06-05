package so.alaz.provouchers.reward;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chooses one {@link RewardSet} from a weighted list. Selection is proportional
 * to each set's weight over the sum of all weights.
 */
public final class WeightedRewardPicker {

    private WeightedRewardPicker() {
    }

    /** Picks a set using the shared thread-local random. */
    @Nullable
    public static RewardSet pick(List<RewardSet> sets) {
        return pick(sets, ThreadLocalRandom.current());
    }

    /**
     * Picks a set using {@code random}. Returns {@code null} only when the list
     * is empty.
     */
    @Nullable
    public static RewardSet pick(List<RewardSet> sets, Random random) {
        if (sets.isEmpty()) {
            return null;
        }
        if (sets.size() == 1) {
            return sets.get(0);
        }
        double total = 0;
        for (RewardSet set : sets) {
            total += set.weight();
        }
        double roll = random.nextDouble() * total;
        double cumulative = 0;
        for (RewardSet set : sets) {
            cumulative += set.weight();
            if (roll < cumulative) {
                return set;
            }
        }
        return sets.get(sets.size() - 1);
    }
}
