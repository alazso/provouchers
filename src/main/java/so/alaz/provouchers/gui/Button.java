package so.alaz.provouchers.gui;

import org.bukkit.inventory.ItemStack;

/** An item plus its click behaviour. A display-only button does nothing (the click is cancelled). */
public final class Button {

    private final ItemStack item;
    private final GuiClickHandler onClick;

    public Button(ItemStack item, GuiClickHandler onClick) {
        this.item = item;
        this.onClick = onClick;
    }

    /** A button that only displays and does not react to clicks. */
    public static Button display(ItemStack item) {
        return new Button(item, click -> GuiAction.none());
    }

    /** A button with a click handler. */
    public static Button of(ItemStack item, GuiClickHandler onClick) {
        return new Button(item, onClick);
    }

    ItemStack item() {
        return item;
    }

    GuiClickHandler onClick() {
        return onClick;
    }
}
