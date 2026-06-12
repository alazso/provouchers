package so.alaz.provouchers.redeem;

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
import so.alaz.provouchers.reward.RewardType;
import so.alaz.provouchers.reward.XpRewardPayload;
import so.alaz.provouchers.util.Placeholders;
import so.alaz.provouchers.hook.EconomyHook;
import so.alaz.provouchers.hook.HookRegistry;
import so.alaz.provouchers.hook.PermissionHook;
import so.alaz.provouchers.platform.Items;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.platform.Sounds;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.voucher.DefinedItem;
import so.alaz.provouchers.voucher.ItemResolver;
import so.alaz.provouchers.voucher.VoucherItemFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs reward actions for a player. Command dispatch and broadcasts are routed
 * through the scheduler so the plugin stays Folia-safe; messages, titles,
 * action bars, sounds, and item grants act on the player (the caller already runs
 * this on the player's region thread). Placeholders such as {@code %player%},
 * {@code %arg%}, and {@code %random:min-max%} are substituted first, then
 * MiniMessage and PlaceholderAPI are resolved by the text renderer.
 *
 * <p>Each reward is executed independently: a single failing reward is logged
 * with its source and never aborts the others.
 */
public final class RewardExecutor {

    private static final Title.Times TITLE_TIMES = Title.Times.times(
        Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500));

    private final Scheduler scheduler;
    private final Text text;
    private final ItemResolver items;
    private final VoucherItemFactory factory;
    private final HookRegistry hooks;
    private final ComponentLogger logger;

    public RewardExecutor(Scheduler scheduler, Text text, ItemResolver items,
                          VoucherItemFactory factory, HookRegistry hooks, ComponentLogger logger) {
        this.scheduler = scheduler;
        this.text = text;
        this.items = items;
        this.factory = factory;
        this.hooks = hooks;
        this.logger = logger;
    }

    /**
     * Runs every reward line for {@code player}, substituting {@code arg}. A reward
     * that fails is logged against {@code source} (the voucher or code) and skipped.
     * {@code definedItems} resolves {@code item: @name} references.
     */
    public void execute(Player player, String source, List<RewardLine> rewards, @Nullable String arg,
                        boolean quiet, Map<String, DefinedItem> definedItems) {
        Map<String, Long> namedRolls = new HashMap<>();
        for (RewardLine reward : rewards) {
            try {
                execute(player, source, reward, arg, quiet, namedRolls, definedItems);
            } catch (RuntimeException ex) {
                warn(source, reward.type().name().toLowerCase(), reward.payload(), ex.getMessage());
            }
        }
    }

    private void execute(Player player, String source, RewardLine reward, @Nullable String arg, boolean quiet,
                         Map<String, Long> namedRolls, Map<String, DefinedItem> definedItems) {
        // Batch open runs quiet: substantive rewards still apply, but per-item feedback that would
        // spam chat (messages, broadcasts, titles, action bars, sounds) is skipped.
        if (quiet && isFeedbackOnly(reward.type())) {
            return;
        }
        // Share namedRolls so %random:a-b:name% gives out and announces the same value.
        String payload = Placeholders.apply(reward.payload(), player.getName(), arg,
            ThreadLocalRandom.current(), namedRolls);
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
            case SOUND -> Sounds.play(player, payload);
            case ITEM -> giveItem(player, payload, definedItems);
            case CURRENCY -> applyCurrency(player, payload);
            case XP -> giveXp(player, payload);
            case GROUP -> applyGroup(player, source, payload);
            case PERMISSION -> applyPermission(player, source, payload);
        }
    }

    private void giveItem(Player player, String payload, Map<String, DefinedItem> definedItems) {
        RewardItemPayload spec = RewardItemPayload.parse(payload);
        if (spec.isDefinedRef()) {
            DefinedItem defined = definedItems.get(spec.definedName());
            if (defined == null) {
                throw new IllegalArgumentException("no defined item named '" + spec.definedName() + "'");
            }
            // Skull-based items may resolve off-thread; deliver on the player's region thread.
            factory.createDefinedItem(defined, spec.resolveAmount(), player).thenAccept(item ->
                scheduler.entity(player, () -> Items.giveOrDrop(player, item)));
            return;
        }
        Items.giveOrDrop(player, items.give(spec.reference(), spec.resolveAmount()));
    }

    private void giveXp(Player player, String payload) {
        XpRewardPayload spec = XpRewardPayload.parse(payload);
        int amount = spec.resolveAmount();
        if (spec.levels()) {
            player.giveExpLevels(amount);
        } else {
            player.giveExp(amount);
        }
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

    /** Feedback-only rewards: shown to the player but grant nothing, so they are skipped when quiet. */
    private static boolean isFeedbackOnly(RewardType type) {
        return switch (type) {
            case MESSAGE, BROADCAST, TITLE, ACTIONBAR, SOUND -> true;
            default -> false;
        };
    }

    private void warn(String source, String type, String payload, String reason) {
        logger.warn("Reward '{}: {}' in {} failed: {}", type, payload, source, reason);
    }

}
