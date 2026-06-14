package so.alaz.provouchers.stash;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StashEntryTest {

    private static StashEntry entry(int amount, Long expiresAt) {
        return new StashEntry(UUID.randomUUID(), UUID.randomUUID(), "crate", amount,
            "arg", StashSource.ADMIN, 1_000L, expiresAt);
    }

    @Test
    void rejectsInvalidFields() {
        UUID id = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        assertThatThrownBy(() -> new StashEntry(null, player, "c", 1, null, StashSource.API, 0, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> new StashEntry(id, null, "c", 1, null, StashSource.API, 0, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("player");
        assertThatThrownBy(() -> new StashEntry(id, player, "  ", 1, null, StashSource.API, 0, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("voucher id");
        assertThatThrownBy(() -> new StashEntry(id, player, "c", 0, null, StashSource.API, 0, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least 1");
        assertThatThrownBy(() -> new StashEntry(id, player, "c", 1, null, null, 0, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("source");
    }

    @Test
    void neverExpiringEntryIsAlwaysLive() {
        StashEntry entry = entry(1, null);
        assertThat(entry.isExpired(0)).isFalse();
        assertThat(entry.isExpired(Long.MAX_VALUE)).isFalse();
    }

    @Test
    void expiryIsInclusiveOfTheBoundary() {
        StashEntry entry = entry(2, 5_000L);
        assertThat(entry.isExpired(4_999L)).isFalse();
        assertThat(entry.isExpired(5_000L)).isTrue();
        assertThat(entry.isExpired(5_001L)).isTrue();
    }

    @Test
    void storedSourceFallsBackToApi() {
        assertThat(StashSource.fromStored("OVERFLOW")).isEqualTo(StashSource.OVERFLOW);
        assertThat(StashSource.fromStored("not-a-source")).isEqualTo(StashSource.API);
        assertThat(StashSource.fromStored(null)).isEqualTo(StashSource.API);
    }
}
