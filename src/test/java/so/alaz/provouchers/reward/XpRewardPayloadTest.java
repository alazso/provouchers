package so.alaz.provouchers.reward;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XpRewardPayloadTest {

    @Test
    void defaultsToPoints() {
        XpRewardPayload payload = XpRewardPayload.parse("500");
        assertThat(payload.levels()).isFalse();
        assertThat(payload.resolveAmount()).isEqualTo(500);
    }

    @Test
    void parsesLevelsUnit() {
        assertThat(XpRewardPayload.parse("30 levels").levels()).isTrue();
        assertThat(XpRewardPayload.parse("30 lvl").levels()).isTrue();
        assertThat(XpRewardPayload.parse("30 points").levels()).isFalse();
    }

    @Test
    void placeholderAmountAcceptedAtLoad() {
        XpRewardPayload payload = XpRewardPayload.parse("%random:10-50% levels");
        assertThat(payload.amount()).isEqualTo("%random:10-50%");
        assertThatThrownBy(payload::resolveAmount).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void rejectsBadAmountsAndUnits() {
        assertThatThrownBy(() -> XpRewardPayload.parse("zero"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> XpRewardPayload.parse("-5"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> XpRewardPayload.parse("10 bottles"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unit");
    }
}
