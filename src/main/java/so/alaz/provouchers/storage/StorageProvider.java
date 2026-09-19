package so.alaz.provouchers.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns the HikariCP connection pool for the plugin's database. Lifecycle work and migrations run on a
 * single dedicated daemon thread, never a server thread.
 */
public final class StorageProvider {

    private final StorageConfig config;
    private final ExecutorService executor;
    private final MigrationRunner runner;

    @Nullable
    private volatile HikariDataSource dataSource;

    /** Set by {@link #shutdown()}; read and written only on the lifecycle thread. */
    private boolean closed;

    public StorageProvider(StorageConfig config) {
        this.config = config;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "provouchers-storage-" + config.poolName);
            thread.setDaemon(true);
            return thread;
        });
        this.runner = new MigrationRunner(this, executor);
    }

    /**
     * Opens the connection pool, creating the SQLite file's parent directories if needed. Idempotent.
     * Fails once {@link #shutdown()} has run.
     */
    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            if (closed) {
                throw new IllegalStateException("StorageProvider was shut down");
            }
            if (dataSource != null) {
                return;
            }
            if (config.backend == Backend.SQLITE) {
                String path = config.jdbcUrl.substring("jdbc:sqlite:".length());
                File parent = new File(path).getAbsoluteFile().getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
            }
            HikariConfig hikari = new HikariConfig();
            hikari.setJdbcUrl(config.jdbcUrl);
            hikari.setDriverClassName(config.backend.driverClassName());
            hikari.setUsername(config.username);
            hikari.setPassword(config.password);
            hikari.setMaximumPoolSize(config.maxPoolSize);
            hikari.setPoolName(config.poolName);
            dataSource = new HikariDataSource(hikari);
        }, executor);
    }

    /** Closes the pool and releases resources. Idempotent. */
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            closed = true;
            HikariDataSource current = dataSource;
            if (current != null) {
                current.close();
                dataSource = null;
            }
        }, executor);
    }

    /** The pooled data source. Only valid between {@link #init()} and {@link #shutdown()}. */
    public DataSource dataSource() {
        HikariDataSource current = dataSource;
        if (current == null) {
            throw new IllegalStateException("StorageProvider not initialised; call init() first");
        }
        return current;
    }

    public MigrationRunner migrations() {
        return runner;
    }
}
