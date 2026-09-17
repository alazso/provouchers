package so.alaz.provouchers.metrics;

import so.alaz.provouchers.reward.RewardType;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe runtime activity counters for metrics. The redeem pipeline bumps
 * these on each outcome; the metrics charts read them on each submission.
 *
 * <p>Counters are monotonic session totals (reset only on plugin enable), never
 * drained on read. That keeps reads idempotent, which matters because the same
 * chart is polled independently by every enabled provider (bStats and FastStats):
 * a drain-on-read counter would split its count between whichever provider polled
 * first. Chart them with {@link MetricChart#counter}, which gives each provider its
 * own mark and reports the increase since that provider's last report.
 * {@link LongAdder} keeps the hot increment paths contention-free.
 */
public final class MetricCounters {

    private final LongAdder voucherRedemptions = new LongAdder();
    private final LongAdder codeRedemptions = new LongAdder();
    private final LongAdder duplicatesBlocked = new LongAdder();
    private final LongAdder conditionDenials = new LongAdder();
    private final LongAdder stashed = new LongAdder();
    private final LongAdder stashClaims = new LongAdder();
    private final LongAdder stashExpired = new LongAdder();
    private final Map<RewardType, LongAdder> rewardsGranted = new EnumMap<>(RewardType.class);

    public MetricCounters() {
        for (RewardType type : RewardType.values()) {
            rewardsGranted.put(type, new LongAdder());
        }
    }

    public void recordVoucherRedemption() {
        voucherRedemptions.increment();
    }

    public void recordCodeRedemption() {
        codeRedemptions.increment();
    }

    public void recordDuplicateBlocked() {
        duplicatesBlocked.increment();
    }

    public void recordConditionDenial() {
        conditionDenials.increment();
    }

    public void recordRewardGranted(RewardType type) {
        LongAdder adder = rewardsGranted.get(type);
        if (adder != null) {
            adder.increment();
        }
    }

    /** Records {@code amount} virtual vouchers queued into a player's Stash (stashgive, overflow, or API). */
    public void recordStashed(int amount) {
        stashed.add(amount);
    }

    /** Records {@code amount} virtual vouchers claimed from a Stash. */
    public void recordStashClaim(int amount) {
        stashClaims.add(amount);
    }

    /** Records {@code count} Stash entries swept by the expiry pruner. */
    public void recordStashExpired(int count) {
        stashExpired.add(count);
    }

    public long totalRedemptions() {
        return voucherRedemptions.sum() + codeRedemptions.sum();
    }

    public long voucherRedemptions() {
        return voucherRedemptions.sum();
    }

    public long codeRedemptions() {
        return codeRedemptions.sum();
    }

    public long duplicatesBlocked() {
        return duplicatesBlocked.sum();
    }

    public long conditionDenials() {
        return conditionDenials.sum();
    }

    public long stashed() {
        return stashed.sum();
    }

    public long stashClaims() {
        return stashClaims.sum();
    }

    public long stashExpired() {
        return stashExpired.sum();
    }

    /** The reward type granted most often this session, or {@code "none"} before any grant. */
    public String topRewardGranted() {
        RewardType top = null;
        long best = 0L;
        for (Map.Entry<RewardType, LongAdder> entry : rewardsGranted.entrySet()) {
            long value = entry.getValue().sum();
            if (value > best) {
                best = value;
                top = entry.getKey();
            }
        }
        return top == null ? "none" : top.name().toLowerCase(Locale.ROOT);
    }
}
