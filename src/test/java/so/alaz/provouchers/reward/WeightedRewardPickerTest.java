package so.alaz.provouchers.reward;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedRewardPickerTest {

    private static RewardSet set(double weight, String tag) {
        return new RewardSet(weight, List.of(new RewardLine(RewardType.MESSAGE, tag)));
    }

    @Test
    void emptyListYieldsNull() {
        assertThat(WeightedRewardPicker.pick(List.of(), new Random(1))).isNull();
    }

    @Test
    void singleSetAlwaysReturned() {
        RewardSet only = set(5, "a");
        assertThat(WeightedRewardPicker.pick(List.of(only), new Random(1))).isSameAs(only);
    }

    @Test
    void heavierWeightWinsMoreOften() {
        List<RewardSet> sets = List.of(set(90, "common"), set(10, "rare"));
        int common = 0;
        Random random = new Random(7);
        for (int i = 0; i < 10_000; i++) {
            RewardSet chosen = WeightedRewardPicker.pick(sets, random);
            if (chosen.rewards().get(0).payload().equals("common")) {
                common++;
            }
        }
        assertThat(common).isBetween(8_500, 9_500);
    }

    @Test
    void selectionAndGatherCombineAlwaysAndRandom() {
        List<RewardLine> always = List.of(new RewardLine(RewardType.MESSAGE, "always"));
        List<RewardSet> random = List.of(set(1, "picked"));
        List<RewardLine> granted = RewardSelection.gather(always, random, new Random(1));
        assertThat(granted).extracting(RewardLine::payload).containsExactly("always", "picked");
    }

    @Test
    void gatherWithNoRandomReturnsOnlyAlways() {
        List<RewardLine> always = List.of(new RewardLine(RewardType.MESSAGE, "always"));
        List<RewardLine> granted = RewardSelection.gather(always, List.of(), new Random(1));
        assertThat(granted).hasSize(1);
    }
}
