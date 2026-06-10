package so.alaz.provouchers.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * A static chest menu: fixed buttons at fixed slots.
 *
 * <pre>{@code
 * Gui gui = ChestGui.builder(3)
 *     .title("<gold>Menu")
 *     .button(13, Button.of(icon, click -> ...))
 *     .build();
 * }</pre>
 */
public final class ChestGui implements Gui {

    private final Component title;
    private final int rows;
    private final Map<Integer, Button> buttons;

    private ChestGui(Component title, int rows, Map<Integer, Button> buttons) {
        this.title = title;
        this.rows = rows;
        this.buttons = buttons;
    }

    @Override
    public Component title() {
        return title;
    }

    @Override
    public int rows() {
        return rows;
    }

    @Override
    public Map<Integer, Button> render(GuiSession session) {
        return buttons;
    }

    public static Builder builder(int rows) {
        return new Builder(rows);
    }

    public static final class Builder {

        private final int rows;
        private Component title = Component.empty();
        private final Map<Integer, Button> buttons = new HashMap<>();

        Builder(int rows) {
            this.rows = Math.max(1, Math.min(6, rows));
        }

        public Builder title(String miniMessage) {
            this.title = MiniMessage.miniMessage().deserialize(miniMessage);
            return this;
        }

        public Builder button(int slot, Button button) {
            buttons.put(slot, button);
            return this;
        }

        public ChestGui build() {
            return new ChestGui(title, rows, Map.copyOf(buttons));
        }
    }
}
