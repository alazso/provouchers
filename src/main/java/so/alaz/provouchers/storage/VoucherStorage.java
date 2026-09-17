package so.alaz.provouchers.storage;

import org.jetbrains.annotations.Nullable;
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

    /** A cap for {@link #reserveUses} that leaves the count unlimited. */
    public static final int UNCAPPED = -1;

    /**
     * How many times in a row a use-count write retries after concurrent writers moved the count it
     * read. Each retry means another write committed, so this only bounds pathological contention.
     */
    private static final int MAX_WRITE_ATTEMPTS = 32;

    private final StorageProvider provider;

    /**
     * Set once every migration has applied, and cleared on shutdown. The pool opens before the
     * migrations run, so queries wait for this instead: a query against a half-migrated schema could
     * write rows a migration is about to create (a use total the backfill then collides with).
     */
    private volatile boolean ready;

    public VoucherStorage(StorageProvider provider) {
        this.provider = provider;
    }

    /** Opens the pool and applies pending migrations, then admits queries. */
    public CompletableFuture<Void> init() {
        provider.migrations()
            .register(new InitialSchema())
            .register(new CooldownSchema())
            .register(new UsedVoucherSchema())
            .register(new StashSchema())
            .register(new UseTotalsSchema());
        return provider.init().thenCompose(ignored -> provider.migrations().migrate())
            .thenApply(applied -> {
                ready = true;
                return null;
            });
    }

    /** Refuses further queries and closes the pool. */
    public CompletableFuture<Void> shutdown() {
        ready = false;
        return provider.shutdown();
    }

    /**
     * Whether storage is open and fully migrated, so queries are admitted. Non-blocking. Stays
     * {@code false} when opening or a migration failed, which leaves every query refused.
     */
    public boolean isReady() {
        return ready;
    }

    /** A pooled connection, refused with an {@link SQLException} until storage is ready. */
    private Connection connection() throws SQLException {
        if (!ready) {
            throw new SQLException("ProVouchers storage is not ready: it is still opening, failed to open, "
                + "or has shut down");
        }
        return provider.dataSource().getConnection();
    }

    /** Whether a voucher item's unique id has already been redeemed. */
    public boolean isUsed(String uid) throws SQLException {
        try (Connection connection = connection();
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
        try (Connection connection = connection();
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
     * Removes a unique id recorded by {@link #recordUse} for {@code player}, making the item redeemable
     * again. Used when a redemption that claimed the id is abandoned before its reward is delivered.
     * Matching the player too means a stale release can never free an id another player claimed.
     * Returns whether a row was removed.
     */
    public boolean deleteUse(String uid, UUID player) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM provouchers_used_vouchers WHERE uid = ? AND player_uuid = ?")) {
            statement.setString(1, uid);
            statement.setString(2, player.toString());
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * How many times this player has redeemed this use key. A key is a code's literal
     * value, or {@code "voucher:<id>"} for an item voucher's use counters.
     */
    public int codeUsesByPlayer(String code, UUID player) throws SQLException {
        try (Connection connection = connection()) {
            return codeUsesByPlayer(connection, code, player);
        }
    }

    /** How many times this use key (a code, or {@code "voucher:<id>"}) was redeemed by anyone. */
    public long codeUsesTotal(String code) throws SQLException {
        try (Connection connection = connection()) {
            Long total = totalUses(connection, code);
            return total != null ? total : 0L;
        }
    }

    /** Which cap held a {@link UseReservation} below the requested count. */
    public enum UseLimit {
        /** Every requested use was reserved. */
        NONE,
        /** The player's own cap ran out. */
        PER_PLAYER,
        /** The global cap across all players ran out. */
        GLOBAL
    }

    /**
     * The outcome of {@link #reserveUses}: how many uses were reserved (zero when denied), and the cap
     * that held the reservation below the request, or {@link UseLimit#NONE} when all were reserved.
     */
    public record UseReservation(int reserved, UseLimit limit) {
    }

    /**
     * Atomically reserves up to {@code requested} uses of {@code key} for {@code player}: as many as
     * both caps still allow, where a negative cap ({@link #UNCAPPED}) leaves that count unlimited. The
     * player's count and the key's total move together in one transaction, or not at all.
     *
     * <p>Each update is conditional ({@code uses + n <= cap}), so the row lock the database takes for
     * it settles a race: of two reservations for the last slot, the later one re-evaluates its condition
     * against the earlier one's committed count and matches no row. A condition that fails after the
     * allowance was read means a concurrent reservation, release, or clear moved it, so the transaction
     * rolls back and the allowance is read again. Both rows are created beforehand in autocommit, so a
     * duplicate insert from a concurrent first use fails on its own instead of aborting the transaction
     * (as PostgreSQL would). Every writer locks a player's row before the key's total, so a
     * reservation, release, or clear cannot deadlock another over that pair.
     *
     * <p>Blocking; call from an async context. Undo with {@link #releaseUses}.
     */
    public UseReservation reserveUses(String key, UUID player, int requested, int perPlayerCap, int globalCap)
        throws SQLException {
        if (requested < 1) {
            throw new IllegalArgumentException("requested must be at least 1, was " + requested);
        }
        try (Connection connection = connection()) {
            for (int attempt = 0; attempt < MAX_WRITE_ATTEMPTS; attempt++) {
                long mine = ensurePlayerUses(connection, key, player);
                long total = ensureTotalUses(connection, key);
                long perPlayerLeft = perPlayerCap < 0 ? Long.MAX_VALUE : perPlayerCap - mine;
                long globalLeft = globalCap < 0 ? Long.MAX_VALUE : globalCap - total;
                int grant = (int) Math.max(0, Math.min(requested, Math.min(perPlayerLeft, globalLeft)));
                UseLimit limit = perPlayerLeft < requested ? UseLimit.PER_PLAYER
                    : globalLeft < requested ? UseLimit.GLOBAL
                    : UseLimit.NONE;
                if (grant == 0 || addUses(connection, key, player, grant, perPlayerCap, globalCap)) {
                    return new UseReservation(grant, limit);
                }
            }
        }
        throw contended("reserve", key);
    }

    /**
     * Gives back up to {@code count} uses of {@code key} reserved for {@code player}, taking the same
     * amount off the key's total, and returns how many were released. Never takes a count below zero:
     * if the player's uses were cleared in the meantime, only what is left is released.
     *
     * <p>Blocking; call from an async context.
     */
    public int releaseUses(String key, UUID player, int count) throws SQLException {
        if (count < 1) {
            return 0;
        }
        try (Connection connection = connection()) {
            for (int attempt = 0; attempt < MAX_WRITE_ATTEMPTS; attempt++) {
                Integer mine = playerUses(connection, key, player);
                int release = mine == null ? 0 : Math.min(count, mine);
                if (release == 0 || subtractUses(connection, key, player, release)) {
                    return release;
                }
            }
        }
        throw contended("release", key);
    }

    /** Deletes every recorded use of this key (a code, or {@code "voucher:<id>"}) and its total. */
    public void clearUses(String key) throws SQLException {
        try (Connection connection = connection()) {
            inTransaction(connection, () -> {
                try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM provouchers_code_uses WHERE code = ?")) {
                    statement.setString(1, key);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM provouchers_use_totals WHERE code = ?")) {
                    statement.setString(1, key);
                    statement.executeUpdate();
                }
                return true;
            });
        }
    }

    /** Deletes one player's recorded uses of this key, taking them off the key's total. */
    public void clearUses(String key, UUID player) throws SQLException {
        try (Connection connection = connection()) {
            for (int attempt = 0; attempt < MAX_WRITE_ATTEMPTS; attempt++) {
                Integer mine = playerUses(connection, key, player);
                if (mine == null || deletePlayerUses(connection, key, player, mine)) {
                    return;
                }
            }
        }
        throw contended("clear", key);
    }

    /**
     * Adds {@code amount} to the player's count and the key's total in one transaction, each update
     * guarded by its cap when capped. Returns {@code false}, with nothing changed, when either update
     * matched no row: its cap no longer fits, or a concurrent clear deleted the row.
     */
    private static boolean addUses(Connection connection, String key, UUID player, int amount,
                                   int perPlayerCap, int globalCap) throws SQLException {
        return inTransaction(connection, () -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE provouchers_code_uses SET uses = uses + ?, updated_at = ? WHERE code = ? AND player_uuid = ?"
                    + (perPlayerCap < 0 ? "" : " AND uses + ? <= ?"))) {
                statement.setInt(1, amount);
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, key);
                statement.setString(4, player.toString());
                if (perPlayerCap >= 0) {
                    statement.setInt(5, amount);
                    statement.setInt(6, perPlayerCap);
                }
                if (statement.executeUpdate() == 0) {
                    return false;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE provouchers_use_totals SET uses = uses + ? WHERE code = ?"
                    + (globalCap < 0 ? "" : " AND uses + ? <= ?"))) {
                statement.setLong(1, amount);
                statement.setString(2, key);
                if (globalCap >= 0) {
                    statement.setLong(3, amount);
                    statement.setLong(4, globalCap);
                }
                return statement.executeUpdate() > 0;
            }
        });
    }

    /**
     * Takes {@code amount} off the player's count and the key's total in one transaction. The player's
     * update requires {@code uses >= amount} and returns {@code false}, with nothing changed, when a
     * concurrent release or clear lowered the count after it was read.
     */
    private static boolean subtractUses(Connection connection, String key, UUID player, int amount)
        throws SQLException {
        return inTransaction(connection, () -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE provouchers_code_uses SET uses = uses - ?, updated_at = ? "
                    + "WHERE code = ? AND player_uuid = ? AND uses >= ?")) {
                statement.setInt(1, amount);
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, key);
                statement.setString(4, player.toString());
                statement.setInt(5, amount);
                if (statement.executeUpdate() == 0) {
                    return false;
                }
            }
            subtractTotal(connection, key, amount);
            return true;
        });
    }

    /**
     * Deletes the player's row only while it still holds {@code uses}, taking that many off the total in
     * the same transaction. Returns {@code false}, with nothing changed, when a concurrent writer moved
     * the count after it was read.
     */
    private static boolean deletePlayerUses(Connection connection, String key, UUID player, int uses)
        throws SQLException {
        return inTransaction(connection, () -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM provouchers_code_uses WHERE code = ? AND player_uuid = ? AND uses = ?")) {
                statement.setString(1, key);
                statement.setString(2, player.toString());
                statement.setInt(3, uses);
                if (statement.executeUpdate() == 0) {
                    return false;
                }
            }
            subtractTotal(connection, key, uses);
            return true;
        });
    }

    /**
     * Takes {@code amount} off the key's total, clamped at zero so a total that fell behind the player
     * counts (for example one written by an older version sharing the database) never goes negative.
     */
    private static void subtractTotal(Connection connection, String key, long amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE provouchers_use_totals SET uses = CASE WHEN uses >= ? THEN uses - ? ELSE 0 END "
                + "WHERE code = ?")) {
            statement.setLong(1, amount);
            statement.setLong(2, amount);
            statement.setString(3, key);
            statement.executeUpdate();
        }
    }

    /**
     * The player's recorded uses of {@code key}, first inserting a zero row when there is none so the
     * conditional update has a row to match. Runs in autocommit: a concurrent first use may insert the
     * same row, and that duplicate is detected by reading again, like {@link #recordUse}.
     */
    private static int ensurePlayerUses(Connection connection, String key, UUID player) throws SQLException {
        Integer uses = playerUses(connection, key, player);
        if (uses != null) {
            return uses;
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO provouchers_code_uses (code, player_uuid, uses, updated_at) VALUES (?, ?, 0, ?)")) {
            statement.setString(1, key);
            statement.setString(2, player.toString());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
            return 0;
        } catch (SQLException ex) {
            Integer raced = playerUses(connection, key, player);
            if (raced != null) {
                return raced;
            }
            throw ex;
        }
    }

    /** The key's total, first inserting a zero row when there is none; see {@link #ensurePlayerUses}. */
    private static long ensureTotalUses(Connection connection, String key) throws SQLException {
        Long total = totalUses(connection, key);
        if (total != null) {
            return total;
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO provouchers_use_totals (code, uses) VALUES (?, 0)")) {
            statement.setString(1, key);
            statement.executeUpdate();
            return 0L;
        } catch (SQLException ex) {
            Long raced = totalUses(connection, key);
            if (raced != null) {
                return raced;
            }
            throw ex;
        }
    }

    private int codeUsesByPlayer(Connection connection, String code, UUID player) throws SQLException {
        Integer uses = playerUses(connection, code, player);
        return uses != null ? uses : 0;
    }

    /** The player's recorded uses of {@code key}, or {@code null} when no row exists. */
    @Nullable
    private static Integer playerUses(Connection connection, String key, UUID player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT uses FROM provouchers_code_uses WHERE code = ? AND player_uuid = ?")) {
            statement.setString(1, key);
            statement.setString(2, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : null;
            }
        }
    }

    /** The key's total uses, or {@code null} when no row exists. */
    @Nullable
    private static Long totalUses(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT uses FROM provouchers_use_totals WHERE code = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : null;
            }
        }
    }

    /** A unit of transactional work: returns {@code true} to commit or {@code false} to roll back. */
    @FunctionalInterface
    private interface TransactionBody {
        boolean run() throws SQLException;
    }

    /**
     * Runs {@code body} in a transaction on {@code connection}, committing when it returns {@code true}
     * and rolling back when it returns {@code false} or throws. Returns what {@code body} returned.
     */
    private static boolean inTransaction(Connection connection, TransactionBody body) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            boolean commit = body.run();
            if (commit) {
                connection.commit();
            } else {
                connection.rollback();
            }
            return commit;
        } catch (SQLException | RuntimeException ex) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
            }
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /** The failure raised when a use-count write keeps losing to concurrent writers. */
    private static SQLException contended(String operation, String key) {
        return new SQLException("Could not " + operation + " uses of '" + key + "': "
            + MAX_WRITE_ATTEMPTS + " attempts in a row conflicted with concurrent writes");
    }

    /** Stores (or replaces) a player's cooldown expiry for a voucher, in epoch millis. */
    public void setCooldown(UUID player, String voucherId, long expiresAt) throws SQLException {
        try (Connection connection = connection()) {
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
        try (Connection connection = connection();
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

    /**
     * Queues a virtual voucher in a player's stash as its own row. Rows are never merged or updated
     * afterwards (the menu groups matching entries for display), so concurrent deliveries cannot
     * inflate one another and a row still holds the amount it was read with when a claim removes it.
     */
    public void addStash(StashEntry entry) throws SQLException {
        try (Connection connection = connection();
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
     * Puts a claimed entry back exactly as it was read: same id, amount, argument, source, and
     * timestamps. Returns {@code false} if a row with its id already exists, so the primary key turns a
     * second restore of one entry into a no-op rather than a duplicate reward.
     */
    public boolean restoreStash(StashEntry entry) throws SQLException {
        try {
            addStash(entry);
            return true;
        } catch (SQLException ex) {
            // Checked after the failed insert released its connection (the SQLite pool holds just one).
            if (hasStash(entry.id())) {
                return false;
            }
            throw ex;
        }
    }

    private boolean hasStash(UUID id) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT 1 FROM provouchers_stash WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    /** A player's still-live (unexpired at {@code now}) stash entries, oldest first. */
    public List<StashEntry> listStash(UUID player, long now) throws SQLException {
        List<StashEntry> entries = new ArrayList<>();
        try (Connection connection = connection();
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
     * Atomically claims one stash entry, removing it only while it still holds the amount it was read
     * with. Returns {@code true} only for the call that removed it, so a double click or a second
     * session cannot claim the same entry twice. A menu card claims each of its rows this way, so
     * overlapping claims split the rows between them. This version never updates a row after insert;
     * the amount check refuses a row that an older server version sharing the database merged a
     * delivery into after it was read, so the stale amount is never granted for the larger row.
     */
    public boolean claimStash(StashEntry entry) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM provouchers_stash WHERE id = ? AND amount = ?")) {
            statement.setString(1, entry.id().toString());
            statement.setInt(2, entry.amount());
            return statement.executeUpdate() > 0;
        }
    }

    /** Removes every entry that has expired at or before {@code now}. Returns the count pruned. */
    public int pruneExpiredStash(long now) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM provouchers_stash WHERE expires_at IS NOT NULL AND expires_at <= ?")) {
            statement.setLong(1, now);
            return statement.executeUpdate();
        }
    }
}
