package so.alaz.provouchers.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Context handed to a {@link Button}'s click handler. */
public final class GuiClick {

    final GuiSession session;
    private final ClickType clickType;

    GuiClick(GuiSession session, ClickType clickType) {
        this.session = session;
        this.clickType = clickType;
    }

    /** The player who clicked. */
    public Player getPlayer() {
        return session.viewer();
    }

    /** How the slot was clicked (left, right, shift, etc.). */
    public ClickType getClickType() {
        return clickType;
    }
}
