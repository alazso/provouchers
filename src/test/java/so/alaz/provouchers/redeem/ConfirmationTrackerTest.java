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
        // after confirming, a fresh click starts over
        assertThat(tracker.needsConfirm(player, "vip")).isTrue();
    }

    @Test
    void differentVoucherRestartsConfirmation() {
        ConfirmationTracker tracker = new ConfirmationTracker(5);
        assertThat(tracker.needsConfirm(player, "vip")).isTrue();
        assertThat(tracker.needsConfirm(player, "daily")).isTrue();  // switched voucher
        assertThat(tracker.needsConfirm(player, "daily")).isFalse(); // confirms daily
    }

    @Test
    void confirmationExpiresAfterWindow() {
        AtomicLong now = new AtomicLong(0);
        ConfirmationTracker tracker = new ConfirmationTracker(5, now::get); // 5s window
        assertThat(tracker.needsConfirm(player, "vip")).isTrue();           // pending until 5000ms
        now.set(6000);                                                      // window lapsed
        assertThat(tracker.needsConfirm(player, "vip")).isTrue();           // must confirm again
    }

    @Test
    void windowSecondsReflectsConstructor() {
        assertThat(new ConfirmationTracker(7).windowSeconds()).isEqualTo(7);
    }
}
