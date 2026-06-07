package so.alaz.provouchers.redeem;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.reward.CurrencyRewardPayload;
import so.alaz.provouchers.reward.GroupRewardPayload;
import so.alaz.provouchers.reward.PermissionRewardPayload;
import so.alaz.provouchers.reward.RewardItemPayload;
import so.alaz.provouchers.reward.RewardLine;
import so.alaz.provouchers.util.Tokens;
import so.alaz.provouchers.voucher.ItemResolver;
import so.alaz.strata.api.hook.EconomyHook;
import so.alaz.strata.api.hook.HookRegistry;
import so.alaz.strata.api.hook.PermissionHook;
import so.alaz.strata.api.scheduler.PlatformScheduler;
import so.alaz.strata.api.text.TextRenderer;

import java.time.Duration;
import java.util.List;

/**
 * Runs reward actions for a player. Command dispatch and broadcasts are routed
 * through Strata's scheduler so the plugin stays Folia-safe; messages, titles,
 * action bars, sounds, and item grants act on the player (the caller already runs
 * this on the player's region thread). Tokens such as {@code %player%},
 * {@code {arg}}, and {@code {random:min-max}} are substituted first, then
 * MiniMessage and PlaceholderAPI are resolved by Strata's renderer.
 *
 * <p>Each reward is executed independently: a single failing reward is logged
 * with its source and never aborts the others.
 */
public final class RewardExecutor {

    private static final Title.Times TITLE_TIMES = Title.Times.times(
        Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500));

    private final PlatformScheduler scheduler;
    private final TextRenderer text;
    private final ItemResolver items;
    private final HookRegistry hooks;
    private final ComponentLogger logger;

    public RewardExecutor(PlatformScheduler scheduler, TextRenderer text, ItemResolver items,
                          HookRegistry hooks, ComponentLogger logger) {
        this.scheduler = scheduler;
        this.text = text;
        this.items = items;
        this.hooks = hooks;
        this.logger = logger;
    }

    /**
     * Runs every reward line for {@code player}, substituting {@code arg}. A reward
     * that fails is logged against {@code source} (the voucher or code) and skipped.
     */
    public void execute(Player player, String source, List<RewardLine> rewards, @Nullable String arg) {
        for (RewardLine reward : rewards) {
            try {
                execute(player, source, reward, arg);
            } catch (RuntimeException ex) {
                warn(source, reward.type().name().toLowerCase(), reward.payload(), ex.getMessage());
            }
        }
    }

    private void execute(Player player, String source, RewardLine reward, @Nullable String arg) {
        String payload = Tokens.apply(reward.payload(), player.getName(), arg);
        switch (reward.type()) {
            case CONSOLE_COMMAND -> scheduler.global(() ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), payload));
            case PLAYER_COMMAND -> scheduler.entity(player, () -> player.performCommand(payload));
            case MESSAGE -> player.sendMessage(text.render(payload, player));
            case BROADCAST -> {
                Component message = text.render(payload, player);
                scheduler.global(() -> Bukkit.broadcast(message));
            }
            case TITLE -> {
                String[] parts = payload.split("\\|", 2);
                Component title = text.render(parts[0], player);
                Component subtitle = parts.length > 1 ? text.render(parts[1], player) : Component.empty();
                player.showTitle(Title.title(title, subtitle, TITLE_TIMES));
            }
            case ACTIONBAR -> player.sendActionBar(text.render(payload, player));
            case SOUND -> playSound(player, payload);
            case ITEM -> giveItem(player, payload);
            case CURRENCY -> applyCurrency(player, payload);
            case GROUP -> applyGroup(player, source, payload);
            case PERMISSION -> applyPermission(player, source, payload);
        }
    }

    private void giveItem(Player player, String payload) {
        RewardItemPayload spec = RewardItemPayload.parse(payload);
        ItemStack item = items.give(spec.reference(), spec.resolveAmount());
        player.getInventory().addItem(item).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void applyCurrency(Player player, String payload) {
        CurrencyRewardPayload spec = CurrencyRewardPayload.parse(payload);
        EconomyHook economy = hooks.get(EconomyHook.class);
        if (economy == null) {
            throw new IllegalStateException("no economy provider is installed");
        }
        double amount = spec.resolveAmount();
        boolean applied = switch (spec.action()) {
            case GIVE -> economy.deposit(player, amount);
            case TAKE -> economy.withdraw(player, amount);
        };
        if (!applied) {
            throw new IllegalStateException("economy transaction was rejected");
        }
    }

    private void applyGroup(Player player, String source, String payload) {
        GroupRewardPayload spec = GroupRewardPayload.parse(payload);
        PermissionHook perms = hooks.get(PermissionHook.class);
        // Permission writes hit the data store, so run them off the main thread.
        scheduler.async(() -> {
            boolean applied = perms != null && switch (spec.action()) {
                case ADD -> spec.isTemporary()
                    ? perms.addTempGroup(player, spec.group(), spec.duration())
                    : perms.addGroup(player, spec.group());
                case REMOVE -> perms.removeGroup(player, spec.group());
            };
            if (!applied) {
                warn(source, "group", payload,
                    "no group-capable permission provider (LuckPerms), or no change");
            }
        });
    }

    private void applyPermission(Player player, String source, String payload) {
        PermissionRewardPayload spec = PermissionRewardPayload.parse(payload);
        PermissionHook perms = hooks.get(PermissionHook.class);
        scheduler.async(() -> {
            boolean applied = perms != null && switch (spec.action()) {
                case SET -> perms.setPermission(player, spec.node(), spec.value());
                case UNSET -> perms.unsetPermission(player, spec.node());
            };
            if (!applied) {
                warn(source, "permission", payload,
                    "no write-capable permission provider (LuckPerms), or no change");
            }
        });
    }

    private void warn(String source, String type, String payload, String reason) {
        logger.warn("Reward '{}: {}' in {} failed: {}", type, payload, source, reason);
    }

    private void playSound(Player player, String payload) {
        String[] parts = payload.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return;
        }
        Key key = Key.key(parts[0]);
        float volume = parts.length > 1 ? parseFloat(parts[1], 1f) : 1f;
        float pitch = parts.length > 2 ? parseFloat(parts[2], 1f) : 1f;
        player.playSound(Sound.sound(key, Sound.Source.MASTER, volume, pitch));
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
