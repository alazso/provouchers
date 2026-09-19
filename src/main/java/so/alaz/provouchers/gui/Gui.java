package so.alaz.provouchers.gui;

import net.kyori.adventure.text.Component;

import java.util.Map;

/**
 * A menu blueprint. {@link GuiManager} creates the inventory from {@link #title} and {@link #rows}, then
 * calls {@link #render} for the session's slot-to-{@link Button} map, so a paginated menu can vary by
 * {@link GuiSession#page()}.
 */
public interface Gui {

    /** The inventory title, fixed at open time. */
    Component title();

    /** Row count, 1 to 6. */
    int rows();

    /** The buttons to place this render, keyed by raw slot index. */
    Map<Integer, Button> render(GuiSession session);
}
