package so.alaz.provouchers;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The ComfiConf integration stays optional: nothing loaded at startup names a ComfiConf API type. */
class ComfiConfHookTest {

    private static final String API = "com/comficonf/api";
    private static final String INTEGRATION = "so/alaz/provouchers/comficonf/";

    @Test
    void mainClassNamesTheIntegrationButNotTheApi() throws Exception {
        byte[] bytes;
        try (InputStream in = ProVouchersPlugin.class.getResourceAsStream("ProVouchersPlugin.class")) {
            assertThat(in).isNotNull();
            bytes = in.readAllBytes();
        }
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(content).contains(INTEGRATION + "ComfiConfIntegration");
        assertThat(content).doesNotContain(API);
    }

    @Test
    void onlyTheIntegrationPackageNamesTheApi() throws Exception {
        Path classes = Path.of(ProVouchersPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<Path> files;
        try (Stream<Path> walk = Files.walk(classes)) {
            files = walk.filter(path -> path.toString().endsWith(".class")).toList();
        }
        assertThat(files).isNotEmpty();
        for (Path file : files) {
            String relative = classes.relativize(file).toString().replace('\\', '/');
            if (!relative.startsWith(INTEGRATION)) {
                assertThat(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1))
                    .as(relative).doesNotContain(API);
            }
        }
    }

    @Test
    void outdatedBridgeMessageNamesTheInstalledVersionAndTheMissingClass() {
        String message = ProVouchersPlugin.incompatibleBridgeMessage("0.1.0",
            new NoClassDefFoundError("com/comficonf/api/ConfigProvider"));
        assertThat(message).startsWith("The installed ComfiConf 0.1.0 does not include")
            .contains("com.comficonf.api.ConfigProvider")
            .contains("Update ComfiConf");
    }

    @Test
    void outdatedBridgeMessageWithoutDetailNamesTheErrorType() {
        String message = ProVouchersPlugin.incompatibleBridgeMessage("?", new NoClassDefFoundError());
        assertThat(message).contains("(NoClassDefFoundError)");
    }
}
