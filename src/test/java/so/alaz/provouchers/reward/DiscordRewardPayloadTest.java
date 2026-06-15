package so.alaz.provouchers.reward;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordRewardPayloadTest {

    @Test
    void parsesInlineWebhookUrl() {
        DiscordRewardPayload p = DiscordRewardPayload.parse(
            "https://discord.com/api/webhooks/123/abc-DEF_4 Hello %player%");
        assertThat(p.isNamedRef()).isFalse();
        assertThat(p.target()).isEqualTo("https://discord.com/api/webhooks/123/abc-DEF_4");
        assertThat(p.message()).isEqualTo("Hello %player%");
    }

    @Test
    void parsesNamedReferenceLowerCased() {
        DiscordRewardPayload p = DiscordRewardPayload.parse("@Announce a rare drop, %player%!");
        assertThat(p.isNamedRef()).isTrue();
        assertThat(p.namedRef()).isEqualTo("announce");
        assertThat(p.message()).isEqualTo("a rare drop, %player%!");
    }

    @Test
    void acceptsCanaryPtbAndVersionedAndLegacyHosts() {
        assertThatCode(() -> {
            DiscordRewardPayload.parse("https://canary.discord.com/api/v10/webhooks/1/tok hi");
            DiscordRewardPayload.parse("https://ptb.discord.com/api/webhooks/1/tok hi");
            DiscordRewardPayload.parse("https://discordapp.com/api/webhooks/1/tok hi");
            DiscordRewardPayload.parse("https://discord.com/api/webhooks/1/tok?wait=true hi");
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonDiscordUrl() {
        assertThatThrownBy(() -> DiscordRewardPayload.parse("https://evil.example.com/api/webhooks/1/t hi"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a Discord webhook");
    }

    @Test
    void rejectsMissingMessage() {
        assertThatThrownBy(() -> DiscordRewardPayload.parse("@announce"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("webhook and a message");
        assertThatThrownBy(() -> DiscordRewardPayload.parse(
            "https://discord.com/api/webhooks/1/tok"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
