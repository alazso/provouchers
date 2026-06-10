package so.alaz.provouchers.gui;

import net.kyori.adventure.text.Component;

import java.util.Map;

/**
 * A menu blueprint. The framework calls {@link #render} to obtain the slot-to-{@link Button} map
 * for a session (so paginated menus can vary by {@link GuiSession#page()}), and {@link #title}/
 * {@link #rows} to size the inventory. Use the {@link ChestGui}/{@link PaginatedGui} builders, or
 * implement directly for a custom menu.
 */
public interface Gui {

    /** The inventory title, fixed at open time. */
    Component title();

    /** Row count, 1 to 6. */
    int rows();

    /** The buttons to place this render, keyed by raw slot index. */
    Map<Integer, Button> render(GuiSession session);
}
