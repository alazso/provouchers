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
import so.alaz.provouchers.locale.Messages;
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
    private static final String PERM_DOCTOR = "provouchers.doctor";

    /** A single subcommand's help metadata; Brigadier nodes carry none of this, so it lives here. */
    private record Sub(String name, String usage, @Nullable String permission) {
    }

    /** The help listing, in display order. Each line is shown only when the sender may use it.
     *  Descriptions are localized under {@code command.help.descriptions.<name>}. */
    private static final List<Sub> HELP = List.of(
        new Sub("give", "give <id> [amount] [player]", PERM_GIVE),
        new Sub("giveall", "giveall <id> [amount]", PERM_GIVEALL),
        new Sub("redeem", "redeem <code> [argument]", PERM_REDEEM),
        new Sub("preview", "preview", PERM_PREVIEW),
        new Sub("list", "list", PERM_LIST),
        new Sub("reload", "reload [id]", PERM_RELOAD),
        new Sub("doctor", "doctor", PERM_DOCTOR),
        new Sub("help", "help", null));

    private final VoucherRegistry registry;
    private final VoucherGiveService giveService;
    private final RedeemHandler redeemHandler;
    private final ConfigManager configManager;
    private final PreviewGui previewGui;
    private final Text text;
    private final Messages messages;
    private final Diagnostics diagnostics;

    public VoucherCommand(
        VoucherRegistry registry,
        VoucherGiveService giveService,
        RedeemHandler redeemHandler,
        ConfigManager configManager,
        PreviewGui previewGui,
        Text text,
        Messages messages,
        Diagnostics diagnostics
    ) {
        this.registry = registry;
        this.giveService = giveService;
        this.redeemHandler = redeemHandler;
        this.configManager = configManager;
        this.previewGui = previewGui;
        this.text = text;
        this.messages = messages;
        this.diagnostics = diagnostics;
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
                .executes(run(ctx -> reloadAll(ctx.getSource())))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggest(builder, registry.voucherIds()))
                    .executes(run(ctx -> reloadOne(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))))
            .then(Commands.literal("doctor").requires(perm(PERM_DOCTOR))
                .executes(run(ctx -> doctor(ctx.getSource()))))
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
        Player viewer = asPlayer(source);
        reply(source, messages.get(viewer, "command.help.heading"));
        for (Sub sub : HELP) {
            if (sub.permission() == null || sender.hasPermission(sub.permission())) {
                reply(source, helpLine(viewer, sub));
            }
        }
    }

    /** A clickable, localized help line: clicking suggests the subcommand in the chat box. */
    private String helpLine(@Nullable Player viewer, Sub sub) {
        String hover = messages.get(viewer, "command.help.hover");
        String description = messages.get(viewer, "command.help.descriptions." + sub.name());
        return "<click:suggest_command:'/voucher " + sub.name() + " '>"
            + "<hover:show_text:'" + hover + "'>"
            + "<gold>/voucher " + sub.usage() + "</gold></hover></click>"
            + " <dark_gray>-</dark_gray> <gray>" + description + "</gray>";
    }

    private void give(CommandContext<CommandSourceStack> ctx, int amount, boolean hasTarget) {
        CommandSourceStack source = ctx.getSource();
        Player viewer = asPlayer(source);
        String id = StringArgumentType.getString(ctx, "id");
        Voucher voucher = registry.getVoucher(id).orElse(null);
        if (voucher == null) {
            reply(source, messages.get(viewer, "command.unknown-voucher", "id", id));
            return;
        }
        Player target;
        if (!hasTarget) {
            target = asPlayer(source);
            if (target == null) {
                reply(source, messages.get(viewer, "command.give.needs-target"));
                reply(source, messages.get(viewer, "command.give.usage"));
                return;
            }
        } else {
            target = firstTarget(ctx);
            if (target == null) {
                reply(source, messages.get(viewer, "command.give.no-target"));
                return;
            }
        }
        giveService.give(target, voucher, amount);
        reply(source, messages.get(viewer, "command.give.success",
            "amount", amount, "voucher", voucher.id(), "target", target.getName()));
    }

    private void giveAll(CommandContext<CommandSourceStack> ctx, int amount) {
        CommandSourceStack source = ctx.getSource();
        Player viewer = asPlayer(source);
        String id = StringArgumentType.getString(ctx, "id");
        Voucher voucher = registry.getVoucher(id).orElse(null);
        if (voucher == null) {
            reply(source, messages.get(viewer, "command.unknown-voucher", "id", id));
            return;
        }
        int count = 0;
        for (Player online : source.getSender().getServer().getOnlinePlayers()) {
            giveService.give(online, voucher, amount);
            count++;
        }
        if (count == 0) {
            reply(source, messages.get(viewer, "command.giveall.none-online"));
            return;
        }
        reply(source, messages.get(viewer, "command.giveall.success",
            "amount", amount, "voucher", voucher.id(), "count", count));
    }

    private void redeem(CommandContext<CommandSourceStack> ctx, boolean hasArg) {
        CommandSourceStack source = ctx.getSource();
        Player player = asPlayer(source);
        if (player == null) {
            reply(source, messages.get(null, "command.redeem.players-only"));
            return;
        }
        String arg = hasArg ? StringArgumentType.getString(ctx, "arg") : null;
        redeemHandler.redeemCode(player, StringArgumentType.getString(ctx, "code"), arg);
    }

    private void preview(CommandSourceStack source) {
        Player player = asPlayer(source);
        if (player == null) {
            reply(source, messages.get(null, "command.preview.players-only"));
            return;
        }
        previewGui.open(player);
    }

    private void list(CommandSourceStack source) {
        Player viewer = asPlayer(source);
        List<String> ids = registry.voucherIds();
        String idsList = ids.isEmpty()
            ? messages.get(viewer, "command.list.none")
            : "<yellow>" + String.join("<gray>, <yellow>", ids);
        reply(source, messages.get(viewer, "command.list.heading"));
        reply(source, messages.get(viewer, "command.list.vouchers", "count", ids.size(), "ids", idsList));
        reply(source, messages.get(viewer, "command.list.codes", "count", registry.codeCount()));
    }

    private void reloadAll(CommandSourceStack source) {
        Player viewer = asPlayer(source);
        messages.reload();
        List<String> errors = configManager.reload();
        if (errors.isEmpty()) {
            reply(source, messages.get(viewer, "command.reload.success",
                "vouchers", registry.voucherCount(), "codes", registry.codeCount()));
            return;
        }
        reportReloadErrors(source, viewer, errors);
    }

    /** Reloads just the voucher and/or code file named {@code id}, leaving the rest untouched. */
    private void reloadOne(CommandSourceStack source, String id) {
        Player viewer = asPlayer(source);
        List<String> errors = configManager.reloadOne(id);
        if (errors == null) {
            reply(source, messages.get(viewer, "command.reload.unknown-file", "id", id));
            return;
        }
        if (errors.isEmpty()) {
            reply(source, messages.get(viewer, "command.reload.one-success", "id", id));
            return;
        }
        reportReloadErrors(source, viewer, errors);
    }

    private void reportReloadErrors(CommandSourceStack source, @Nullable Player viewer, List<String> errors) {
        reply(source, messages.get(viewer, "command.reload.errors", "errors", errors.size()));
        for (String error : errors) {
            reply(source, messages.get(viewer, "command.reload.error-line", "error", error));
        }
    }

    private void doctor(CommandSourceStack source) {
        Player viewer = asPlayer(source);
        reply(source, messages.get(viewer, "command.doctor.heading"));
        for (String line : diagnostics.report()) {
            reply(source, line);
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
