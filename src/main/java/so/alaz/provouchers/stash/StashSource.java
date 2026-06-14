package so.alaz.provouchers.stash;

/**
 * Where a {@link StashEntry} came from, kept for auditing and to let the GUI explain why a
 * reward is waiting. Stored as its name; an unrecognised stored value reads back as {@link #API}.
 */
public enum StashSource {

    /** Queued because the recipient was offline when the voucher was given. */
    OFFLINE_GIVE,
    /** Queued because a give did not fit the player's inventory. */
    OVERFLOW,
    /** Queued by a player converting a held physical voucher. */
    CONVERT,
    /** Queued by an admin command. */
    ADMIN,
    /** Queued through the public API. */
    API;

    /** Resolves a stored source name, falling back to {@link #API} for an unknown value. */
    public static StashSource fromStored(String value) {
        if (value == null) {
            return API;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return API;
        }
    }
}
