package so.alaz.provouchers.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholdersTest {

    @Test
    void substitutesPercentPlaceholders() {
        String result = Placeholders.apply("give %player% %arg% (%random:5-5%)", "Steve", "diamond");
        assertThat(result).isEqualTo("give Steve diamond (5)");
    }

    @Test
    void legacyCurlyPlaceholdersStillWork() {
        String result = Placeholders.apply("give {player} {arg} ({random:5-5})", "Steve", "diamond");
        assertThat(result).isEqualTo("give Steve diamond (5)");
    }

    @Test
    void substitutesPlayerAndArgPlaceholders() {
        String result = Placeholders.apply("give %player% {arg} for {player}", "Steve", "diamond");
        assertThat(result).isEqualTo("give Steve diamond for Steve");
    }

    @Test
    void blankArgBecomesEmptyString() {
        assertThat(Placeholders.apply("reward {arg}", "Steve", null)).isEqualTo("reward ");
    }

    @Test
    void randomPlaceholderStaysWithinInclusiveRange() {
        Random fixed = new Random(42);
        for (int i = 0; i < 100; i++) {
            String result = Placeholders.apply("{random:1-6}", "Steve", null, fixed);
            int value = Integer.parseInt(result);
            assertThat(value).isBetween(1, 6);
        }
    }

    @Test
    void randomPlaceholderHandlesReversedBounds() {
        String result = Placeholders.apply("{random:6-1}", "Steve", null, new Random(1));
        assertThat(Integer.parseInt(result)).isBetween(1, 6);
    }

    @Test
    void leavesUnknownPlaceholdersUntouched() {
        assertThat(Placeholders.apply("<green>%player_level%", "Steve", null))
            .isEqualTo("<green>%player_level%");
    }

    @Test
    void emptyInputReturnedUnchanged() {
        assertThat(Placeholders.apply("", "Steve", "x")).isEmpty();
    }

    @Test
    void applyAllSubstitutesEveryLine() {
        List<String> lore = List.of("Bound to %player%", "Reward: {random:5-5} gems", "<gray>plain");
        assertThat(Placeholders.applyAll(lore, "Steve", null))
            .containsExactly("Bound to Steve", "Reward: 5 gems", "<gray>plain");
    }
}
