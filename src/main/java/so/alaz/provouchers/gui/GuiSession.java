package so.alaz.provouchers.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

/**
 * A live, open menu for one viewer. Carries the current page (for paginated menus) and lets a
 * click handler {@link #refresh()} the menu. Created by the {@link GuiManager} when a {@link Gui}
 * is opened.
 */
public final class GuiSession {

    private final Player viewer;
    private final Gui gui;
    private final GuiManager manager;
    @Nullable Inventory inventory;
    private int page;

    GuiSession(Player viewer, Gui gui, GuiManager manager) {
        this.viewer = viewer;
        this.gui = gui;
        this.manager = manager;
    }

    public Player viewer() {
        return viewer;
    }

    public Gui gui() {
        return gui;
    }

    /** Current page (0-based); meaningful for paginated menus. */
    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    /** Re-renders the current menu's items (does not change the title). */
    public void refresh() {
        manager.render(this);
    }
}
