package so.alaz.provouchers.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import so.alaz.provouchers.platform.ItemBuilder;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.strata.api.gui.Button;
import so.alaz.strata.api.gui.ChestGui;
import so.alaz.strata.api.gui.Gui;
import so.alaz.strata.api.gui.GuiClickHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The per-voucher admin sub-menu, opened by left-clicking a voucher in the preview.
 * For now an info-only shell (PROVOUCHER-78): it shows the voucher's appearance,
 * reward/condition counts, and flags. Edit actions (enable/disable, reward editor,
 * rename, limits) are added in a later cycle; this is the entry point they hang off.
 */
public final class VoucherAdminMenu {

    private final Text text;

    public VoucherAdminMenu(Text text) {
        this.text = text;
    }

    /**
     * Builds the info menu for {@code voucher}, reusing its already-built display
     * {@code icon}. The Back button runs {@code onBack} (which reopens the preview).
     */
    public Gui build(Voucher voucher, ItemStack icon, GuiClickHandler onBack) {
        return ChestGui.builder(3)
            .title("<gold>" + voucher.id())
            .button(13, Button.display(infoItem(voucher, icon)))
            .button(22, Button.of(backItem(), onBack))
            .build();
    }

    private ItemStack infoItem(Voucher voucher, ItemStack icon) {
        ItemStack item = icon.clone();
        item.editMeta(meta -> {
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(line("<gray>Rewards: <yellow>" + voucher.rewards().size()
                + (voucher.randomRewards().isEmpty() ? "" : " <dark_gray>(+random)")));
            lore.add(line("<gray>Conditions: <yellow>" + voucher.conditionMaps().size()));
            lore.add(line("<gray>Cooldown: <yellow>" + voucher.cooldownSeconds() + "s"));
            lore.add(line("<gray>Stackable: <yellow>" + voucher.stackable()));
            lore.add(line("<gray>Batch open: <yellow>" + voucher.batchOpen()));
            if (voucher.ownerOnly()) {
                lore.add(line("<gray>Owner-only"));
            }
            if (voucher.unredeemable()) {
                lore.add(line("<gray>Unredeemable (display only)"));
            }
            if (voucher.expiry() != null) {
                lore.add(line("<gray>Expiry: <yellow>" + voucher.expiry()));
            }
            lore.add(Component.empty());
            lore.add(line("<dark_gray>In-game editing is coming in a later update."));
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack backItem() {
        return new ItemBuilder(Material.BARRIER).name(line("<red>Back")).build();
    }

    private Component line(String miniMessage) {
        return text.render(miniMessage).decoration(TextDecoration.ITALIC, false);
    }
}
