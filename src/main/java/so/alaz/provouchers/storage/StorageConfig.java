package so.alaz.provouchers.storage;

import org.jetbrains.annotations.Nullable;

/**
 * Connection settings for the {@link StorageProvider}. Build via the static factories rather than
 * the constructor, e.g. {@code StorageConfig.sqlite(dataFolder + "/data.db")} or
 * {@code StorageConfig.mysql(host, port, db, user, pass, poolSize, poolName)}.
 */
public final class StorageConfig {

    final Backend backend;
    final String jdbcUrl;
    @Nullable final String username;
    @Nullable final String password;
    final int maxPoolSize;
    final String poolName;

    private StorageConfig(Backend backend, String jdbcUrl, @Nullable String username,
                          @Nullable String password, int maxPoolSize, String poolName) {
        this.backend = backend;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.maxPoolSize = maxPoolSize;
        this.poolName = poolName;
    }

    /** File-based SQLite. Pool size is fixed at 1 (SQLite is single-writer). */
    public static StorageConfig sqlite(String filePath) {
        return new StorageConfig(Backend.SQLITE, "jdbc:sqlite:" + filePath, null, null, 1, "provouchers-sqlite");
    }

    public static StorageConfig mysql(String host, int port, String database, String username,
                                      String password, int maxPoolSize, String poolName) {
        return new StorageConfig(Backend.MYSQL, "jdbc:mysql://" + host + ":" + port + "/" + database,
            username, password, maxPoolSize, poolName);
    }

    public static StorageConfig mariadb(String host, int port, String database, String username,
                                        String password, int maxPoolSize, String poolName) {
        return new StorageConfig(Backend.MARIADB, "jdbc:mariadb://" + host + ":" + port + "/" + database,
            username, password, maxPoolSize, poolName);
    }

    public static StorageConfig postgres(String host, int port, String database, String username,
                                         String password, int maxPoolSize, String poolName) {
        return new StorageConfig(Backend.POSTGRES, "jdbc:postgresql://" + host + ":" + port + "/" + database,
            username, password, maxPoolSize, poolName);
    }
}
