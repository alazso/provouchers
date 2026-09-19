package so.alaz.provouchers.platform;

import io.github.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Renders MiniMessage strings (RGB, gradients, PlaceholderAPI, MiniPlaceholders) into
 * Adventure {@link Component}s.
 *
 * <p>The resolution order must be kept: PlaceholderAPI placeholders are resolved <em>first</em>,
 * then the result is parsed as MiniMessage, with MiniPlaceholders' component-safe tags added as
 * resolvers. Both integrations are optional: when a plugin is absent, its pass is skipped.
 */
public final class Text {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final boolean papiAvailable = Classes.present("me.clip.placeholderapi.PlaceholderAPI", getClass());
    private final boolean miniPlaceholdersAvailable =
        Classes.present("io.github.miniplaceholders.api.MiniPlaceholders", getClass());

    /** Parses the input as MiniMessage, without PlaceholderAPI resolution. */
    public Component render(String input) {
        return deserialize(applyPlaceholders(input, null));
    }

    /**
     * Resolves PlaceholderAPI placeholders against the viewer (when non-null and PAPI is
     * present), then parses the result as MiniMessage.
     */
    public Component render(String input, @Nullable Player viewer) {
        return deserialize(applyPlaceholders(input, viewer));
    }

    public List<Component> render(List<String> lines, @Nullable Player viewer) {
        return lines.stream().map(line -> render(line, viewer)).toList();
    }

    public void send(Player player, String miniMessage) {
        player.sendMessage(render(miniMessage, player));
    }

    /**
     * Resolves PlaceholderAPI placeholders in the input against the viewer, without MiniMessage
     * parsing. Returns the input unchanged when PAPI is absent or throws, or the viewer is null.
     */
    public String resolve(String input, @Nullable Player viewer) {
        return applyPlaceholders(input, viewer);
    }

    /** Renders the input (PlaceholderAPI, then MiniMessage) and flattens it to plain text. */
    public String plain(String input, @Nullable Player viewer) {
        return PlainTextComponentSerializer.plainText().serialize(render(input, viewer));
    }

    /**
     * Parses already-resolved text as MiniMessage with MiniPlaceholders' resolvers when installed,
     * falling back to parsing without them if an expansion throws.
     */
    private Component deserialize(String resolved) {
        TagResolver miniPlaceholders = miniPlaceholderResolver();
        if (miniPlaceholders == null) {
            return mm.deserialize(resolved);
        }
        try {
            return mm.deserialize(resolved, miniPlaceholders);
        } catch (RuntimeException ex) {
            return mm.deserialize(resolved);
        }
    }

    /**
     * MiniPlaceholders' resolver for global and audience placeholders, built per render so
     * expansions registered later are included. {@code null} when MiniPlaceholders is absent or
     * throws while building it.
     */
    @Nullable
    private TagResolver miniPlaceholderResolver() {
        if (!miniPlaceholdersAvailable) {
            return null;
        }
        try {
            return MiniPlaceholders.audienceGlobalPlaceholders();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String applyPlaceholders(String input, @Nullable Player viewer) {
        if (viewer == null || !papiAvailable) {
            return input;
        }
        try {
            // A throwing PlaceholderAPI expansion must not break rendering.
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(viewer, input);
        } catch (RuntimeException ex) {
            return input;
        }
    }

}
