package so.alaz.provouchers.gui;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Declarative result of a click: what the framework should do next. The slot click is always
 * cancelled regardless. Optionally carries a {@link #withMessage(Component) message} sent to the
 * viewer before the action is applied.
 */
public final class GuiAction {

    enum Kind {NONE, OPEN}

    private final Kind kind;
    @Nullable private final Gui target;
    @Nullable private final Component message;

    private GuiAction(Kind kind, @Nullable Gui target, @Nullable Component message) {
        this.kind = kind;
        this.target = target;
        this.message = message;
    }

    /** Keep the menu open and unchanged. */
    public static GuiAction none() {
        return new GuiAction(Kind.NONE, null, null);
    }

    /** Open another menu for the viewer. */
    public static GuiAction open(Gui gui) {
        return new GuiAction(Kind.OPEN, gui, null);
    }

    /** A copy that also sends a message to the viewer. */
    public GuiAction withMessage(Component message) {
        return new GuiAction(kind, target, message);
    }

    Kind kind() {
        return kind;
    }

    @Nullable
    Gui target() {
        return target;
    }

    @Nullable
    Component message() {
        return message;
    }
}
