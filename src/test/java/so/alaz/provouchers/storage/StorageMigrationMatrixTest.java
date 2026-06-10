package so.alaz.provouchers.storage;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the schema migrations and a CRUD round-trip against real MySQL, MariaDB, and PostgreSQL
 * servers, so the portable DDL and queries are proven on every networked backend, not just SQLite.
 *
 * <p>The databases are ephemeral Docker containers. The whole class is skipped when no Docker
 * environment is available, so it exercises the matrix in CI (where Docker is present) and never
 * fails a Docker-less local build.
 */
@Testcontainers(disabledWithoutDocker = true)
class StorageMigrationMatrixTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Container
    private static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.4");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void mysqlMigratesAndRoundTrips() throws Exception {
        StorageScenario.migrateAndRoundTrip(StorageConfig.mysql(
            MYSQL.getHost(), MYSQL.getFirstMappedPort(), MYSQL.getDatabaseName(),
            MYSQL.getUsername(), MYSQL.getPassword(), 3, "test-mysql"));
    }

    @Test
    void mariadbMigratesAndRoundTrips() throws Exception {
        StorageScenario.migrateAndRoundTrip(StorageConfig.mariadb(
            MARIADB.getHost(), MARIADB.getFirstMappedPort(), MARIADB.getDatabaseName(),
            MARIADB.getUsername(), MARIADB.getPassword(), 3, "test-mariadb"));
    }

    @Test
    void postgresMigratesAndRoundTrips() throws Exception {
        StorageScenario.migrateAndRoundTrip(StorageConfig.postgres(
            POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), POSTGRES.getDatabaseName(),
            POSTGRES.getUsername(), POSTGRES.getPassword(), 3, "test-postgres"));
    }
}
