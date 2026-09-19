package so.alaz.provouchers.stash;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One virtual (itemless) voucher waiting in a player's Stash: a deferred grant of {@code amount}
 * copies of voucher {@code voucherId}, claimed through the Stash GUI. The entry references the voucher
 * by id, so a claim uses the voucher's current config. The entry is the single-use claim token; claims
 * carry no anti-dupe stamp.
 *
 * @param id        the unique entry id, also the claim token
 * @param player    the owning player
 * @param voucherId the voucher this entry grants
 * @param amount    how many copies to grant, at least 1
 * @param arg       an optional argument passed to the voucher's rewards as {@code %arg%}
 * @param source    where the entry came from
 * @param createdAt when the entry was queued (epoch millis)
 * @param expiresAt when the entry lapses (epoch millis), or {@code null} to never expire
 */
public record StashEntry(UUID id, UUID player, String voucherId, int amount,
                         @Nullable String arg, StashSource source, long createdAt,
                         @Nullable Long expiresAt) {

    public StashEntry {
        if (id == null) {
            throw new IllegalArgumentException("stash entry needs an id");
        }
        if (player == null) {
            throw new IllegalArgumentException("stash entry needs a player");
        }
        if (voucherId == null || voucherId.isBlank()) {
            throw new IllegalArgumentException("stash entry needs a voucher id");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("stash amount must be at least 1, was " + amount);
        }
        if (source == null) {
            throw new IllegalArgumentException("stash entry needs a source");
        }
    }

    /** Whether this entry has reached its expiry at {@code now} (epoch millis); never-expiring entries are always live. */
    public boolean isExpired(long now) {
        return expiresAt != null && now >= expiresAt;
    }
}
