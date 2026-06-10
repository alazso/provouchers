package so.alaz.provouchers.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.config.ConfigManager;
import so.alaz.provouchers.give.VoucherGiveService;
import so.alaz.provouchers.gui.PreviewGui;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.redeem.RedeemHandler;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The {@code /voucher} command tree, built directly on Paper's Brigadier command API.
 * Covers help, give, giveall, redeem, preview, list, and reload. Arguments tab-complete
 * against live data (voucher ids, predefined amounts, online players); replies all route
 * through {@link Messages} for a consistent prefix, palette, and wording.
 */
public final class VoucherCommand {

    private static final int MAX_AMOUNT = 2304;

    /** Predefined amount suggestions offered for give/giveall. */
    private static final List<String> AMOUNT_SUGGESTIONS = List.of("1", "16", "32", "64");

    private static final String PERM_GIVE = "provouchers.give";
    private static final String PERM_GIVEALL = "provouchers.giveall";
    private static final String PERM_REDEEM = "provouchers.redeem";
    private static final String PERM_PREVIEW = "provouchers.preview";
    private static final String PERM_LIST = "provouchers.list";
    private static final String PERM_RELOAD = "provouchers.reload";

    /** A single subcommand's help metadata; Brigadier nodes carry none of this, so it lives here. */
    private record Sub(String name, String usage, String description, @Nullable String permission) {
    }

    /** The help listing, in display order. Each line is shown only when the sender may use it. */
    private static final List<Sub> HELP = List.of(
        new Sub("give", "give <id> [amount] [player]", "Give a voucher to a player", PERM_GIVE),
        new Sub("giveall", "giveall <id> [amount]", "Give a voucher to every online player", PERM_GIVEALL),
        new Sub("redeem", "redeem <code> [argument]", "Redeem a code", PERM_REDEEM),
        new Sub("preview", "preview", "Browse vouchers in a GUI", PERM_PREVIEW),
        new Sub("list", "list", "List loaded vouchers and codes", PERM_LIST),
        new Sub("reload", "reload", "Reload vouchers and codes from disk", PERM_RELOAD),
        new Sub("help", "help", "Show this help menu", null));

    private final VoucherRegistry registry;
    private final VoucherGiveService giveService;
    private final RedeemHandler redeemHandler;
    private final ConfigManager configManager;
    private final PreviewGui previewGui;
    private final Text text;

    public VoucherCommand(
        VoucherRegistry registry,
        VoucherGiveService giveService,
        RedeemHandler redeemHandler,
        ConfigManager configManager,
        PreviewGui previewGui,
        Text text
    ) {
        this.registry = registry;
        this.giveService = giveService;
        this.redeemHandler = redeemHandler;
        this.configManager = configManager;
        this.previewGui = previewGui;
        this.text = text;
    }

