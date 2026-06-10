package so.alaz.provouchers.hook;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * LuckPerms-backed {@link PermissionHook}: group resolution for the rank condition and the
 * group/permission reward writes. All LuckPerms references live inside guarded method bodies, so
 * the class loads fine when LuckPerms is absent and every lookup degrades to {@code null}/empty/
 * {@code false} instead of throwing.
 */
public final class LuckPermsPermissionHook implements PermissionHook {

    private final boolean present = Classes.present("net.luckperms.api.LuckPerms", getClass());

    @Override
    public String name() {
        return "LuckPerms";
    }

    @Override
    public boolean isAvailable() {
        if (!present) {
            return false;
        }
        try {
            LuckPermsProvider.get();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    @Nullable
    public String primaryGroup(Player player) {
        User user = user(player);
        return user != null ? user.getPrimaryGroup() : null;
    }

    @Override
    public List<String> groups(Player player) {
        try {
            User user = user(player);
            if (user == null) {
                return List.of();
            }
            return user.getInheritedGroups(QueryOptions.nonContextual()).stream()
                .map(group -> group.getName())
                .toList();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    @Override
    public boolean addGroup(Player player, String group) {
        return mutate(player, user -> user.data().add(InheritanceNode.builder(group).build()).wasSuccessful());
    }

    @Override
    public boolean addTempGroup(Player player, String group, Duration duration) {
        return mutate(player, user -> user.data()
            .add(InheritanceNode.builder(group).expiry(duration.toSeconds(), TimeUnit.SECONDS).build())
            .wasSuccessful());
    }

    @Override
    public boolean removeGroup(Player player, String group) {
        return mutate(player, user -> user.data().remove(InheritanceNode.builder(group).build()).wasSuccessful());
    }

    @Override
    public boolean setPermission(Player player, String node, boolean value) {
        return mutate(player, user -> user.data()
            .add(PermissionNode.builder(node).value(value).build())
            .wasSuccessful());
    }

    @Override
    public boolean unsetPermission(Player player, String node) {
        // Remove the node regardless of the stored true/false value or contexts.
        return mutate(player, user -> {
            Predicate<PermissionNode> matches = candidate -> candidate.getPermission().equals(node);
            boolean exists = user.getNodes(NodeType.PERMISSION).stream().anyMatch(matches);
            if (!exists) {
                return false;
            }
            user.data().clear(candidate ->
                candidate instanceof PermissionNode permission && matches.test(permission));
            return true;
        });
    }

    @Nullable
    private User user(Player player) {
        try {
            return LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Loads the user (cached or from storage), applies the action, saves only if it changed. */
    private boolean mutate(Player player, Function<User, Boolean> action) {
        try {
            UserManager manager = LuckPermsProvider.get().getUserManager();
            User user = manager.getUser(player.getUniqueId());
            if (user == null) {
                user = manager.loadUser(player.getUniqueId()).join();
            }
            boolean changed = action.apply(user);
            if (changed) {
                manager.saveUser(user).join();
            }
            return changed;
        } catch (Throwable ignored) {
            // A missing or version-incompatible LuckPerms degrades to "not changed".
            return false;
        }
    }
}
