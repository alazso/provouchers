package so.alaz.provouchers.comficonf;

import com.comficonf.api.bukkit.SectionMapper;
import com.comficonf.api.schema.ConfigField;
import com.comficonf.api.schema.ConfigSchema;
import com.comficonf.api.schema.VisibilityCondition;

/**
 * Editable fields of config.yml. Undeclared keys ({@code cooldown.tiers}, whose keys are tier names) are never sent
 * to ComfiConf and are kept on every write.
 *
 * <p>config.yml files written by older ProVouchers versions can lack every key except {@code storage.*} and
 * {@code anti-dupe.remove-on-discovery}, and the plugin has a default for each of them, so those fields are
 * {@code optional()}. Every field the plugin reads only in {@code onEnable} is {@code restartRequired()};
 * {@code stash.overflow} is read on every give and is live.
 */
final class ConfigYmlSchema {

    /** Shows a storage field while {@code storage.backend} names a database server. */
    private static final VisibilityCondition NETWORKED_BACKEND = VisibilityCondition.whenNotEquals("backend", "sqlite");

    private static final String ALIAS = "\\S{1,64}";
    private static final String ALIAS_MESSAGE = "One word per alias, without spaces.";

    private ConfigYmlSchema() {
    }

    static SectionMapper mapper() {
        return SectionMapper.of(create());
    }

