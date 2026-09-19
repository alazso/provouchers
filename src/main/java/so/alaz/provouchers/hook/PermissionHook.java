package so.alaz.provouchers.hook;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Group lookups and permission writes, backing the group/permission rewards and the rank
 * condition. Plain permission checks go through Bukkit directly and are not part of this hook.
 * The write operations block on the permission store and must run off the main thread; they
 * return {@code true} only when the data changed and was saved, and {@code false} when nothing
 * changed or the operation is not supported.
 */
public interface PermissionHook extends Hook {

    /** The player's primary group, or {@code null} if unknown. */
    @Nullable
    String primaryGroup(Player player);

    /** All groups the player inherits, or empty if unknown. */
    java.util.List<String> groups(Player player);

    /** Adds the player to the group permanently. */
    boolean addGroup(Player player, String group);

    boolean addTempGroup(Player player, String group, Duration duration);

    boolean removeGroup(Player player, String group);

    boolean setPermission(Player player, String node, boolean value);

    /** Clears any directly-set node permission from the player. */
    boolean unsetPermission(Player player, String node);
}
