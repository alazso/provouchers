package so.alaz.provouchers.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class BackendTest {

    /**
     * Each backend must name a driver class that actually resolves. The pool sets this class
     * explicitly (drivers live on the plugin classloader, invisible to DriverManager), so a typo
     * would only surface at runtime as a failed connection. This catches it without Docker.
     */
    @Test
    void everyBackendNamesALoadableDriverClass() {
        for (Backend backend : Backend.values()) {
            assertThatCode(() -> Class.forName(backend.driverClassName()))
                .as("driver class for %s", backend)
                .doesNotThrowAnyException();
        }
    }
}
