package so.alaz.provouchers.storage;

import so.alaz.provouchers.stash.StashEntry;
import so.alaz.provouchers.stash.StashSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence gateway for ProVouchers, built on a {@link StorageProvider}. The
 * provider owns the connection pool, JDBC drivers, and migration runner; this class
 * registers the schema and runs the plain-JDBC queries the redeem pipeline needs.
 *
 * <p>The blocking query methods are intended to be called from an async context
 * (the async scheduler), never the main or a region thread.
 */
public final class VoucherStorage {

    private final StorageProvider provider;

    public VoucherStorage(StorageProvider provider) {
        this.provider = provider;
    }

    /** Opens the pool and applies pending migrations. */
    public CompletableFuture<Void> init() {
        provider.migrations()
            .register(new InitialSchema())
            .register(new CooldownSchema())
            .register(new UsedVoucherSchema())
            .register(new StashSchema());
        return provider.init().thenCompose(ignored -> provider.migrations().migrate())
            .thenApply(applied -> null);
    }

    /** Closes the pool. */
    public CompletableFuture<Void> shutdown() {
        return provider.shutdown();
    }

    /** Whether the connection pool is open (storage initialised without error). Non-blocking. */
    public boolean isReady() {
        try {
            provider.dataSource();
            return true;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    /** Whether a voucher item's unique id has already been redeemed. */
    public boolean isUsed(String uid) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT 1 FROM provouchers_used_vouchers WHERE uid = ?")) {
            statement.setString(1, uid);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    /** Records a unique id as redeemed. Returns {@code false} if it was already present. */
    public boolean recordUse(String uid, UUID player) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO provouchers_used_vouchers (uid, player_uuid, redeemed_at) "
                     + "VALUES (?, ?, ?)")) {
            statement.setString(1, uid);
            statement.setString(2, player.toString());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
            return true;
        } catch (SQLException ex) {
            if (isUsed(uid)) {
                return false;
            }
            throw ex;
        }
    }

    /**
     * How many times this player has redeemed this use key. A key is a code's literal
     * value, or {@code "voucher:<id>"} for an item voucher's use counters.
     */
    public int codeUsesByPlayer(String code, UUID player) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection()) {
            return codeUsesByPlayer(connection, code, player);
        }
    }

    /** How many times this use key (a code, or {@code "voucher:<id>"}) was redeemed by anyone. */
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

    /** Deletes every recorded use of this key (a code, or {@code "voucher:<id>"}). */
    public void clearUses(String key) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM provouchers_code_uses WHERE code = ?")) {
            statement.setString(1, key);
            statement.executeUpdate();
        }
    }

    /** Deletes one player's recorded uses of this key. */
    public void clearUses(String key, UUID player) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM provouchers_code_uses WHERE code = ? AND player_uuid = ?")) {
            statement.setString(1, key);
            statement.setString(2, player.toString());
            statement.executeUpdate();
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

    /** Stores (or replaces) a player's cooldown expiry for a voucher, in epoch millis. */
    public void setCooldown(UUID player, String voucherId, long expiresAt) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection()) {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE provouchers_cooldowns SET expires_at = ? WHERE player_uuid = ? "
                    + "AND voucher_id = ?")) {
                statement.setLong(1, expiresAt);
                statement.setString(2, player.toString());
                statement.setString(3, voucherId);
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO provouchers_cooldowns (player_uuid, voucher_id, expires_at) "
                        + "VALUES (?, ?, ?)")) {
                    statement.setString(1, player.toString());
                    statement.setString(2, voucherId);
                    statement.setLong(3, expiresAt);
                    statement.executeUpdate();
                }
            }
        }
    }

    /** A player's still-active cooldowns: voucher id to expiry epoch millis (after {@code now}). */
    public Map<String, Long> activeCooldowns(UUID player, long now) throws SQLException {
        Map<String, Long> cooldowns = new HashMap<>();
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT voucher_id, expires_at FROM provouchers_cooldowns WHERE player_uuid = ? "
                     + "AND expires_at > ?")) {
            statement.setString(1, player.toString());
            statement.setLong(2, now);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    cooldowns.put(result.getString(1), result.getLong(2));
                }
            }
        }
        return cooldowns;
    }

    /** Queues a virtual voucher in a player's stash. */
    public void addStash(StashEntry entry) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO provouchers_stash "
                     + "(id, player_uuid, voucher_id, amount, arg, source, created_at, expires_at) "
                     + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, entry.id().toString());
            statement.setString(2, entry.player().toString());
            statement.setString(3, entry.voucherId());
            statement.setInt(4, entry.amount());
            if (entry.arg() != null) {
                statement.setString(5, entry.arg());
            } else {
                statement.setNull(5, Types.VARCHAR);
            }
            statement.setString(6, entry.source().name());
            statement.setLong(7, entry.createdAt());
            if (entry.expiresAt() != null) {
                statement.setLong(8, entry.expiresAt());
            } else {
                statement.setNull(8, Types.BIGINT);
            }
            statement.executeUpdate();
        }
    }

    /**
     * Adds {@code entry}, merging its amount into an existing never-expiring entry for the same voucher
     * and argument so repeated gives show as one stack rather than many. Entries that expire are kept
     * separate so each keeps its own countdown.
     */
    public void addOrMergeStash(StashEntry entry) throws SQLException {
        if (entry.expiresAt() == null && mergeStash(entry)) {
            return;
        }
        addStash(entry);
    }

    /** Adds {@code entry}'s amount to a matching never-expiring row; returns whether one was found. */
    private boolean mergeStash(StashEntry entry) throws SQLException {
        String sql = "UPDATE provouchers_stash SET amount = amount + ? "
            + "WHERE player_uuid = ? AND voucher_id = ? AND expires_at IS NULL AND "
            + (entry.arg() != null ? "arg = ?" : "arg IS NULL");
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, entry.amount());
            statement.setString(2, entry.player().toString());
            statement.setString(3, entry.voucherId());
            if (entry.arg() != null) {
                statement.setString(4, entry.arg());
            }
            return statement.executeUpdate() > 0;
        }
    }

    /** A player's still-live (unexpired at {@code now}) stash entries, oldest first. */
    public List<StashEntry> listStash(UUID player, long now) throws SQLException {
        List<StashEntry> entries = new ArrayList<>();
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, voucher_id, amount, arg, source, created_at, expires_at "
                     + "FROM provouchers_stash WHERE player_uuid = ? "
                     + "AND (expires_at IS NULL OR expires_at > ?) ORDER BY created_at ASC")) {
            statement.setString(1, player.toString());
            statement.setLong(2, now);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long expiresValue = result.getLong("expires_at");
                    Long expiresAt = result.wasNull() ? null : expiresValue;
                    entries.add(new StashEntry(
                        UUID.fromString(result.getString("id")), player, result.getString("voucher_id"),
                        result.getInt("amount"), result.getString("arg"),
                        StashSource.fromStored(result.getString("source")),
                        result.getLong("created_at"), expiresAt));
                }
            }
        }
        return entries;
    }

    /**
     * Atomically claims one stash entry by id, removing it. Returns {@code true} only for the call
     * that removed it, so a double click or a second session cannot claim the same entry twice.
     */
    public boolean claimStash(UUID id) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM provouchers_stash WHERE id = ?")) {
            statement.setString(1, id.toString());
            return statement.executeUpdate() > 0;
        }
    }

    /** Removes every entry that has expired at or before {@code now}. Returns the count pruned. */
    public int pruneExpiredStash(long now) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM provouchers_stash WHERE expires_at IS NOT NULL AND expires_at <= ?")) {
            statement.setLong(1, now);
            return statement.executeUpdate();
        }
    }

    /** How many still-live (unexpired at {@code now}) entries a player has waiting. */
    public int countStash(UUID player, long now) throws SQLException {
        try (Connection connection = provider.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT COUNT(*) FROM provouchers_stash WHERE player_uuid = ? "
                     + "AND (expires_at IS NULL OR expires_at > ?)")) {
            statement.setString(1, player.toString());
            statement.setLong(2, now);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }
}
