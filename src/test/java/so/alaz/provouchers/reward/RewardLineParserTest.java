package so.alaz.provouchers.reward;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RewardLineParserTest {

    @Test
    void parsesKnownTypesAndTrimsOneLeadingSpace() {
        RewardLine reward = RewardLineParser.parse("command: give %player% diamond 1");
        assertThat(reward.type()).isEqualTo(RewardType.CONSOLE_COMMAND);
        assertThat(reward.payload()).isEqualTo("give %player% diamond 1");
    }

    @Test
    void mapsAliasesToTypes() {
        assertThat(RewardLineParser.parse("p-command: spawn").type()).isEqualTo(RewardType.PLAYER_COMMAND);
        assertThat(RewardLineParser.parse("announce: hi").type()).isEqualTo(RewardType.BROADCAST);
        assertThat(RewardLineParser.parse("msg: hi").type()).isEqualTo(RewardType.MESSAGE);
        assertThat(RewardLineParser.parse("action-bar: hi").type()).isEqualTo(RewardType.ACTIONBAR);
    }

    @Test
    void keepsColonsInsidePayload() {
        RewardLine reward = RewardLineParser.parse("message: <red>10:00 left");
        assertThat(reward.payload()).isEqualTo("<red>10:00 left");
    }

    @Test
    void rejectsMissingSeparator() {
        assertThatThrownBy(() -> RewardLineParser.parse("give diamond"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownType() {
        assertThatThrownBy(() -> RewardLineParser.parse("teleport: spawn"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseOrNullSwallowsErrors() {
        assertThat(RewardLineParser.parseOrNull("bogus line")).isNull();
        assertThat(RewardLineParser.parseOrNull("sound: minecraft:entity.player.levelup")).isNotNull();
    }

    @Test
    void unknownKeywordResolvesToNull() {
        assertThat(RewardType.fromKeyword("nope")).isNull();
    }
}
