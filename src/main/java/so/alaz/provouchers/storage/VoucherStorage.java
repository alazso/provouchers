package so.alaz.provouchers.storage;

import so.alaz.strata.api.storage.StorageProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence gateway for ProVouchers, built on a Strata {@link StorageProvider}.
 * Strata owns the connection pool, the JDBC driver, and the migration runner; this
 * class registers the schema and runs the plain-JDBC queries the redeem pipeline
 * needs.
 *
 * <p>The blocking query methods are intended to be called from an async context
 * (Strata's async scheduler), never the main or a region thread.
 */
public final class VoucherStorage {

    private final StorageProvider provider;

    public VoucherStorage(StorageProvider provider) {
        this.provider = provider;
    }

    /** Opens the pool and applies pending migrations. */
    public CompletableFuture<Void> init() {
        provider.migrations().register(new InitialSchema());
        return provider.init().thenCompose(ignored -> provider.migrations().migrate())
            .thenApply(applied -> null);
    }

    /** Closes the pool. */
    public CompletableFuture<Void> shutdown() {
        return provider.shutdown();
    }

    /** Whether a voucher item stamp has already been redeemed. */
    public boolean isStampRedeemed(String batchId, String nonce) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT 1 FROM provouchers_redeemed_stamps WHERE batch_id = ? AND nonce = ?")) {
            statement.setString(1, batchId);
            statement.setString(2, nonce);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    /** Records a redeemed stamp. Returns {@code false} if it was already present. */
    public boolean recordStamp(String batchId, String nonce, UUID player) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO provouchers_redeemed_stamps (batch_id, nonce, player_uuid, redeemed_at) "
                     + "VALUES (?, ?, ?, ?)")) {
            statement.setString(1, batchId);
            statement.setString(2, nonce);
            statement.setString(3, player.toString());
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
            return true;
        } catch (SQLException ex) {
            if (isStampRedeemed(batchId, nonce)) {
                return false;
            }
            throw ex;
        }
    }

    /** How many times this player has redeemed this code. */
    public int codeUsesByPlayer(String code, UUID player) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection()) {
            return codeUsesByPlayer(connection, code, player);
        }
    }

    /** How many times this code has been redeemed across all players. */
    public long codeUsesTotal(String code) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT COALESCE(SUM(uses), 0) FROM provouchers_code_uses WHERE code = ?")) {
            statement.setString(1, code);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    /**
     * Increments a player's use count for a code by one and returns the new total.
     * Uses a read-then-write within a transaction so it stays portable across all
     * supported backends.
     */
    public int incrementCodeUse(String code, UUID player) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int current = codeUsesByPlayer(connection, code, player);
                int next = current + 1;
                String sql = current == 0
                    ? "INSERT INTO provouchers_code_uses (code, player_uuid, uses, updated_at) "
                        + "VALUES (?, ?, ?, ?)"
                    : "UPDATE provouchers_code_uses SET uses = ?, updated_at = ? "
                        + "WHERE code = ? AND player_uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    if (current == 0) {
                        statement.setString(1, code);
                        statement.setString(2, player.toString());
                        statement.setInt(3, next);
                        statement.setLong(4, System.currentTimeMillis());
                    } else {
                        statement.setInt(1, next);
                        statement.setLong(2, System.currentTimeMillis());
                        statement.setString(3, code);
                        statement.setString(4, player.toString());
                    }
                    statement.executeUpdate();
                }
                connection.commit();
                return next;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private int codeUsesByPlayer(Connection connection, String code, UUID player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT uses FROM provouchers_code_uses WHERE code = ? AND player_uuid = ?")) {
            statement.setString(1, code);
            statement.setString(2, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }
}
