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
 * The player's Stash: a paginated chest of the virtual vouchers waiting for them, each rendered from
 * its own icon with a "click to claim" hint. Clicking a card claims that reward and refreshes the
 * menu. Built off-thread (icons may resolve skulls) and opened on the viewer's thread.
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
        this.rows = Math.min(6, Math.max(1, rows));
    }

    /** Loads the viewer's stash, builds the cards off-thread, and opens the menu on their thread. */
    public void open(Player viewer) {
        stashService.entries(viewer.getUniqueId(), entries -> {
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
                scheduler.entity(viewer, () -> text.send(viewer, messages.get(viewer, "stash.empty")));
                return;
            }
            CompletableFuture.allOf(icons.toArray(CompletableFuture[]::new)).thenRun(() -> {
                List<Button> buttons = new ArrayList<>(shown.size());
                for (int i = 0; i < shown.size(); i++) {
                    buttons.add(card(viewer, shown.get(i), icons.get(i).join()));
                }
                int navRow = (rows - 1) * 9;
                PaginatedGui gui = PaginatedGui.builder(rows)
                    .title(title)
                    .content(buttons)
                    .navigation(navRow, navRow + 8, navItem("<yellow>Previous"), navItem("<yellow>Next"))
                    .build();
                scheduler.entity(viewer, () -> guiManager.open(gui, viewer));
            });
        });
    }

    private Button card(Player viewer, StashEntry entry, ItemStack icon) {
        ItemStack card = icon.clone();
        card.editMeta(meta -> {
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            if (entry.amount() > 1) {
                lore.add(line(messages.get(viewer, "stash.lore.amount", "amount", entry.amount())));
            }
            lore.add(line(messages.get(viewer, "stash.lore.claim")));
            meta.lore(lore);
        });
        return Button.of(card, click -> {
            stashService.claim(click.getPlayer(), entry, () -> open(click.getPlayer()));
            return GuiAction.none();
        });
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
