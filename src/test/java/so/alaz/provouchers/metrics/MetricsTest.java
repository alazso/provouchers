package so.alaz.provouchers.metrics;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
        assertThatCode(() -> neither.shutdown(Duration.ofSeconds(1))).doesNotThrowAnyException();
        assertThatCode(() -> neither.shutdown(Duration.ofSeconds(1))).doesNotThrowAnyException();
    }

    @Test
    void boundedRunReportsATaskThatFinishesOnADaemonThread() throws InterruptedException {
        AtomicBoolean ranOnDaemon = new AtomicBoolean();

        boolean finished = Metrics.runBounded("metrics-test-quick",
            () -> ranOnDaemon.set(Thread.currentThread().isDaemon()), Duration.ofSeconds(10));

        assertThat(finished).isTrue();
        assertThat(ranOnDaemon).isTrue();
    }

    @Test
    void boundedRunStopsWaitingForAStalledTaskAtTheLimit() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        try {
            long start = System.nanoTime();

            boolean finished = Metrics.runBounded("metrics-test-stalled", () -> {
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, Duration.ofMillis(200));

            assertThat(finished).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
        } finally {
            release.countDown();
        }
    }
}
