package so.alaz.provouchers.reward;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RewardDescriberTest {

    @Test
    void describesItemWithLiteralAmount() {
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.ITEM, "DIAMOND 5"))).isEqualTo("5x Diamond");
    }

    @Test
    void titleCasesMultiWordMaterials() {
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.ITEM, "GOLD_INGOT 2"))).isEqualTo("2x Gold Ingot");
    }

    @Test
    void itemDefaultsToOne() {
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.ITEM, "EMERALD"))).isEqualTo("1x Emerald");
    }

    @Test
    void tokenAmountShownAsRange() {
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.ITEM, "GOLD_INGOT {random:1-3}")))
            .isEqualTo("1-3x Gold Ingot");
    }

    @Test
    void serializedItemShownGenerically() {
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.ITEM, "serialized:abc 1")))
            .isEqualTo("1x a custom item");
    }

    @Test
    void providerItemShownAsWritten() {
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.ITEM, "itemsadder:pack:wings 1")))
            .isEqualTo("1x itemsadder:pack:wings");
    }

    @Test
    void currencyGiveIsLootTakeIsNot() {
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.CURRENCY, "give 250"))).isEqualTo("250 currency");
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.CURRENCY, "take 100"))).isNull();
    }

    @Test
    void groupAddIsLootRemoveIsNot() {
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.GROUP, "add vip"))).isEqualTo("vip rank");
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.GROUP, "add vip 7d")))
            .isEqualTo("vip rank (temporary)");
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.GROUP, "remove vip"))).isNull();
    }

    @Test
    void cosmeticAndCommandRewardsAreNotLoot() {
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.MESSAGE, "hi"))).isNull();
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.CONSOLE_COMMAND, "say hi"))).isNull();
        assertThat(RewardDescriber.describe(new RewardLine(RewardType.SOUND, "minecraft:x"))).isNull();
    }

    @Test
    void describeAllFiltersNonLoot() {
        List<RewardLine> rewards = List.of(
            new RewardLine(RewardType.MESSAGE, "hi"),
            new RewardLine(RewardType.ITEM, "DIAMOND 3"),
            new RewardLine(RewardType.CONSOLE_COMMAND, "say hi"));
        assertThat(RewardDescriber.describeAll(rewards)).containsExactly("3x Diamond");
    }

    @Test
    void describeRandomPrefixesChances() {
        List<RewardSet> sets = List.of(
            new RewardSet(70, List.of(new RewardLine(RewardType.ITEM, "GOLD_INGOT 1"))),
            new RewardSet(30, List.of(
                new RewardLine(RewardType.ITEM, "EMERALD 1"),
                new RewardLine(RewardType.BROADCAST, "x"))));
        assertThat(RewardDescriber.describeRandom(sets)).containsExactly("70%: 1x Gold Ingot", "30%: 1x Emerald");
    }

    @Test
    void randomSetWithNoLootShowsGeneric() {
        List<RewardSet> sets = List.of(new RewardSet(1, List.of(new RewardLine(RewardType.CONSOLE_COMMAND, "say hi"))));
        assertThat(RewardDescriber.describeRandom(sets)).containsExactly("100%: a reward");
    }
}
