package so.alaz.provouchers.command;

import org.bukkit.entity.Player;
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
import so.alaz.strata.api.scheduler.PlatformScheduler;

import java.util.List;
import java.util.UUID;

/**
 * The {@code /voucher} command tree, built with Strata's fluent Brigadier builder.
 * Covers give, giveall, redeem, list, and reload; each leaf reads only the
 * arguments present at its depth so optional arguments work without overloading.
 */
public final class VoucherCommand {

    private static final int MAX_AMOUNT = 2304;

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
        StrataCommand root = StrataCommand.literal("voucher")
            .then(giveTree())
            .then(giveAllTree())
            .then(redeemTree())
            .then(StrataCommand.literal("list")
                .permission("provouchers.list")
                .executes(this::list))
            .then(StrataCommand.literal("reload")
                .permission("provouchers.reload")
                .executes(this::reload));
        root.register(plugin, "ProVouchers command", List.of("pv", "vouchers"));
    }

    private StrataCommand giveTree() {
        return StrataCommand.literal("give")
            .permission("provouchers.give")
            .then(StrataCommand.argument("id", ArgType.word())
                .executes(ctx -> give(ctx, 1, null, null))
                .then(StrataCommand.argument("amount", ArgType.integer(1, MAX_AMOUNT))
                    .executes(ctx -> give(ctx, ctx.getInt("amount"), null, null))
                    .then(StrataCommand.argument("target", ArgType.player())
                        .executes(ctx -> give(ctx, ctx.getInt("amount"), "target", null))
                        .then(StrataCommand.argument("arg", ArgType.greedyString())
                            .executes(ctx ->
                                give(ctx, ctx.getInt("amount"), "target", ctx.getString("arg")))))));
    }

    private StrataCommand giveAllTree() {
        return StrataCommand.literal("giveall")
            .permission("provouchers.giveall")
            .then(StrataCommand.argument("id", ArgType.word())
                .executes(ctx -> giveAll(ctx, 1, null))
                .then(StrataCommand.argument("amount", ArgType.integer(1, MAX_AMOUNT))
                    .executes(ctx -> giveAll(ctx, ctx.getInt("amount"), null))
                    .then(StrataCommand.argument("arg", ArgType.greedyString())
                        .executes(ctx -> giveAll(ctx, ctx.getInt("amount"), ctx.getString("arg"))))));
    }

    private StrataCommand redeemTree() {
        return StrataCommand.literal("redeem")
            .permission("provouchers.redeem")
            .then(StrataCommand.argument("code", ArgType.word())
                .executes(ctx -> redeem(ctx, null))
                .then(StrataCommand.argument("arg", ArgType.greedyString())
                    .executes(ctx -> redeem(ctx, ctx.getString("arg")))));
    }

    private void give(CommandContext ctx, int amount, @Nullable String targetArg, @Nullable String arg) {
        Voucher voucher = registry.getVoucher(ctx.getString("id")).orElse(null);
        if (voucher == null) {
            ctx.reply("<red>Unknown voucher: " + ctx.getString("id"));
            return;
        }
        Player target = targetArg == null ? ctx.player() : ctx.getPlayer(targetArg);
        if (target == null) {
            ctx.reply("<red>Specify a target player when running this from the console.");
            return;
        }
        giveItem(voucher, amount, target);
        ctx.reply("<green>Gave " + amount + "x " + voucher.id() + " to " + target.getName() + ".");
    }

    private void giveAll(CommandContext ctx, int amount, @Nullable String arg) {
        Voucher voucher = registry.getVoucher(ctx.getString("id")).orElse(null);
        if (voucher == null) {
            ctx.reply("<red>Unknown voucher: " + ctx.getString("id"));
            return;
        }
        int count = 0;
        for (Player online : ctx.sender().getServer().getOnlinePlayers()) {
            giveItem(voucher, amount, online);
            count++;
        }
        ctx.reply("<green>Gave " + amount + "x " + voucher.id() + " to " + count + " player(s).");
    }

    private void giveItem(Voucher voucher, int amount, Player target) {
        UUID batchId = UUID.randomUUID();
        factory.createItem(voucher, amount, target, batchId).thenAccept(item ->
            scheduler.entity(target, () -> target.getInventory().addItem(item).values()
                .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover))));
    }

    private void redeem(CommandContext ctx, @Nullable String arg) {
        Player player = ctx.player();
        if (player == null) {
            ctx.reply("<red>Only players can redeem codes.");
            return;
        }
        redeemHandler.redeemCode(player, ctx.getString("code"), arg);
    }

    private void list(CommandContext ctx) {
        List<String> ids = registry.voucherIds();
        ctx.reply("<gold>Vouchers (" + ids.size() + "): <yellow>"
            + (ids.isEmpty() ? "none" : String.join(", ", ids)));
        ctx.reply("<gold>Codes loaded: <yellow>" + registry.codeCount());
    }

    private void reload(CommandContext ctx) {
        List<String> errors = configManager.reload();
        if (errors.isEmpty()) {
            ctx.reply("<green>Reloaded " + registry.voucherCount() + " voucher(s) and "
                + registry.codeCount() + " code(s).");
            return;
        }
        ctx.reply("<red>Reloaded with " + errors.size() + " error(s):");
        for (String error : errors) {
            ctx.reply("<red>- " + error);
        }
    }
}
