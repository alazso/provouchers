package so.alaz.provouchers.storage;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The restart a use-count write makes when the database rolled it back to break a deadlock. */
class RollbackRetryTest {

    private final VoucherStorage storage =
        new VoucherStorage(new StorageProvider(StorageConfig.sqlite("unused.db")));

    @Test
    void recognisesARolledBackTransaction() {
        assertThat(VoucherStorage.rolledBack(new SQLTransactionRollbackException("deadlock"))).isTrue();
        assertThat(VoucherStorage.rolledBack(new SQLException("deadlock found", "40001"))).isTrue();
        assertThat(VoucherStorage.rolledBack(new SQLException("deadlock detected", "40P01"))).isTrue();
        assertThat(VoucherStorage.rolledBack(
            new SQLException("wrapped", "HY000", new SQLException("deadlock", "40001")))).isTrue();
    }

    @Test
    void leavesOtherFailuresAlone() {
        assertThat(VoucherStorage.rolledBack(new SQLException("duplicate key", "23000"))).isFalse();
        assertThat(VoucherStorage.rolledBack(new SQLException("no state"))).isFalse();
    }

    @Test
    void restartsUntilTheWriteSucceeds() throws SQLException {
        AtomicInteger attempts = new AtomicInteger();
        String result = storage.retryOnRollback(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new SQLTransactionRollbackException("deadlock");
            }
            return "written";
        });
        assertThat(result).isEqualTo("written");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void givesUpAfterTheLastAttemptAndReportsTheRollback() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> storage.retryOnRollback(() -> {
            attempts.incrementAndGet();
            throw new SQLTransactionRollbackException("deadlock");
        })).isInstanceOf(SQLTransactionRollbackException.class);
        assertThat(attempts).hasValue(4);
    }

    @Test
    void doesNotRestartAFailureTheDatabaseDidNotRollBack() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> storage.retryOnRollback(() -> {
            attempts.incrementAndGet();
            throw new SQLException("column not found", "42S22");
        })).isInstanceOf(SQLException.class);
        assertThat(attempts).hasValue(1);
    }
}
