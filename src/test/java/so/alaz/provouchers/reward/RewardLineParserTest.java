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
    void parsesGenericItemReward() {
        RewardLine reward = RewardLineParser.parse("item: DIAMOND 5");
        assertThat(reward.type()).isEqualTo(RewardType.ITEM);
        assertThat(reward.payload()).isEqualTo("DIAMOND 5");
    }

    @Test
    void parsesProviderItemReward() {
        RewardLine reward = RewardLineParser.parse("item: itemsadder:ax_wings_pack:phoenix_wings");
        assertThat(reward.type()).isEqualTo(RewardType.ITEM);
        assertThat(reward.payload()).isEqualTo("itemsadder:ax_wings_pack:phoenix_wings");
    }

    @Test
    void foldsProviderKeywordIntoReference() {
        RewardLine fromItemsAdder = RewardLineParser.parse("itemsadder: ax_wings_pack:phoenix_wings");
        assertThat(fromItemsAdder.type()).isEqualTo(RewardType.ITEM);
        assertThat(fromItemsAdder.payload()).isEqualTo("itemsadder:ax_wings_pack:phoenix_wings");

        RewardLine fromAlias = RewardLineParser.parse("ia: ax_wings_pack:phoenix_wings 3");
        assertThat(fromAlias.payload()).isEqualTo("itemsadder:ax_wings_pack:phoenix_wings 3");

        RewardLine fromOraxen = RewardLineParser.parse("oraxen: cool_sword");
        assertThat(fromOraxen.payload()).isEqualTo("oraxen:cool_sword");
    }

    @Test
    void rejectsItemRewardWithUnknownMaterial() {
        assertThatThrownBy(() -> RewardLineParser.parse("item: DIMAOND"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsItemRewardWithBadAmount() {
        assertThatThrownBy(() -> RewardLineParser.parse("item: DIAMOND lots"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesCurrencyReward() {
        RewardLine give = RewardLineParser.parse("currency: give 250");
        assertThat(give.type()).isEqualTo(RewardType.CURRENCY);
        assertThat(give.payload()).isEqualTo("give 250");
        assertThat(RewardLineParser.parse("economy: 100").type()).isEqualTo(RewardType.CURRENCY);
    }

    @Test
    void rejectsCurrencyRewardWithBadAmount() {
        assertThatThrownBy(() -> RewardLineParser.parse("currency: give lots"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesGroupAndPermissionRewards() {
        assertThat(RewardLineParser.parse("group: add vip 7d").type()).isEqualTo(RewardType.GROUP);
        assertThat(RewardLineParser.parse("rank: remove vip").type()).isEqualTo(RewardType.GROUP);
        assertThat(RewardLineParser.parse("permission: add some.node").type())
            .isEqualTo(RewardType.PERMISSION);
        assertThat(RewardLineParser.parse("perm: set some.node false").type())
            .isEqualTo(RewardType.PERMISSION);
    }

    @Test
    void rejectsMalformedGroupAndPermissionRewards() {
        assertThatThrownBy(() -> RewardLineParser.parse("group: add vip soon"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RewardLineParser.parse("permission: set some.node maybe"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesDiscordReward() {
        RewardLine named = RewardLineParser.parse("discord: @announce %player% won!");
        assertThat(named.type()).isEqualTo(RewardType.DISCORD);
        assertThat(named.payload()).isEqualTo("@announce %player% won!");
        assertThat(RewardLineParser.parse(
            "discord: https://discord.com/api/webhooks/1/tok hi").type()).isEqualTo(RewardType.DISCORD);
    }

    @Test
    void rejectsDiscordRewardWithNonWebhookUrl() {
        assertThatThrownBy(() -> RewardLineParser.parse("discord: https://evil.example.com/hook hi"))
            .isInstanceOf(IllegalArgumentException.class);
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
