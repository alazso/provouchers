package so.alaz.provouchers.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.reward.RewardDescriber;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherItemFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A read-only reward preview for a single voucher, opened when a player left-clicks a
 * held voucher (PV-79). Shows the voucher's icon with its guaranteed and random rewards
 * listed in the lore, so a player can see what a voucher grants without redeeming it.
 */
public final class RewardPreviewGui {

    private final VoucherItemFactory factory;
    private final GuiManager guiManager;
    private final Scheduler scheduler;
    private final Text text;

    public RewardPreviewGui(VoucherItemFactory factory, GuiManager guiManager, Scheduler scheduler, Text text) {
        this.factory = factory;
        this.guiManager = guiManager;
        this.scheduler = scheduler;
        this.text = text;
    }

    /** Builds the icon off-thread (skulls may resolve), then opens the preview on the viewer's thread. */
    public void open(Player viewer, Voucher voucher) {
        factory.buildDisplay(voucher, viewer).thenAccept(icon -> {
            ItemStack display = withRewardLore(voucher, icon);
            Gui gui = ChestGui.builder(3)
                .title("<gradient:#FFD700:#FF8A00>" + voucher.id() + "</gradient>")
                .button(13, Button.display(display))
                .build();
            scheduler.entity(viewer, () -> guiManager.open(gui, viewer));
        });
    }

    /**
     * The icon with a reward section appended. When the voucher already shows its rewards
     * in lore ({@code show-rewards}), the icon is used as-is to avoid listing them twice.
     */
    private ItemStack withRewardLore(Voucher voucher, ItemStack icon) {
        if (voucher.showRewards()) {
            return icon;
        }
        ItemStack item = icon.clone();
        item.editMeta(meta -> {
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            List<String> guaranteed = RewardDescriber.describeAll(voucher.rewards());
            List<String> random = RewardDescriber.describeRandom(voucher.randomRewards());
            if (guaranteed.isEmpty() && random.isEmpty()) {
                lore.add(line("<dark_gray>No previewable rewards."));
            } else {
                lore.add(Component.empty());
                lore.add(line("<gray>Rewards:"));
                for (String reward : guaranteed) {
                    lore.add(line("<dark_gray>- <gray>" + reward));
                }
                for (String reward : random) {
                    lore.add(line("<dark_gray>- <gray>" + reward));
                }
            }
            meta.lore(lore);
        });
        return item;
    }

    private Component line(String miniMessage) {
        return text.render(miniMessage).decoration(TextDecoration.ITALIC, false);
    }
}
