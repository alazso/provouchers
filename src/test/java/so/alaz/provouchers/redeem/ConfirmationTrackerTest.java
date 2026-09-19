package so.alaz.provouchers.redeem;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationTrackerTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void firstClickNeedsConfirmSecondConfirms() {
        ConfirmationTracker tracker = new ConfirmationTracker(5);
        assertThat(tracker.needsConfirm(player, "vip")).isTrue();
        assertThat(tracker.needsConfirm(player, "vip")).isFalse();
        assertThat(tracker.needsConfirm(player, "vip")).isTrue();
    }

    @Test
    void differentVoucherRestartsConfirmation() {
        ConfirmationTracker tracker = new ConfirmationTracker(5);
        assertThat(tracker.needsConfirm(player, "vip")).isTrue();
        assertThat(tracker.needsConfirm(player, "daily")).isTrue();
        assertThat(tracker.needsConfirm(player, "daily")).isFalse();
    }

    @Test
    void confirmationExpiresAfterWindow() {
        AtomicLong now = new AtomicLong(0);
        ConfirmationTracker tracker = new ConfirmationTracker(5, now::get);
        assertThat(tracker.needsConfirm(player, "vip")).isTrue();
        now.set(6000);
        assertThat(tracker.needsConfirm(player, "vip")).isTrue();
    }

    @Test
    void windowSecondsReflectsConstructor() {
        assertThat(new ConfirmationTracker(7).windowSeconds()).isEqualTo(7);
    }
}
