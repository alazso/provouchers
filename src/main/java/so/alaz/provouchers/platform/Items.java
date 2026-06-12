package so.alaz.provouchers.platform;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Inventory delivery shared by item rewards and refunds. */
public final class Items {

    private Items() {
    }

    /** Adds the item to the player's inventory, dropping whatever does not fit at their feet. */
    public static void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
