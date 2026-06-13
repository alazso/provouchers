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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import so.alaz.provouchers.config.ConfigManager;
import so.alaz.provouchers.migrate.MigrationReport;
import so.alaz.provouchers.migrate.MigrationService;
import so.alaz.provouchers.migrate.Migrator;
import so.alaz.provouchers.give.VoucherGiveService;
import so.alaz.provouchers.gui.FromhandGui;
import so.alaz.provouchers.gui.PreviewGui;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.platform.ItemBuilder;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.redeem.RedeemHandler;
import so.alaz.provouchers.storage.VoucherStorage;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

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
    private static final String PERM_RESETUSES = "provouchers.resetuses";
    private static final String PERM_FROMHAND = "provouchers.fromhand";
    private static final String PERM_IMPORT = "provouchers.import";

    /** Valid voucher ids for files created in-game: file-name safe, lower-cased on use. */
    private static final Pattern FILE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** A single subcommand's help metadata; Brigadier nodes carry none of this, so it lives here. */
    private record Sub(String name, String usage, @Nullable String permission) {
    }

    /** The help listing, in display order. Each line is shown only when the sender may use it.
     *  Descriptions are localized under {@code command.help.descriptions.<name>}. */
    private static final List<Sub> HELP = List.of(
        new Sub("give", "give <id> [amount] [player]", PERM_GIVE),
        new Sub("giveall", "giveall <id> [amount] [permission]", PERM_GIVEALL),
        new Sub("redeem", "redeem <code> [argument]", PERM_REDEEM),
        new Sub("preview", "preview", PERM_PREVIEW),
        new Sub("list", "list", PERM_LIST),
        new Sub("reload", "reload [id]", PERM_RELOAD),
        new Sub("resetuses", "resetuses <id> [player]", PERM_RESETUSES),
        new Sub("fromhand", "fromhand <id>", PERM_FROMHAND),
        new Sub("import", "import <source>", PERM_IMPORT),
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
    private final VoucherStorage storage;
    private final Scheduler scheduler;
    private final FromhandGui fromhandGui;
    private final MigrationService migrationService;

    public VoucherCommand(
        VoucherRegistry registry,
        VoucherGiveService giveService,
        RedeemHandler redeemHandler,
        ConfigManager configManager,
        PreviewGui previewGui,
        Text text,
        Messages messages,
        Diagnostics diagnostics,
        VoucherStorage storage,
        Scheduler scheduler,
        FromhandGui fromhandGui,
        MigrationService migrationService
    ) {
        this.registry = registry;
        this.giveService = giveService;
        this.redeemHandler = redeemHandler;
        this.configManager = configManager;
        this.previewGui = previewGui;
        this.text = text;
        this.messages = messages;
        this.diagnostics = diagnostics;
        this.storage = storage;
        this.scheduler = scheduler;
        this.fromhandGui = fromhandGui;
        this.migrationService = migrationService;
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
            .then(Commands.literal("resetuses").requires(perm(PERM_RESETUSES))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggest(builder, registry.voucherIds()))
                    .executes(run(ctx -> resetUses(ctx, false)))
                    .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(run(ctx -> resetUses(ctx, true))))))
            .then(Commands.literal("fromhand").requires(perm(PERM_FROMHAND))
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(run(this::fromhand))))
            .then(Commands.literal("import").requires(perm(PERM_IMPORT))
                .then(Commands.argument("source", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggest(builder, migrationService.presentIds()))
                    .executes(run(ctx ->
                        runImport(ctx.getSource(), StringArgumentType.getString(ctx, "source"))))))
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
                .executes(run(ctx -> giveAll(ctx, 1, false)))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                    .suggests((ctx, builder) -> suggest(builder, AMOUNT_SUGGESTIONS))
                    .executes(run(ctx -> giveAll(ctx, IntegerArgumentType.getInteger(ctx, "amount"), false)))
                    .then(Commands.argument("permission", StringArgumentType.word())
                        .executes(run(ctx ->
                            giveAll(ctx, IntegerArgumentType.getInteger(ctx, "amount"), true))))));
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

    /** Gives to every online player, or only those holding {@code permission} when one is set. */
    private void giveAll(CommandContext<CommandSourceStack> ctx, int amount, boolean hasPermission) {
        CommandSourceStack source = ctx.getSource();
        Player viewer = asPlayer(source);
        String id = StringArgumentType.getString(ctx, "id");
        Voucher voucher = registry.getVoucher(id).orElse(null);
        if (voucher == null) {
            reply(source, messages.get(viewer, "command.unknown-voucher", "id", id));
            return;
        }
        String permission = hasPermission ? StringArgumentType.getString(ctx, "permission") : null;
        int count = 0;
        for (Player online : source.getSender().getServer().getOnlinePlayers()) {
            if (permission != null && !online.hasPermission(permission)) {
                continue;
            }
            giveService.give(online, voucher, amount);
            count++;
        }
        if (count == 0) {
            reply(source, messages.get(viewer, permission != null
                ? "command.giveall.none-matched" : "command.giveall.none-online"));
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

    /**
     * Captures the held item as a new voucher file, behind a confirm GUI that renders the
     * item from its serialized form, so the admin sees the exact round-trip result.
     */
    private void fromhand(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Player admin = asPlayer(source);
        if (admin == null) {
            reply(source, messages.get(null, "command.fromhand.players-only"));
            return;
        }
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        if (!FILE_ID.matcher(id).matches()) {
            reply(source, messages.get(admin, "command.fromhand.bad-id", "id", id));
            return;
        }
        if (fromhandGui.fileExists(id) || registry.getVoucher(id).isPresent()) {
            reply(source, messages.get(admin, "command.fromhand.exists", "id", id));
            return;
        }
        ItemStack held = admin.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            reply(source, messages.get(admin, "command.fromhand.empty-hand"));
            return;
        }
        String serialized = ItemBuilder.serialize(held.asOne());
        ItemStack preview = ItemBuilder.deserialize(serialized);
        if (preview == null) {
            reply(source, messages.get(admin, "command.fromhand.failed"));
            return;
        }
        fromhandGui.open(admin, id, serialized, preview);
    }

    /**
     * Clears the persistent use counters recorded for {@code id}, either for one player or
     * for everyone. Clears both the voucher key and the code key so the id works for either.
     */
    private void resetUses(CommandContext<CommandSourceStack> ctx, boolean hasTarget) {
        CommandSourceStack source = ctx.getSource();
        Player viewer = asPlayer(source);
        String id = StringArgumentType.getString(ctx, "id");
        Player target = hasTarget ? firstTarget(ctx) : null;
        if (hasTarget && target == null) {
            reply(source, messages.get(viewer, "command.give.no-target"));
            return;
        }
        scheduler.async(() -> {
            boolean ok = true;
            try {
                for (String key : new String[] {Voucher.voucherUseKey(id), id}) {
                    if (target != null) {
                        storage.clearUses(key, target.getUniqueId());
                    } else {
                        storage.clearUses(key);
                    }
                }
            } catch (SQLException | RuntimeException ex) {
                ok = false;
            }
            boolean finalOk = ok;
            scheduler.global(() -> reply(source, finalOk
                ? (target != null
                    ? messages.get(viewer, "command.resetuses.success-player", "id", id,
                        "target", target.getName())
                    : messages.get(viewer, "command.resetuses.success", "id", id))
                : messages.get(viewer, "command.resetuses.failed")));
        });
    }

    /** Imports from {@code sourceId}, reloads when anything landed, and reports the outcome. */
    private void runImport(CommandSourceStack source, String sourceId) {
        Player viewer = asPlayer(source);
        Migrator migrator = migrationService.byId(sourceId).orElse(null);
        if (migrator == null || !migrator.isPresent()) {
            reply(source, messages.get(viewer, "command.import.not-found"));
            return;
        }
        MigrationReport result = migrator.migrate();
        if (!result.imported().isEmpty()) {
            configManager.reload();
        }
        reply(source, messages.get(viewer, "command.import.summary",
            "imported", result.imported().size(), "skipped", result.skipped().size()));
        reportLines(source, viewer, "command.import.skipped-line", result.skipped());
        reportLines(source, viewer, "command.import.warning-line", result.warnings());
    }

    /** Reports every line: a migration audit must be complete, so nothing is truncated. */
    private void reportLines(CommandSourceStack source, @Nullable Player viewer, String key,
                             List<String> lines) {
        for (String line : lines) {
            reply(source, messages.get(viewer, key, "line", line));
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
