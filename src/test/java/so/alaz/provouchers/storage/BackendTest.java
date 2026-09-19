package so.alaz.provouchers.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class BackendTest {

    @Test
    void everyBackendNamesALoadableDriverClass() {
        for (Backend backend : Backend.values()) {
            assertThatCode(() -> Class.forName(backend.driverClassName()))
                .as("driver class for %s", backend)
                .doesNotThrowAnyException();
        }
    }
}
