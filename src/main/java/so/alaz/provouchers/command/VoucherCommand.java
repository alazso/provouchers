package so.alaz.provouchers.command;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.config.ConfigManager;
import so.alaz.provouchers.redeem.RedeemHandler;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherItemFactory;
import so.alaz.provouchers.voucher.VoucherRegistry;
import so.alaz.strata.api.command.ArgType;
import so.alaz.strata.api.command.CommandContext;
import so.alaz.strata.api.command.StrataCommand;
import so.alaz.strata.api.command.Suggestions;
import so.alaz.strata.api.scheduler.PlatformScheduler;

import java.util.List;

/**
 * The {@code /voucher} command tree, built with Strata's fluent Brigadier builder.
 * Covers help, give, giveall, redeem, list, and reload. Arguments tab-complete
 * against live data (voucher ids, predefined amounts, online players); replies all
 * route through {@link Messages} for a consistent prefix, palette, and wording.
 */
public final class VoucherCommand {

    private static final int MAX_AMOUNT = 2304;

    /** Predefined amount suggestions offered for give/giveall. */
    private static final int[] AMOUNT_SUGGESTIONS = {1, 16, 32, 64};

    private final VoucherRegistry registry;
    private final VoucherItemFactory factory;
    private final RedeemHandler redeemHandler;
    private final ConfigManager configManager;
    private final PlatformScheduler scheduler;

    public VoucherCommand(
        VoucherRegistry registry,
        VoucherItemFactory factory,
        RedeemHandler redeemHandler,
        ConfigManager configManager,
        PlatformScheduler scheduler
    ) {
        this.registry = registry;
        this.factory = factory;
        this.redeemHandler = redeemHandler;
        this.configManager = configManager;
        this.scheduler = scheduler;
    }

    /** Builds and registers the command for {@code plugin}. Call during {@code onEnable}. */
    public void register(Plugin plugin) {
        StrataCommand root = StrataCommand.literal("voucher");
        root.executes(ctx -> help(ctx, root));
        root.then(giveTree());
        root.then(giveAllTree());
        root.then(redeemTree());
        root.then(StrataCommand.literal("list")
            .permission("provouchers.list")
            .description("List loaded vouchers and codes")
            .usage("list")
            .executes(this::list));
        root.then(StrataCommand.literal("reload")
            .permission("provouchers.reload")
            .description("Reload vouchers and codes from disk")
            .usage("reload")
            .executes(this::reload));
        root.then(StrataCommand.literal("help")
            .description("Show this help menu")
            .usage("help")
            .executes(ctx -> help(ctx, root)));
        root.register(plugin, "ProVouchers command", List.of("pv", "vouchers"));
    }

    private StrataCommand giveTree() {
        return StrataCommand.literal("give")
            .permission("provouchers.give")
            .description("Give a voucher to a player")
            .usage("give <id> [amount] [player]")
            .then(StrataCommand.argument("id", ArgType.word())
                .suggests(Suggestions.from(registry::voucherIds))
                .executes(ctx -> give(ctx, 1, null))
                .then(StrataCommand.argument("amount", ArgType.integer(1, MAX_AMOUNT))
                    .suggests(Suggestions.integers(AMOUNT_SUGGESTIONS))
                    .executes(ctx -> give(ctx, ctx.getInt("amount"), null))
                    .then(StrataCommand.argument("target", ArgType.player())
                        .executes(ctx -> give(ctx, ctx.getInt("amount"), "target")))));
    }

    private StrataCommand giveAllTree() {
        return StrataCommand.literal("giveall")
            .permission("provouchers.giveall")
            .description("Give a voucher to every online player")
            .usage("giveall <id> [amount]")
            .then(StrataCommand.argument("id", ArgType.word())
                .suggests(Suggestions.from(registry::voucherIds))
                .executes(ctx -> giveAll(ctx, 1))
                .then(StrataCommand.argument("amount", ArgType.integer(1, MAX_AMOUNT))
                    .suggests(Suggestions.integers(AMOUNT_SUGGESTIONS))
                    .executes(ctx -> giveAll(ctx, ctx.getInt("amount")))));
    }

