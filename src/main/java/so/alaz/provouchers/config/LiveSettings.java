package so.alaz.provouchers.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * The config.yml settings that take effect without a restart. They are read on every use through a supplier, so
 * {@code /voucher reload} and a save from ComfiConf both apply them at once.
 *
 * <p>Settings that decide what is built at startup (the storage backend, command aliases, registered listeners)
 * are not here: changing those needs a restart, and their schema fields say so.
 *
 * @param removeOnDiscovery remove a duplicate voucher instead of returning it
 * @param warningEnabled add the warning lore to a returned duplicate
 * @param warningText the warning lore line, as MiniMessage
 * @param notifyEnabled alert staff when a duplicate is caught
 * @param batchQuiet suppress per-item feedback when a stack is opened at once
 * @param confirmWindowSeconds how long a two-step confirmation stays valid
 * @param guiConfirm ask for the second confirmation click in a menu instead of chat
 * @param leftClickPreview left-click a held voucher to preview its rewards
 * @param blockEntityUse keep a held voucher from acting on a mob, armor stand or other entity
 * @param stashTitle title of the Stash menu, as MiniMessage
 * @param stashRows height of the Stash menu
 */
public record LiveSettings(
    boolean removeOnDiscovery,
    boolean warningEnabled,
    String warningText,
    boolean notifyEnabled,
    boolean batchQuiet,
    long confirmWindowSeconds,
    boolean guiConfirm,
    boolean leftClickPreview,
    boolean blockEntityUse,
    String stashTitle,
    int stashRows
) {

    /** Reads the live settings from a loaded config.yml, falling back to the shipped defaults. */
    public static LiveSettings from(FileConfiguration config) {
        return new LiveSettings(
            config.getBoolean("anti-dupe.remove-on-discovery", true),
            config.getBoolean("anti-dupe.warning.enabled", false),
            config.getString("anti-dupe.warning.text", "<red>This item has been duplicated"),
            config.getBoolean("anti-dupe.notify.enabled", true),
            config.getBoolean("redeem.batch-open-quiet", true),
            config.getLong("redeem.confirm-window-seconds", 5L),
            "gui".equalsIgnoreCase(config.getString("redeem.confirm-style", "chat")),
            config.getBoolean("redeem.left-click-preview", false),
            config.getBoolean("redeem.block-entity-use", true),
            config.getString("stash.title", "<gradient:#FFD700:#FF8A00>Your Stash"),
            config.getInt("stash.rows", 6));
    }
}
