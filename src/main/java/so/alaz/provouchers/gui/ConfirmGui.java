package so.alaz.provouchers.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import so.alaz.provouchers.platform.ItemBuilder;
import so.alaz.provouchers.platform.Scheduler;
import so.alaz.provouchers.platform.Text;
import so.alaz.provouchers.voucher.Voucher;
import so.alaz.provouchers.voucher.VoucherItemFactory;

import java.util.List;

/**
 * The GUI variant of two-step confirmation ({@code redeem.confirm-style: gui}). Shows the
 * voucher between a confirm and a cancel button; confirming resumes the redemption,
 * cancelling clears the pending confirmation. Closing the menu without clicking behaves
 * like ignoring the chat prompt: the pending confirmation simply lapses with its window.
 */
public final class ConfirmGui {

    private final VoucherItemFactory factory;
    private final GuiManager guiManager;
    private final Scheduler scheduler;
    private final Text text;

    public ConfirmGui(VoucherItemFactory factory, GuiManager guiManager, Scheduler scheduler, Text text) {
        this.factory = factory;
        this.guiManager = guiManager;
        this.scheduler = scheduler;
        this.text = text;
    }

    /** Builds the voucher display off-thread, then opens the menu on the player's thread. */
    public void open(Player player, Voucher voucher, Runnable onConfirm, Runnable onCancel) {
        factory.buildDisplay(voucher, player).thenAccept(display -> {
            Gui gui = ChestGui.builder(3)
                .title("<gradient:#FFD700:#FF8A00>Confirm: " + voucher.id() + "</gradient>")
                .button(11, Button.of(confirmIcon(player), click -> {
                    click.getPlayer().closeInventory();
                    onConfirm.run();
                    return GuiAction.none();
                }))
                .button(13, Button.display(display))
                .button(15, Button.of(cancelIcon(player), click -> {
                    click.getPlayer().closeInventory();
                    onCancel.run();
                    return GuiAction.none();
                }))
                .build();
            scheduler.entity(player, () -> guiManager.open(gui, player));
        });
    }

    private org.bukkit.inventory.ItemStack confirmIcon(Player player) {
        return new ItemBuilder(Material.LIME_CONCRETE)
            .name(text.render("<green><b>Redeem</b>", player))
            .lore(List.of(text.render("<gray>Redeem this voucher now.", player)))
            .build();
    }

    private org.bukkit.inventory.ItemStack cancelIcon(Player player) {
        return new ItemBuilder(Material.RED_CONCRETE)
            .name(text.render("<red><b>Cancel</b>", player))
            .lore(List.of(text.render("<gray>Keep the voucher unredeemed.", player)))
            .build();
    }
}
