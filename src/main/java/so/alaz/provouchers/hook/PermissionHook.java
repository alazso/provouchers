package so.alaz.provouchers.hook;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Group lookups and permission writes, backing the group/permission rewards and the rank
 * condition. Plain permission checks go through Bukkit directly and are not part of this hook.
 * The write operations hit the permission store, so call them off the main thread; returning
 * {@code false} means "not changed / not supported".
 */
public interface PermissionHook extends Hook {

    /** The player's primary group, or {@code null} if unknown. */
    @Nullable
    String primaryGroup(Player player);

    /** All groups the player inherits, or empty if unknown. */
    java.util.List<String> groups(Player player);

    /** Adds the player to the group permanently. Returns {@code true} if the data changed and saved. */
    boolean addGroup(Player player, String group);

    /** Adds the player to the group for a duration (an expiring membership). */
    boolean addTempGroup(Player player, String group, Duration duration);

    /** Removes the player from the group. */
    boolean removeGroup(Player player, String group);

    /** Sets a permission node on the player to a value. */
    boolean setPermission(Player player, String node, boolean value);

    /** Clears any directly-set node permission from the player. */
    boolean unsetPermission(Player player, String node);
}
