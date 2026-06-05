package so.alaz.provouchers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProVouchersPluginTest {

    @Test
    void buildsAndRunsOnJava25OrNewer() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(25);
    }
}