    private StrataCommand redeemTree() {
        // Codes are intentionally not suggested: tab-completing them would leak
        // every loaded code to any player. The argument stays free text.
        return StrataCommand.literal("redeem")
            .permission("provouchers.redeem")
            .description("Redeem a code")
            .usage("redeem <code> [argument]")
            .then(StrataCommand.argument("code", ArgType.word())
                .executes(ctx -> redeem(ctx, null))
                .then(StrataCommand.argument("arg", ArgType.greedyString())
                    .executes(ctx -> redeem(ctx, ctx.getString("arg")))));
    }

    private void help(CommandContext ctx, StrataCommand root) {
        ctx.reply(Messages.heading("Commands"));
        for (StrataCommand.HelpEntry entry : root.helpEntries(ctx.sender())) {
            ctx.reply(Messages.helpLine(entry.getName(), entry.getUsage(), entry.getDescription()));
        }
    }

    private void give(CommandContext ctx, int amount, @Nullable String targetArg) {
        Voucher voucher = registry.getVoucher(ctx.getString("id")).orElse(null);
        if (voucher == null) {
            ctx.reply(Messages.error("Unknown voucher <yellow>" + ctx.getString("id") + "</yellow>."));
            return;
        }
        Player target;
        if (targetArg == null) {
            target = ctx.player();
            if (target == null) {
                ctx.reply(Messages.error("Run this in-game, or name a target player from the console."));
                ctx.reply(Messages.usage("give <id> [amount] [player]"));
                return;
            }
        } else {
            List<Player> matched = ctx.getPlayers(targetArg);
            if (matched.isEmpty()) {
                ctx.reply(Messages.error("No online player matched that target."));
                return;
            }
            target = matched.get(0);
        }
        giveItem(voucher, amount, target);
        ctx.reply(Messages.success("Gave <yellow>" + amount + "x</yellow> <gold>" + voucher.id()
            + "</gold> to <yellow>" + target.getName() + "</yellow>."));
    }

    private void giveAll(CommandContext ctx, int amount) {
        Voucher voucher = registry.getVoucher(ctx.getString("id")).orElse(null);
        if (voucher == null) {
            ctx.reply(Messages.error("Unknown voucher <yellow>" + ctx.getString("id") + "</yellow>."));
            return;
        }
        int count = 0;
        for (Player online : ctx.sender().getServer().getOnlinePlayers()) {
            giveItem(voucher, amount, online);
            count++;
        }
        if (count == 0) {
            ctx.reply(Messages.info("No players are online to give to."));
            return;
        }
        ctx.reply(Messages.success("Gave <yellow>" + amount + "x</yellow> <gold>" + voucher.id()
            + "</gold> to <yellow>" + count + "</yellow> player(s)."));
    }

    private void giveItem(Voucher voucher, int amount, Player target) {
        factory.createItems(voucher, amount, target).thenAccept(items ->
            scheduler.entity(target, () -> target.getInventory().addItem(items.toArray(ItemStack[]::new))
                .values()
                .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover))));
    }

    private void redeem(CommandContext ctx, @Nullable String arg) {
        Player player = ctx.player();
        if (player == null) {
            ctx.reply(Messages.error("Only players can redeem codes."));
            return;
        }
        redeemHandler.redeemCode(player, ctx.getString("code"), arg);
    }

    private void list(CommandContext ctx) {
        List<String> ids = registry.voucherIds();
        ctx.reply(Messages.heading("Loaded content"));
        ctx.reply("<gray>  Vouchers (<yellow>" + ids.size() + "</yellow>): "
            + (ids.isEmpty() ? "<dark_gray>none" : "<yellow>" + String.join("<gray>, <yellow>", ids)));
        ctx.reply("<gray>  Codes: <yellow>" + registry.codeCount());
    }

    private void reload(CommandContext ctx) {
        List<String> errors = configManager.reload();
        if (errors.isEmpty()) {
            ctx.reply(Messages.success("Reloaded <yellow>" + registry.voucherCount()
                + "</yellow> voucher(s) and <yellow>" + registry.codeCount() + "</yellow> code(s)."));
            return;
        }
        ctx.reply(Messages.error("Reloaded with <yellow>" + errors.size() + "</yellow> error(s):"));
        for (String error : errors) {
            ctx.reply("<red>  - <gray>" + error);
        }
    }
}
