package so.alaz.provouchers.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * A paged chest menu: a flat list of content {@link Button}s laid into a set of content slots, with
 * optional previous/next navigation that updates {@link GuiSession#page()} and refreshes. Page
 * state lives in the session, so one instance can serve many viewers independently.
 */
public final class PaginatedGui implements Gui {

    private static final int COLUMNS = 9;

    private final Component title;
    private final int rows;
    private final List<Integer> contentSlots;
    private final List<Button> content;
    private final int previousSlot;
    private final int nextSlot;
    @Nullable private final ItemStack previousItem;
    @Nullable private final ItemStack nextItem;
    @Nullable private final ItemStack filler;
    private final Map<Integer, Button> fixed;

    private PaginatedGui(Component title, int rows, List<Integer> contentSlots, List<Button> content,
                         int previousSlot, int nextSlot,
                         @Nullable ItemStack previousItem, @Nullable ItemStack nextItem,
                         @Nullable ItemStack filler, Map<Integer, Button> fixed) {
        this.title = title;
        this.rows = rows;
        this.contentSlots = contentSlots;
        this.content = content;
        this.previousSlot = previousSlot;
        this.nextSlot = nextSlot;
        this.previousItem = previousItem;
        this.nextItem = nextItem;
        this.filler = filler;
        this.fixed = fixed;
    }

    @Override
    public Component title() {
        return title;
    }

    @Override
    public int rows() {
        return rows;
    }

    private int pageCount() {
        return contentSlots.isEmpty() ? 1
            : Math.max(1, (int) Math.ceil((double) content.size() / contentSlots.size()));
    }

    @Override
    public Map<Integer, Button> render(GuiSession session) {
        int pages = pageCount();
        int page = Math.max(0, Math.min(session.page(), pages - 1));
        Map<Integer, Button> result = new HashMap<>();

        int start = page * contentSlots.size();
        for (int i = 0; i < contentSlots.size(); i++) {
            int index = start + i;
            if (index < content.size()) {
                result.put(contentSlots.get(i), content.get(index));
            }
        }

        if (page > 0 && previousItem != null) {
            result.put(previousSlot, new Button(previousItem, click -> {
                click.session.setPage(click.session.page() - 1);
                click.session.refresh();
                return GuiAction.none();
            }));
        }
        if (page < pages - 1 && nextItem != null) {
            result.put(nextSlot, new Button(nextItem, click -> {
                click.session.setPage(click.session.page() + 1);
                click.session.refresh();
                return GuiAction.none();
            }));
        }
        // Fixed controls (e.g. claim-all) never override content or navigation.
        fixed.forEach(result::putIfAbsent);
        // The filler dresses the menu bar: every non-content slot not already taken by a button.
        if (filler != null) {
            for (int slot = 0; slot < rows * COLUMNS; slot++) {
                if (!contentSlots.contains(slot) && !result.containsKey(slot)) {
                    result.put(slot, Button.display(filler));
                }
            }
        }
        return result;
    }

    public static Builder builder(int rows) {
        return new Builder(rows);
    }

    public static final class Builder {

        private final int rows;
        private Component title = Component.empty();
        private List<Integer> contentSlots;
        private List<Button> content = List.of();
        private int previousSlot;
        private int nextSlot;
        @Nullable private ItemStack previousItem;
        @Nullable private ItemStack nextItem;
        @Nullable private ItemStack filler;
        private final Map<Integer, Button> fixed = new HashMap<>();

        Builder(int rows) {
            int clamped = Math.max(1, Math.min(6, rows));
            this.rows = clamped;
            this.contentSlots = IntStream.range(0, (clamped - 1) * COLUMNS).boxed().toList();
            this.previousSlot = (clamped - 1) * COLUMNS;
            this.nextSlot = clamped * COLUMNS - 1;
        }

        public Builder title(String miniMessage) {
            this.title = MiniMessage.miniMessage().deserialize(miniMessage);
            return this;
        }

        public Builder content(List<Button> content) {
            this.content = content;
            return this;
        }

        /** Restricts content to the first {@code contentRows} rows, freeing the rest for a menu bar. */
        public Builder contentRows(int contentRows) {
            int clamped = Math.max(1, Math.min(rows, contentRows));
            this.contentSlots = IntStream.range(0, clamped * COLUMNS).boxed().toList();
            return this;
        }

        /** Configures navigation: which slots hold the prev/next buttons and their icons. */
        public Builder navigation(int previousSlot, int nextSlot, ItemStack previousItem, ItemStack nextItem) {
            this.previousSlot = previousSlot;
            this.nextSlot = nextSlot;
            this.previousItem = previousItem;
            this.nextItem = nextItem;
            return this;
        }

        /** Fills every non-content slot left empty (the buffer row and menu bar) with {@code filler}. */
        public Builder filler(ItemStack filler) {
            this.filler = filler;
            return this;
        }

        /** Pins a button to a slot (a menu-bar control such as claim-all); never overrides content. */
        public Builder fixed(int slot, Button button) {
            this.fixed.put(slot, button);
            return this;
        }

        public PaginatedGui build() {
            return new PaginatedGui(title, rows, contentSlots, List.copyOf(content),
                previousSlot, nextSlot, previousItem, nextItem, filler, Map.copyOf(fixed));
        }
    }
}
