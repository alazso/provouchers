package so.alaz.provouchers.voucher;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomItemRefTest {

    @Test
    void splitsProviderFromIdOnFirstColon() {
        CustomItemRef ref = CustomItemRef.parse("itemsadder:ax_wings_pack:phoenix_wings");
        assertThat(ref.providerHint()).isEqualTo("itemsadder");
        assertThat(ref.id()).isEqualTo("ax_wings_pack:phoenix_wings");
    }

    @Test
    void singleTokenIdHasNoProviderHint() {
        CustomItemRef ref = CustomItemRef.parse("my_item");
        assertThat(ref.providerHint()).isNull();
        assertThat(ref.id()).isEqualTo("my_item");
    }

    @Test
    void oraxenStyleReference() {
        CustomItemRef ref = CustomItemRef.parse("oraxen:cool_sword");
        assertThat(ref.providerHint()).isEqualTo("oraxen");
        assertThat(ref.id()).isEqualTo("cool_sword");
    }

    @Test
    void blankProviderHintBecomesNull() {
        CustomItemRef ref = CustomItemRef.parse(":just_id");
        assertThat(ref.providerHint()).isNull();
        assertThat(ref.id()).isEqualTo("just_id");
    }

    @Test
    void blankIdRejected() {
        assertThatThrownBy(() -> CustomItemRef.parse("oraxen:"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