    /** Builds and registers the command for {@code plugin}. Call during {@code onEnable}. */
    public void register(Plugin plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
            event.registrar().register(buildTree(), "ProVouchers command", List.of("pv", "vouchers")));
    }

    private LiteralCommandNode<CommandSourceStack> buildTree() {
        return Commands.literal("voucher")
            .executes(run(ctx -> help(ctx.getSource())))
            .then(giveTree())
            .then(giveAllTree())
            .then(redeemTree())
            .then(Commands.literal("preview").requires(perm(PERM_PREVIEW))
                .executes(run(ctx -> preview(ctx.getSource()))))
            .then(Commands.literal("list").requires(perm(PERM_LIST))
                .executes(run(ctx -> list(ctx.getSource()))))
            .then(Commands.literal("reload").requires(perm(PERM_RELOAD))
                .executes(run(ctx -> reload(ctx.getSource()))))
            .then(Commands.literal("help")
                .executes(run(ctx -> help(ctx.getSource()))))
            .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> giveTree() {
        return Commands.literal("give").requires(perm(PERM_GIVE))
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> suggest(builder, registry.voucherIds()))
                .executes(run(ctx -> give(ctx, 1, false)))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                    .suggests((ctx, builder) -> suggest(builder, AMOUNT_SUGGESTIONS))
                    .executes(run(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "amount"), false)))
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(run(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "amount"), true))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> giveAllTree() {
        return Commands.literal("giveall").requires(perm(PERM_GIVEALL))
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, builder) -> suggest(builder, registry.voucherIds()))
                .executes(run(ctx -> giveAll(ctx, 1)))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                    .suggests((ctx, builder) -> suggest(builder, AMOUNT_SUGGESTIONS))
                    .executes(run(ctx -> giveAll(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> redeemTree() {
        // Codes are intentionally not suggested: tab-completing them would leak every loaded
        // code to any player. The argument stays free text.
        return Commands.literal("redeem").requires(perm(PERM_REDEEM))
            .then(Commands.argument("code", StringArgumentType.word())
                .executes(run(ctx -> redeem(ctx, false)))
                .then(Commands.argument("arg", StringArgumentType.greedyString())
                    .executes(run(ctx -> redeem(ctx, true)))));
    }

    private void help(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        reply(source, Messages.heading("Commands"));
        for (Sub sub : HELP) {
            if (sub.permission() == null || sender.hasPermission(sub.permission())) {
                reply(source, Messages.helpLine(sub.name(), sub.usage(), sub.description()));
            }
        }
    }

    private void give(CommandContext<CommandSourceStack> ctx, int amount, boolean hasTarget) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        Voucher voucher = registry.getVoucher(id).orElse(null);
        if (voucher == null) {
            reply(source, Messages.error("Unknown voucher <yellow>" + id + "</yellow>."));
            return;
        }
        Player target;
        if (!hasTarget) {
            target = asPlayer(source);
            if (target == null) {
                reply(source, Messages.error("Run this in-game, or name a target player from the console."));
                reply(source, Messages.usage("give <id> [amount] [player]"));
                return;
            }
        } else {
            target = firstTarget(ctx);
            if (target == null) {
                reply(source, Messages.error("No online player matched that target."));
                return;
            }
        }
        giveService.give(target, voucher, amount);
        reply(source, Messages.success("Gave <yellow>" + amount + "x</yellow> <gold>" + voucher.id()
            + "</gold> to <yellow>" + target.getName() + "</yellow>."));
    }

    private void giveAll(CommandContext<CommandSourceStack> ctx, int amount) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        Voucher voucher = registry.getVoucher(id).orElse(null);
        if (voucher == null) {
            reply(source, Messages.error("Unknown voucher <yellow>" + id + "</yellow>."));
            return;
        }
        int count = 0;
        for (Player online : source.getSender().getServer().getOnlinePlayers()) {
            giveService.give(online, voucher, amount);
            count++;
        }
        if (count == 0) {
            reply(source, Messages.info("No players are online to give to."));
            return;
        }
        reply(source, Messages.success("Gave <yellow>" + amount + "x</yellow> <gold>" + voucher.id()
            + "</gold> to <yellow>" + count + "</yellow> player(s)."));
    }

    private void redeem(CommandContext<CommandSourceStack> ctx, boolean hasArg) {
        CommandSourceStack source = ctx.getSource();
        Player player = asPlayer(source);
        if (player == null) {
            reply(source, Messages.error("Only players can redeem codes."));
            return;
        }
        String arg = hasArg ? StringArgumentType.getString(ctx, "arg") : null;
        redeemHandler.redeemCode(player, StringArgumentType.getString(ctx, "code"), arg);
    }

    private void preview(CommandSourceStack source) {
        Player player = asPlayer(source);
        if (player == null) {
            reply(source, Messages.error("Only players can open the preview."));
            return;
        }
        previewGui.open(player);
    }

    private void list(CommandSourceStack source) {
        List<String> ids = registry.voucherIds();
        reply(source, Messages.heading("Loaded content"));
        reply(source, "<gray>  Vouchers (<yellow>" + ids.size() + "</yellow>): "
            + (ids.isEmpty() ? "<dark_gray>none" : "<yellow>" + String.join("<gray>, <yellow>", ids)));
        reply(source, "<gray>  Codes: <yellow>" + registry.codeCount());
    }

    private void reload(CommandSourceStack source) {
        List<String> errors = configManager.reload();
        if (errors.isEmpty()) {
            reply(source, Messages.success("Reloaded <yellow>" + registry.voucherCount()
                + "</yellow> voucher(s) and <yellow>" + registry.codeCount() + "</yellow> code(s)."));
            return;
        }
        reply(source, Messages.error("Reloaded with <yellow>" + errors.size() + "</yellow> error(s):"));
        for (String error : errors) {
            reply(source, "<red>  - <gray>" + error);
        }
    }

    /** Resolves the first player matched by a {@code player()} selector argument, or {@code null}. */
    @Nullable
    private static Player firstTarget(CommandContext<CommandSourceStack> ctx) {
        try {
            List<Player> matched = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource());
            return matched.isEmpty() ? null : matched.get(0);
        } catch (CommandSyntaxException ex) {
            return null;
        }
    }

    /** The player behind the source (execution entity, else sender), or {@code null} from the console. */
    @Nullable
    private static Player asPlayer(CommandSourceStack source) {
        Entity executor = source.getExecutor();
        if (executor instanceof Player player) {
            return player;
        }
        return source.getSender() instanceof Player player ? player : null;
    }

    private void reply(CommandSourceStack source, String miniMessage) {
        source.getSender().sendMessage(text.render(miniMessage));
    }

    /** Wraps a void handler as a Brigadier command returning success. */
    private static Command<CommandSourceStack> run(Consumer<CommandContext<CommandSourceStack>> handler) {
        return ctx -> {
            handler.accept(ctx);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Predicate<CommandSourceStack> perm(String node) {
        return source -> source.getSender().hasPermission(node);
    }

    /** Offers the candidates that match what the player has typed so far, case-insensitively. */
    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Collection<String> candidates) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(candidate);
            }
        }
        return builder.buildFuture();
    }
}
