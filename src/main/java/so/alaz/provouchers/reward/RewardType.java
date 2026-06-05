package so.alaz.provouchers.reward;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The kinds of reward action a voucher can run. Each reward line in config is
 * written as {@code "<type>: <payload>"}; the leading keyword maps to one of
 * these values. Several aliases map to the same type for operator convenience.
 */
public enum RewardType {

    /** Run a command from the server console. */
    CONSOLE_COMMAND,
    /** Run a command as the redeeming player. */
    PLAYER_COMMAND,
    /** Send a private message to the redeeming player. */
    MESSAGE,
    /** Broadcast a message to the whole server. */
    BROADCAST,
    /** Show a title (and optional subtitle, separated by {@code |}) to the player. */
    TITLE,
    /** Show an action bar message to the player. */
    ACTIONBAR,
    /** Play a sound (a namespaced key) to the player. */
    SOUND;

    /**
     * Resolves a reward keyword (case-insensitive) to its type, or {@code null}
     * if the keyword is not recognised.
     */
    @Nullable
    public static RewardType fromKeyword(String keyword) {
        return switch (keyword.trim().toLowerCase(Locale.ROOT)) {
            case "command", "console-command", "console" -> CONSOLE_COMMAND;
            case "player-command", "p-command", "run-as-player" -> PLAYER_COMMAND;
            case "message", "msg", "tell" -> MESSAGE;
            case "broadcast", "announce" -> BROADCAST;
            case "title" -> TITLE;
            case "actionbar", "action-bar" -> ACTIONBAR;
            case "sound" -> SOUND;
            default -> null;
        };
    }
}
