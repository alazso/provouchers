package so.alaz.provouchers.reward;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RewardItemPayloadTest {

    @Test
    void defaultsAmountToOne() {
        RewardItemPayload payload = RewardItemPayload.parse("DIAMOND");
        assertThat(payload.reference()).isEqualTo("DIAMOND");
        assertThat(payload.amount()).isEqualTo(1);
    }

    @Test
    void readsTrailingAmount() {
        RewardItemPayload payload = RewardItemPayload.parse("DIAMOND 5");
        assertThat(payload.reference()).isEqualTo("DIAMOND");
        assertThat(payload.amount()).isEqualTo(5);
    }

    @Test
    void keepsProviderReferenceIntact() {
        RewardItemPayload payload = RewardItemPayload.parse("itemsadder:ax_wings_pack:phoenix_wings 2");
        assertThat(payload.reference()).isEqualTo("itemsadder:ax_wings_pack:phoenix_wings");
        assertThat(payload.amount()).isEqualTo(2);
    }

    @Test
    void rejectsBlankReference() {
        assertThatThrownBy(() -> RewardItemPayload.parse("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonNumericAmount() {
        assertThatThrownBy(() -> RewardItemPayload.parse("DIAMOND lots"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> RewardItemPayload.parse("DIAMOND 0"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