    static ConfigSchema create() {
        return ConfigSchema.of(
            ConfigField.stringList("command-aliases").label("/voucher aliases")
                .description("Extra names for /voucher, such as pv when no other plugin uses it (PlayerVaults "
                    + "does). An empty list registers none; /voucher itself always works. Aliases are lower-cased.")
                .optional().uniqueItems().ordered(false).itemPattern(ALIAS, ALIAS_MESSAGE).placeholder("vouchers")
                .restartRequired(),
            ConfigField.object("locale").label("Language").fields(
                ConfigField.string("default").label("Default language")
                    .description("Code of the language file used for plugin messages and the console: "
                        + "lang/<code>.yml in the plugin folder, for example de for lang/de.yml. Keys a "
                        + "translation leaves out, and an empty value, fall back to English.")
                    .optional().pattern("[A-Za-z0-9_.-]{1,32}",
                        "Use the name of a file in the lang folder without .yml, for example en or de.")
                    .placeholder("en").restartRequired(),
                ConfigField.bool("per-player").label("Per-player language")
                    .description("Show each player the messages in their Minecraft client language when a "
                        + "matching lang/<code>.yml exists, otherwise in the default language.")
                    .optional().restartRequired()),
            ConfigField.object("storage").label("Storage").fields(
                ConfigField.enumeration("backend")
                    .description("SQLite is a file in the plugin folder and needs no extra setup; the others "
                        + "connect to a database server with the settings below.")
                    .choice("sqlite", "SQLite").choice("mysql", "MySQL").choice("mariadb", "MariaDB")
                    .choice("postgres", "PostgreSQL").restartRequired(),
                ConfigField.string("host").placeholder("localhost").visibleWhen(NETWORKED_BACKEND)
                    .restartRequired(),
                ConfigField.integer("port").range(1, 65535)
                    .description("MySQL and MariaDB usually listen on 3306, PostgreSQL on 5432.")
                    .visibleWhen(NETWORKED_BACKEND).restartRequired(),
                ConfigField.string("database").visibleWhen(NETWORKED_BACKEND).restartRequired(),
                ConfigField.string("username").visibleWhen(NETWORKED_BACKEND).restartRequired(),
                ConfigField.string("password").optional().writeOnly().visibleWhen(NETWORKED_BACKEND)
                    .restartRequired(),
                ConfigField.integer("pool-size").label("Connection pool size")
                    .description("Most database connections ProVouchers keeps open at once.").min(1)
                    .unit("connections").visibleWhen(NETWORKED_BACKEND).restartRequired()),
            ConfigField.object("anti-dupe").label("Anti-dupe").fields(
                ConfigField.bool("remove-on-discovery")
                    .description("Remove a duplicate voucher found on redeem instead of returning it to the "
                        + "player. Anti-dupe covers vouchers with stackable: false; stackable vouchers are not "
                        + "tracked."),
                ConfigField.object("warning").label("Duplicate warning").fields(
                    ConfigField.bool("enabled")
                        .description("Add a warning line to the lore of a duplicate that is returned to the "
                            + "player (Remove on discovery off).")
                        .optional(),
                    ConfigField.string("text").label("Warning line").description("MiniMessage text.")
                        .optional().placeholder("<red>This item has been duplicated")),
                ConfigField.object("notify").label("Staff alerts").fields(
                    ConfigField.bool("enabled")
                        .description("Alert players with provouchers.notify when a duplicate voucher is caught. "
                            + "The message is staff.duplicate-alert in the language file.")
                        .optional())),
            ConfigField.object("redeem").label("Redeeming").fields(
                ConfigField.bool("left-click-preview")
                    .description("Left-click a held voucher to preview its rewards in a menu. Experimental.")
                    .optional(),
                ConfigField.bool("batch-open-quiet")
                    .description("When a stack is opened at once (shift-right-click), show one summary line "
                        + "instead of the messages, titles and sounds of every voucher.")
                    .optional(),
                ConfigField.integer("confirm-window-seconds").label("Confirmation window")
                    .description("How long the confirmation of a voucher with two-step-authentication: true "
                        + "stays valid.")
                    .optional().min(1).unit("seconds"),
                ConfigField.enumeration("confirm-style").label("Confirmation style")
                    .choice("chat", "Chat", "Click again after a chat prompt.")
                    .choice("gui", "Menu", "A confirm or cancel menu showing the voucher.")
                    .optional()),
            ConfigField.object("auto-give").label("Auto-give").fields(
                ConfigField.stringList("first-join").label("First-join vouchers")
                    .description("Voucher ids given once to a player joining the server for the first time. The "
                        + "ids are looked up at each join; an unknown id is logged.")
                    .optional().itemMinLength(1).placeholder("welcome_crate").restartRequired()),
            ConfigField.object("stash").label("Stash").fields(
                ConfigField.bool("enabled")
                    .description("The Stash holds virtual vouchers a player claims from a menu (/stash). Gives "
                        + "to offline players and overflow can land there instead of being lost.")
                    .optional().restartRequired(),
                ConfigField.stringList("command-aliases").label("/stash aliases")
                    .description("Extra names for /stash. Aliases are lower-cased.")
                    .optional().uniqueItems().ordered(false).itemPattern(ALIAS, ALIAS_MESSAGE)
                    .placeholder("rewards").restartRequired(),
                ConfigField.string("title").label("Menu title").description("MiniMessage text.")
                    .optional().placeholder("<gradient:#FFD700:#FF8A00>Your Stash"),
                ConfigField.integer("rows").label("Menu rows")
                    .description("Height of the Stash menu. It always has at least 3 rows, so 1 and 2 show as 3.")
                    .range(1, 6).optional(),
                ConfigField.bool("notify-on-join")
                    .description("Tell a joining player when rewards are waiting in their Stash.")
                    .optional().restartRequired(),
                ConfigField.string("expire-after")
                    .description("Stash entries lapse after this time: a number followed by s, m, h or d, for "
                        + "example 30d. Empty keeps them until claimed.")
                    .optional().pattern("[0-9]{1,9}[smhdSMHD]",
                        "Use a number followed by s, m, h or d, for example 30d, or leave it empty.")
                    .placeholder("30d").restartRequired(),
                ConfigField.enumeration("overflow")
                    .description("What happens when a give does not fit in the player's inventory. Used while "
                        + "the Stash is enabled.")
                    .choice("drop", "Drop", "Drop the items that do not fit.")
                    .choice("stash", "Stash", "Queue the items that do not fit in the player's Stash.")
                    .optional()),
            ConfigField.object("metrics").label("Metrics").fields(
                ConfigField.bool("enabled").label("Submit usage statistics")
                    .description("Submit pseudonymous usage statistics to bStats and FastStats.")
                    .optional().restartRequired()));
    }
}
