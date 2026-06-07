package so.alaz.provouchers.command;

import org.jetbrains.annotations.Nullable;

/**
 * Centralised command output: one prefix, one palette, and standardized
 * error/success/info/usage wording so every reply looks and reads the same.
 *
 * <p>Each method returns a MiniMessage string; pass it straight to
 * {@code CommandContext.reply}, which renders the markup.
 */
final class Messages {

    /** The branded prefix that leads every top-level reply. */
    private static final String PREFIX =
        "<gradient:#FFD700:#FF8A00><b>ProVouchers</b></gradient> <dark_gray>»</dark_gray> ";

    private Messages() {
    }

    /** A success reply (green). */
    static String success(String body) {
        return PREFIX + "<green>" + body;
    }

    /** An error reply (red). */
    static String error(String body) {
        return PREFIX + "<red>" + body;
    }

    /** A neutral, informational reply (gray). */
    static String info(String body) {
        return PREFIX + "<gray>" + body;
    }

    /** A heading reply (gold), used above multi-line output. */
    static String heading(String body) {
        return PREFIX + "<gold>" + body;
    }

    /** A usage hint shown when input is missing or malformed. */
    static String usage(String syntax) {
        return PREFIX + "<gray>Usage: <yellow>/voucher " + syntax + "</yellow>";
    }

    /**
     * A clickable help line: the syntax in gold, then the description. Clicking
     * fills the subcommand into the chat box so it is easy to complete.
     */
    static String helpLine(String name, @Nullable String syntax, @Nullable String description) {
        String shown = syntax != null ? syntax : name;
        String line = "<click:suggest_command:'/voucher " + name + " '>"
            + "<hover:show_text:'<gray>Click to fill in chat'>"
            + "<gold>/voucher " + shown + "</gold></hover></click>";
        if (description != null && !description.isEmpty()) {
            line += " <dark_gray>—</dark_gray> <gray>" + description + "</gray>";
        }
        return line;
    }
}
