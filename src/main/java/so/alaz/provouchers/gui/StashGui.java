package so.alaz.provouchers.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import so.alaz.provouchers.locale.Messages;
import so.alaz.provouchers.platform.ItemBuilder;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.stash.StashEntry;
import so.alaz.provouchers.stash.StashService;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherItemFactory;
import so.alaz.provouchers.voucher.VoucherRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The player's Stash: a paginated chest of their waiting virtual vouchers, each rendered from its own
 * icon with an amount, an optional expiry, and a "click to claim" hint. The bottom row is a menu bar
 * (previous, claim-all, next), separated from the cards by a buffer row of panes. Clicking a card
 * claims that reward and refreshes; claiming the last one closes the menu. Cards are built on the
 * viewer's thread (icons may resolve skulls) and the menu is opened there too.
 */
public final class StashGui {

    private final StashService stashService;
    private final VoucherRegistry registry;
    private final VoucherItemFactory factory;
    private final GuiManager guiManager;
    private final Scheduler scheduler;
    private final Text text;
    private final Messages messages;
    private final String title;
    private final int rows;
    private final ItemStack pane;

    public StashGui(StashService stashService, VoucherRegistry registry, VoucherItemFactory factory,
                    GuiManager guiManager, Scheduler scheduler, Text text, Messages messages,
                    String title, int rows) {
        this.stashService = stashService;
        this.registry = registry;
        this.factory = factory;
        this.guiManager = guiManager;
        this.scheduler = scheduler;
        this.text = text;
        this.messages = messages;
        this.title = title;
        // At least three rows so content, the buffer row, and the menu bar each have their own.
        this.rows = Math.min(6, Math.max(3, rows));
        this.pane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
            .name(text.render(" ").decoration(TextDecoration.ITALIC, false))
            .build();
    }

    /** Opens the viewer's Stash on its first page. */
    public void open(Player viewer) {
        refresh(viewer, false, 0);
    }

    /**
     * Loads the viewer's entries and rebuilds the menu at {@code page}. {@code closeIfEmpty} closes a
     * stale menu when a claim empties the Stash, so the last claimed card never lingers on screen.
     */
    private void refresh(Player viewer, boolean closeIfEmpty, int page) {
        stashService.entries(viewer.getUniqueId(),
            entries -> scheduler.entity(viewer, () -> build(viewer, entries, closeIfEmpty, page)));
    }

    private void build(Player viewer, List<StashEntry> entries, boolean closeIfEmpty, int page) {
        List<StashEntry> shown = new ArrayList<>();
        List<CompletableFuture<ItemStack>> icons = new ArrayList<>();
        for (StashEntry entry : entries) {
            Voucher voucher = registry.getVoucher(entry.voucherId()).orElse(null);
            if (voucher == null) {
                continue;
            }
            shown.add(entry);
            icons.add(factory.buildDisplay(voucher, viewer));
        }
        if (shown.isEmpty()) {
            if (closeIfEmpty) {
                viewer.closeInventory();
            }
            text.send(viewer, messages.get(viewer, "stash.empty"));
            return;
        }
        // Once every icon has resolved, build the cards and open on the viewer's thread: card() edits
        // item meta, which must not run on the async thread that completed a skull icon's future.
        CompletableFuture.allOf(icons.toArray(CompletableFuture[]::new)).thenRun(() ->
            scheduler.entity(viewer, () -> {
                List<Button> buttons = new ArrayList<>(shown.size());
                for (int i = 0; i < shown.size(); i++) {
                    buttons.add(card(viewer, shown.get(i), icons.get(i).join()));
                }
                int menuRow = (rows - 1) * 9;
                PaginatedGui gui = PaginatedGui.builder(rows)
                    .title(title)
                    .contentRows(rows - 2)
                    .content(buttons)
                    .navigation(menuRow, menuRow + 8, navItem("<yellow>Previous"), navItem("<yellow>Next"))
                    .fixed(menuRow + 3, claimAll(viewer, shown))
                    .fixed(menuRow + 5, closeButton(viewer))
                    .filler(pane)
                    .build();
                guiManager.open(gui, viewer, page);
            }));
    }

    private Button card(Player viewer, StashEntry entry, ItemStack icon) {
        ItemStack card = icon.clone();
        // Show the count on the stack (the corner caps at 64); the true total is in the lore below.
        card.setAmount(Math.min(64, Math.max(1, entry.amount())));
        card.editMeta(meta -> {
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            if (entry.amount() > 1) {
                lore.add(line(messages.get(viewer, "stash.lore.amount", "amount", entry.amount())));
            }
            if (entry.expiresAt() != null) {
                lore.add(line(messages.get(viewer, "stash.lore.expires",
                    "time", relativeTime(entry.expiresAt() - System.currentTimeMillis()))));
            }
            lore.add(line(messages.get(viewer, "stash.lore.claim")));
            meta.lore(lore);
        });
        return Button.of(card, click -> {
            int page = click.session.page();
            stashService.claim(click.getPlayer(), entry, () -> refresh(click.getPlayer(), true, page));
            return GuiAction.none();
        });
    }

    private Button claimAll(Player viewer, List<StashEntry> entries) {
        ItemStack icon = new ItemBuilder(Material.CHEST)
            .name(line(messages.get(viewer, "stash.claim-all")))
            .build();
        return Button.of(icon, click -> {
            int page = click.session.page();
            stashService.claimAll(click.getPlayer(), entries, () -> refresh(click.getPlayer(), true, page));
            return GuiAction.none();
        });
    }

    private Button closeButton(Player viewer) {
        ItemStack icon = new ItemBuilder(Material.BARRIER)
            .name(line(messages.get(viewer, "stash.close")))
            .build();
        return Button.of(icon, click -> {
            click.getPlayer().closeInventory();
            return GuiAction.none();
        });
    }

    /** A short relative duration for a card's expiry line: days, else hours, else at least one minute. */
    private static String relativeTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds >= 86_400) {
            return (seconds / 86_400) + "d";
        }
        if (seconds >= 3_600) {
            return (seconds / 3_600) + "h";
        }
        return Math.max(1, seconds / 60) + "m";
    }

    private Component line(String miniMessage) {
        return text.render(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack navItem(String name) {
        return new ItemBuilder(Material.ARROW)
            .name(text.render(name).decoration(TextDecoration.ITALIC, false))
            .build();
    }
}
