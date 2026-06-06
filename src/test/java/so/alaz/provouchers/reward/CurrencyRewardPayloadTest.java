package so.alaz.provouchers.reward;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyRewardPayloadTest {

    @Test
    void parsesGiveWithAmount() {
        CurrencyRewardPayload payload = CurrencyRewardPayload.parse("give 250");
        assertThat(payload.action()).isEqualTo(CurrencyRewardPayload.Action.GIVE);
        assertThat(payload.resolveAmount()).isEqualTo(250.0);
    }

    @Test
    void parsesTakeAliases() {
        assertThat(CurrencyRewardPayload.parse("take 100").action())
            .isEqualTo(CurrencyRewardPayload.Action.TAKE);
        assertThat(CurrencyRewardPayload.parse("withdraw 100").action())
            .isEqualTo(CurrencyRewardPayload.Action.TAKE);
        assertThat(CurrencyRewardPayload.parse("cost 100").action())
            .isEqualTo(CurrencyRewardPayload.Action.TAKE);
    }

    @Test
    void defaultsToGiveWhenNoVerb() {
        CurrencyRewardPayload payload = CurrencyRewardPayload.parse("99.5");
        assertThat(payload.action()).isEqualTo(CurrencyRewardPayload.Action.GIVE);
        assertThat(payload.resolveAmount()).isEqualTo(99.5);
    }

    @Test
    void acceptsTokenAmountWithoutResolving() {
        CurrencyRewardPayload payload = CurrencyRewardPayload.parse("give {random:100-500}");
        assertThat(payload.action()).isEqualTo(CurrencyRewardPayload.Action.GIVE);
        assertThat(payload.amount()).isEqualTo("{random:100-500}");
    }

    @Test
    void rejectsNonNumericAmount() {
        assertThatThrownBy(() -> CurrencyRewardPayload.parse("give lots"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> CurrencyRewardPayload.parse("give -5"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CurrencyRewardPayload.parse("give 0"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> CurrencyRewardPayload.parse("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
