package so.alaz.provouchers.reward;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionRewardPayloadTest {

    @Test
    void addGrantsTrue() {
        PermissionRewardPayload payload = PermissionRewardPayload.parse("add essentials.fly");
        assertThat(payload.action()).isEqualTo(PermissionRewardPayload.Action.SET);
        assertThat(payload.node()).isEqualTo("essentials.fly");
        assertThat(payload.value()).isTrue();
    }

    @Test
    void denySetsFalse() {
        PermissionRewardPayload payload = PermissionRewardPayload.parse("deny some.node");
        assertThat(payload.action()).isEqualTo(PermissionRewardPayload.Action.SET);
        assertThat(payload.value()).isFalse();
    }

    @Test
    void removeUnsets() {
        PermissionRewardPayload payload = PermissionRewardPayload.parse("remove some.node");
        assertThat(payload.action()).isEqualTo(PermissionRewardPayload.Action.UNSET);
    }

    @Test
    void setReadsExplicitValue() {
        assertThat(PermissionRewardPayload.parse("set some.node false").value()).isFalse();
        assertThat(PermissionRewardPayload.parse("set some.node true").value()).isTrue();
    }

    @Test
    void rejectsSetWithoutValue() {
        assertThatThrownBy(() -> PermissionRewardPayload.parse("set some.node"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownActionAndMissingNode() {
        assertThatThrownBy(() -> PermissionRewardPayload.parse("toggle some.node"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PermissionRewardPayload.parse("add"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
