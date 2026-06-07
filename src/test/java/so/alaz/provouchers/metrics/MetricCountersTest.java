package so.alaz.provouchers.metrics;

import org.junit.jupiter.api.Test;
import so.alaz.provouchers.reward.RewardType;

import static org.assertj.core.api.Assertions.assertThat;

class MetricCountersTest {

    @Test
    void freshCountersAreZeroAndHaveNoTopReward() {
        MetricCounters counters = new MetricCounters();
        assertThat(counters.totalRedemptions()).isZero();
        assertThat(counters.voucherRedemptions()).isZero();
        assertThat(counters.codeRedemptions()).isZero();
        assertThat(counters.duplicatesBlocked()).isZero();
        assertThat(counters.conditionDenials()).isZero();
        assertThat(counters.topRewardGranted()).isEqualTo("none");
    }

    @Test
    void totalRedemptionsSumsVoucherAndCode() {
        MetricCounters counters = new MetricCounters();
        counters.recordVoucherRedemption();
        counters.recordVoucherRedemption();
        counters.recordCodeRedemption();
        assertThat(counters.voucherRedemptions()).isEqualTo(2);
        assertThat(counters.codeRedemptions()).isEqualTo(1);
        assertThat(counters.totalRedemptions()).isEqualTo(3);
    }

    @Test
    void blocksAndDenialsCountIndependentlyOfRedemptions() {
        MetricCounters counters = new MetricCounters();
        counters.recordDuplicateBlocked();
        counters.recordConditionDenial();
        counters.recordConditionDenial();
        assertThat(counters.duplicatesBlocked()).isEqualTo(1);
        assertThat(counters.conditionDenials()).isEqualTo(2);
        assertThat(counters.totalRedemptions()).isZero();
    }

    @Test
    void topRewardGrantedIsTheMostFrequentType() {
        MetricCounters counters = new MetricCounters();
        counters.recordRewardGranted(RewardType.ITEM);
        counters.recordRewardGranted(RewardType.ITEM);
        counters.recordRewardGranted(RewardType.ITEM);
        counters.recordRewardGranted(RewardType.CURRENCY);
        counters.recordRewardGranted(RewardType.MESSAGE);
        assertThat(counters.topRewardGranted()).isEqualTo("item");
    }

    @Test
    void topRewardGrantedReportsLowerCaseEnumName() {
        MetricCounters counters = new MetricCounters();
        counters.recordRewardGranted(RewardType.CONSOLE_COMMAND);
        assertThat(counters.topRewardGranted()).isEqualTo("console_command");
    }

    @Test
    void readsAreIdempotentAndDoNotResetCounters() {
        MetricCounters counters = new MetricCounters();
        counters.recordVoucherRedemption();
        assertThat(counters.totalRedemptions()).isEqualTo(1);
        // Reading again must not drain the counter: both metric providers poll it.
        assertThat(counters.totalRedemptions()).isEqualTo(1);
        assertThat(counters.voucherRedemptions()).isEqualTo(1);
    }
}
