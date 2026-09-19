package so.alaz.provouchers.metrics;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CounterMarkTest {

    private final AtomicLong total = new AtomicLong();
    private final CounterMark mark = new CounterMark(total::get);

    @Test
    void takeDeltaReportsTheIncreaseSinceTheLastRead() {
        total.set(5);
        assertThat(mark.takeDelta()).isEqualTo(5);
        total.set(8);
        assertThat(mark.takeDelta()).isEqualTo(3);
        assertThat(mark.takeDelta()).isZero();
    }

    @Test
    void peekDeltaAdvancesOnlyOnCommit() {
        total.set(5);
        assertThat(mark.peekDelta()).isEqualTo(5);
        assertThat(mark.peekDelta()).isEqualTo(5);
        mark.commit();
        total.set(7);
        assertThat(mark.peekDelta()).isEqualTo(2);
    }

    @Test
    void uncommittedReadKeepsTheCountForTheNextReport() {
        total.set(5);
        mark.peekDelta();
        total.set(9);
        assertThat(mark.peekDelta()).isEqualTo(9);
        mark.commit();
        assertThat(mark.peekDelta()).isZero();
    }

    @Test
    void readOnAnotherThreadIsNotCommittedByThisThread() throws InterruptedException {
        total.set(5);
        mark.peekDelta();
        total.set(9);
        Thread errorReport = new Thread(mark::peekDelta);
        errorReport.start();
        errorReport.join();
        mark.commit();
        assertThat(mark.peekDelta()).isEqualTo(4);
    }

    @Test
    void commitWithoutAReadChangesNothing() {
        total.set(6);
        mark.commit();
        assertThat(mark.peekDelta()).isEqualTo(6);
    }

    @Test
    void commitNeverMovesTheMarkBackwards() throws InterruptedException {
        total.set(5);
        mark.peekDelta();
        total.set(10);
        Thread other = new Thread(() -> {
            mark.peekDelta();
            mark.commit();
        });
        other.start();
        other.join();
        mark.commit();
        assertThat(mark.peekDelta()).isZero();
    }
}
