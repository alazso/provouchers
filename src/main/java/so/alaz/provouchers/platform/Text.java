package so.alaz.provouchers.platform;

import io.github.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Renders MiniMessage strings (RGB, gradients, PlaceholderAPI, MiniPlaceholders) into
 * Adventure {@link Component}s.
 *
 * <p>Resolution order is fixed and load-bearing: PlaceholderAPI placeholders are resolved
 * <em>first</em>, then the result is parsed as MiniMessage. Doing it the other way round lets
 * placeholder output be reinterpreted as markup (the CrazyCrates #878 class of bug).
 * MiniPlaceholders' component-safe tags are added as MiniMessage resolvers. Both integrations
 * are soft: when their plugin is absent the pass is skipped and plain MiniMessage parsing
 * proceeds unchanged.
 */
public final class Text {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final boolean papiAvailable = Classes.present("me.clip.placeholderapi.PlaceholderAPI", getClass());
    private final boolean miniPlaceholdersAvailable =
        Classes.present("io.github.miniplaceholders.api.MiniPlaceholders", getClass());

    /** Parses the input as MiniMessage with no placeholder resolution. */
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

    /** Renders each line via {@link #render(String, Player)}. */
    public List<Component> render(List<String> lines, @Nullable Player viewer) {
        return lines.stream().map(line -> render(line, viewer)).toList();
    }

    /** Renders {@code miniMessage} for the player and sends it as a chat message. */
    public void send(Player player, String miniMessage) {
        player.sendMessage(render(miniMessage, player));
    }

    /**
     * Resolves PlaceholderAPI placeholders in the input against the viewer <em>without</em>
     * MiniMessage parsing, returning the raw resolved string. Returns the input unchanged when PAPI
     * is absent or the viewer is null. Useful for comparing placeholder output (e.g. the papi
     * condition).
     */
    public String resolve(String input, @Nullable Player viewer) {
        return applyPlaceholders(input, viewer);
    }

    /**
     * Parses already-resolved text as MiniMessage, adding MiniPlaceholders' component-safe
     * resolvers when installed. If a MiniPlaceholders expansion throws, it falls back to parsing
     * without them, so a misbehaving provider can never break rendering.
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
     * MiniPlaceholders' resolver covering global and audience placeholders, resolved fresh per
     * render so expansions registered late are picked up. {@code null} (and harmless) when
     * MiniPlaceholders is absent or the provider misbehaves while building the resolver.
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

    /** PAPI first, then MiniMessage - the order is the whole point (CrazyCrates #878). */
    private String applyPlaceholders(String input, @Nullable Player viewer) {
        if (viewer == null || !papiAvailable) {
            return input;
        }
        try {
            // Fail-safe: a misbehaving PlaceholderAPI (or expansion) must not break rendering.
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(viewer, input);
        } catch (RuntimeException ex) {
            return input;
        }
    }

}
