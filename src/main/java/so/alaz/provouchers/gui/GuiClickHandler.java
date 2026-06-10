package so.alaz.provouchers.gui;

/** Handles a click on a {@link Button}, returning the {@link GuiAction} to apply. */
@FunctionalInterface
public interface GuiClickHandler {
    GuiAction handle(GuiClick click);
}
