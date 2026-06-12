package so.alaz.provouchers.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyTextTest {

    @Test
    void convertsAmpersandCodes() {
        assertThat(LegacyText.toMiniMessage("&aHi &e{player}&7!"))
            .isEqualTo("<green>Hi <yellow>%player%<gray>!");
        assertThat(LegacyText.toMiniMessage("&lBold &rplain"))
            .isEqualTo("<bold>Bold <reset>plain");
    }

    @Test
    void convertsSectionSignAndHex() {
        assertThat(LegacyText.toMiniMessage("§cRed")).isEqualTo("<red>Red");
        assertThat(LegacyText.toMiniMessage("&#FF8800Hi")).isEqualTo("<#FF8800>Hi");
    }

    @Test
    void convertsCurlyTokens() {
        assertThat(LegacyText.toMiniMessage("give {player} {random:1-3} of {arg}"))
            .isEqualTo("give %player% %random:1-3% of %arg%");
    }

    @Test
    void leavesMiniMessageAndPlainTextAlone() {
        assertThat(LegacyText.toMiniMessage("<gradient:#a:#b>Hi</gradient>"))
            .isEqualTo("<gradient:#a:#b>Hi</gradient>");
        assertThat(LegacyText.toMiniMessage("plain & simple")).isEqualTo("plain & simple");
        assertThat(LegacyText.toMiniMessage("trailing &")).isEqualTo("trailing &");
    }
}
