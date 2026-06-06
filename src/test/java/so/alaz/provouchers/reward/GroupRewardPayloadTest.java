package so.alaz.provouchers.reward;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupRewardPayloadTest {

    @Test
    void parsesPermanentAdd() {
        GroupRewardPayload payload = GroupRewardPayload.parse("add vip");
        assertThat(payload.action()).isEqualTo(GroupRewardPayload.Action.ADD);
        assertThat(payload.group()).isEqualTo("vip");
        assertThat(payload.isTemporary()).isFalse();
        assertThat(payload.duration()).isNull();
    }

    @Test
    void parsesTemporaryAdd() {
        GroupRewardPayload payload = GroupRewardPayload.parse("add vip 7d");
        assertThat(payload.isTemporary()).isTrue();
        assertThat(payload.duration()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void parsesRemoveAndIgnoresDuration() {
        GroupRewardPayload payload = GroupRewardPayload.parse("remove vip");
        assertThat(payload.action()).isEqualTo(GroupRewardPayload.Action.REMOVE);
        assertThat(payload.duration()).isNull();
    }

    @Test
    void rejectsUnknownAction() {
        assertThatThrownBy(() -> GroupRewardPayload.parse("toggle vip"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingGroup() {
        assertThatThrownBy(() -> GroupRewardPayload.parse("add"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBadDuration() {
        assertThatThrownBy(() -> GroupRewardPayload.parse("add vip soon"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
