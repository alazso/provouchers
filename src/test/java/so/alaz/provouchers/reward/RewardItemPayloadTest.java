package so.alaz.provouchers.reward;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RewardItemPayloadTest {

    @Test
    void defaultsAmountToOne() {
        RewardItemPayload payload = RewardItemPayload.parse("DIAMOND");
        assertThat(payload.reference()).isEqualTo("DIAMOND");
        assertThat(payload.amount()).isEqualTo("1");
        assertThat(payload.resolveAmount()).isEqualTo(1);
    }

    @Test
    void readsTrailingAmount() {
        RewardItemPayload payload = RewardItemPayload.parse("DIAMOND 5");
        assertThat(payload.reference()).isEqualTo("DIAMOND");
        assertThat(payload.resolveAmount()).isEqualTo(5);
    }

    @Test
    void keepsProviderReferenceIntact() {
        RewardItemPayload payload = RewardItemPayload.parse("itemsadder:ax_wings_pack:phoenix_wings 2");
        assertThat(payload.reference()).isEqualTo("itemsadder:ax_wings_pack:phoenix_wings");
        assertThat(payload.resolveAmount()).isEqualTo(2);
    }

    @Test
    void acceptsTokenAmountAtParseTime() {
        // The token is only resolved at redeem time, after substitution, so it must
        // parse without error at load (mirroring currency).
        RewardItemPayload payload = RewardItemPayload.parse("GOLD_INGOT {random:1-3}");
        assertThat(payload.reference()).isEqualTo("GOLD_INGOT");
        assertThat(payload.amount()).isEqualTo("{random:1-3}");
    }

    @Test
    void resolvesAmountAfterSubstitution() {
        // What the executor sees once the token has been substituted to a number.
        assertThat(RewardItemPayload.parse("GOLD_INGOT 3").resolveAmount()).isEqualTo(3);
    }

    @Test
    void rejectsBlankReference() {
        assertThatThrownBy(() -> RewardItemPayload.parse("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonNumericLiteralAmount() {
        assertThatThrownBy(() -> RewardItemPayload.parse("DIAMOND lots"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> RewardItemPayload.parse("DIAMOND 0"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
