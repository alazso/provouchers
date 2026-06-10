package so.alaz.provouchers.gui;

import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import so.alaz.provouchers.give.VoucherGiveService;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherItemFactory;
import so.alaz.provouchers.voucher.VoucherRegistry;
import so.alaz.strata.api.gui.Button;
import so.alaz.strata.api.gui.GuiAction;
import so.alaz.strata.api.gui.GuiManager;
import so.alaz.strata.api.gui.ItemBuilder;
import so.alaz.strata.api.gui.PaginatedGui;
import so.alaz.strata.api.scheduler.PlatformScheduler;
import so.alaz.strata.api.text.TextRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The staff voucher browser: a paginated chest of every loaded voucher. Right-click an
 * entry to give yourself one; left-click to open its admin info menu. Opened via
 * {@code /voucher preview} behind {@code provouchers.preview}.
 */
public final class PreviewGui {

    private final VoucherRegistry registry;
    private final VoucherItemFactory factory;
    private final VoucherGiveService giveService;
    private final VoucherAdminMenu adminMenu;
    private final GuiManager guiManager;
    private final PlatformScheduler scheduler;
    private final TextRenderer text;

    public PreviewGui(VoucherRegistry registry, VoucherItemFactory factory, VoucherGiveService giveService,
                      VoucherAdminMenu adminMenu, GuiManager guiManager, PlatformScheduler scheduler,
                      TextRenderer text) {
        this.registry = registry;
        this.factory = factory;
        this.giveService = giveService;
        this.adminMenu = adminMenu;
        this.guiManager = guiManager;
        this.scheduler = scheduler;
        this.text = text;
    }

    /** Builds the preview off-thread (icons may resolve skulls) and opens it on the viewer's thread. */
    public void open(Player viewer) {
        List<Voucher> vouchers = new ArrayList<>(registry.vouchers());
        vouchers.sort(Comparator.comparing(Voucher::id));
        if (vouchers.isEmpty()) {
            viewer.sendMessage(text.render("<gray>No vouchers are loaded."));
            return;
        }
        List<CompletableFuture<ItemStack>> icons =
            vouchers.stream().map(voucher -> factory.buildDisplay(voucher, viewer)).toList();
        CompletableFuture.allOf(icons.toArray(CompletableFuture[]::new)).thenRun(() -> {
            List<Button> buttons = new ArrayList<>(vouchers.size());
            for (int i = 0; i < vouchers.size(); i++) {
                buttons.add(entry(vouchers.get(i), icons.get(i).join()));
            }
            PaginatedGui gui = PaginatedGui.builder(6)
                .title("<gradient:#FFD700:#FF8A00>Vouchers</gradient>")
                .content(buttons)
                .navigation(45, 53, navItem("<yellow>Previous"), navItem("<yellow>Next"))
                .build();
            scheduler.entity(viewer, () -> guiManager.open(gui, viewer));
        });
    }

    private Button entry(Voucher voucher, ItemStack icon) {
        return Button.of(icon, click -> {
            if (click.getClickType().isRightClick()) {
                giveService.give(click.getPlayer(), voucher, 1);
                return GuiAction.none()
                    .withMessage(text.render("<green>Gave <gold>" + voucher.id() + "</gold>."));
            }
            return GuiAction.open(adminMenu.build(voucher, icon, back -> {
                open(back.getPlayer());
                return GuiAction.none();
            }));
        });
    }

    private ItemStack navItem(String name) {
        return new ItemBuilder(Material.ARROW)
            .name(text.render(name).decoration(TextDecoration.ITALIC, false))
            .build();
    }
}
