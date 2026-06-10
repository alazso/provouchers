package so.alaz.provouchers.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens menus and tracks open sessions. Inventories are identified by a {@link GuiHolder} (never by
 * title), and all click/close events route through here with the safety guards (clicks cancelled,
 * drags into the menu blocked) applied by {@link GuiListener}.
 *
 * <p>Must be called from the thread that owns the player (the player's region thread on Folia).
 */
public final class GuiManager {

    private final org.bukkit.plugin.Plugin plugin;
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();

    public GuiManager(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    /** Opens {@code gui} for {@code player}. */
    public void open(Gui gui, Player player) {
        GuiSession session = new GuiSession(player, gui, this);
        GuiHolder holder = new GuiHolder();
        holder.session = session;
        Inventory inventory = Bukkit.createInventory(holder, clampRows(gui.rows()) * 9, gui.title());
        holder.backing = inventory;
        session.inventory = inventory;

        render(session);
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inventory);
    }

    /** Closes all open menus (e.g. on plugin disable). */
    public void closeAll() {
        List.copyOf(sessions.values()).forEach(session -> session.viewer().closeInventory());
    }

    void render(GuiSession session) {
        Inventory inventory = session.inventory;
        if (inventory == null) {
            return;
        }
        inventory.clear();
        session.gui().render(session).forEach((slot, button) -> {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, button.item());
            }
        });
    }

    void handleClick(GuiSession session, int rawSlot, ClickType clickType) {
        Button button = session.gui().render(session).get(rawSlot);
        if (button == null) {
            return;
        }
        applyAction(session, button.onClick().handle(new GuiClick(session, clickType)));
    }

    void untrack(GuiSession session) {
        sessions.remove(session.viewer().getUniqueId(), session);
    }

    private void applyAction(GuiSession session, GuiAction action) {
        @Nullable net.kyori.adventure.text.Component message = action.message();
        if (message != null) {
            session.viewer().sendMessage(message);
        }
        switch (action.kind()) {
            case NONE -> {
            }
            case OPEN -> {
                Gui target = action.target();
                if (target != null) {
                    open(target, session.viewer());
                }
            }
        }
    }

    private static int clampRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }
}
