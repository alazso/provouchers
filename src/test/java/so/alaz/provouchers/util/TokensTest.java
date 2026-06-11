package so.alaz.provouchers.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class TokensTest {

    @Test
    void substitutesPercentTokens() {
        String result = Tokens.apply("give %player% %arg% (%random:5-5%)", "Steve", "diamond");
        assertThat(result).isEqualTo("give Steve diamond (5)");
    }

    @Test
    void legacyCurlyTokensStillWork() {
        String result = Tokens.apply("give {player} {arg} ({random:5-5})", "Steve", "diamond");
        assertThat(result).isEqualTo("give Steve diamond (5)");
    }

    @Test
    void substitutesPlayerAndArgTokens() {
        String result = Tokens.apply("give %player% {arg} for {player}", "Steve", "diamond");
        assertThat(result).isEqualTo("give Steve diamond for Steve");
    }

    @Test
    void blankArgBecomesEmptyString() {
        assertThat(Tokens.apply("reward {arg}", "Steve", null)).isEqualTo("reward ");
    }

    @Test
    void randomTokenStaysWithinInclusiveRange() {
        Random fixed = new Random(42);
        for (int i = 0; i < 100; i++) {
            String result = Tokens.apply("{random:1-6}", "Steve", null, fixed);
            int value = Integer.parseInt(result);
            assertThat(value).isBetween(1, 6);
        }
    }

    @Test
    void randomTokenHandlesReversedBounds() {
        String result = Tokens.apply("{random:6-1}", "Steve", null, new Random(1));
        assertThat(Integer.parseInt(result)).isBetween(1, 6);
    }

    @Test
    void leavesUnknownTokensUntouched() {
        assertThat(Tokens.apply("<green>%player_level%", "Steve", null))
            .isEqualTo("<green>%player_level%");
    }

    @Test
    void emptyInputReturnedUnchanged() {
        assertThat(Tokens.apply("", "Steve", "x")).isEmpty();
    }

    @Test
    void applyAllSubstitutesEveryLine() {
        List<String> lore = List.of("Bound to %player%", "Reward: {random:5-5} gems", "<gray>plain");
        assertThat(Tokens.applyAll(lore, "Steve", null))
            .containsExactly("Bound to Steve", "Reward: 5 gems", "<gray>plain");
    }
}
