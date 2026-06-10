package so.alaz.provouchers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against the runtime libraries in {@link ProVouchersLoader} drifting from the version
 * catalog: the loader is hardcoded (it must not reference the catalog, which is a build-time
 * artifact), so this test fails the build if a coordinate's version no longer matches
 * {@code gradle/libs.versions.toml}.
 */
class ProVouchersLoaderTest {

    /** Each loader library's {@code group:artifact} mapped to its catalog {@code [versions]} key. */
    private static final Map<String, String> MODULE_TO_VERSION_KEY = Map.of(
        "com.zaxxer:HikariCP", "hikari",
        "org.xerial:sqlite-jdbc", "sqlite",
        "com.mysql:mysql-connector-j", "mysql",
        "org.mariadb.jdbc:mariadb-java-client", "mariadb",
        "org.postgresql:postgresql", "postgresql");

    @Test
    void loaderCoordinatesMatchTheVersionCatalog() throws IOException {
        Map<String, String> catalog = catalogVersions();
        assertThat(ProVouchersLoader.LIBRARIES).isNotEmpty();
        for (String coordinates : ProVouchersLoader.LIBRARIES) {
            int lastColon = coordinates.lastIndexOf(':');
            String module = coordinates.substring(0, lastColon);
            String version = coordinates.substring(lastColon + 1);
            String key = MODULE_TO_VERSION_KEY.get(module);
            assertThat(key).as("loader library '%s' is not mapped in the test", module).isNotNull();
            assertThat(version)
                .as("loader version of '%s' must match catalog version '%s'", module, key)
                .isEqualTo(catalog.get(key));
        }
    }

    private static Map<String, String> catalogVersions() throws IOException {
        Pattern entry = Pattern.compile("^([\\w-]+)\\s*=\\s*\"([^\"]+)\"");
        Map<String, String> versions = new HashMap<>();
        boolean inVersions = false;
        for (String line : Files.readAllLines(Path.of("gradle", "libs.versions.toml"))) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[")) {
                inVersions = trimmed.equals("[versions]");
                continue;
            }
            if (inVersions) {
                Matcher matcher = entry.matcher(trimmed);
                if (matcher.find()) {
                    versions.put(matcher.group(1), matcher.group(2));
                }
            }
        }
        return versions;
    }
}
