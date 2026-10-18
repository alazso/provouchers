package so.alaz.provouchers.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MetricsTest {

    private final Metrics neither = new Metrics(null, null);

    @Test
    void reportsNothingStartedWhenNoProviderIsConfigured() {
        assertThat(neither.bStatsStarted()).isFalse();
        assertThat(neither.fastStatsSdk()).isNull();
        assertThat(neither.fastStatsEnabled()).isFalse();
        assertThat(neither.fastStatsSubmitting()).isFalse();
        assertThat(neither.fastStatsErrorTracking()).isFalse();
    }

    @Test
    void shutsDownWithoutProviders() {
        assertThatCode(neither::shutdown).doesNotThrowAnyException();
        assertThatCode(neither::shutdown).doesNotThrowAnyException();
    }
}
