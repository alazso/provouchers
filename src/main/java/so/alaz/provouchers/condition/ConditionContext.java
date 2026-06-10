package so.alaz.provouchers.condition;

import org.bukkit.entity.Player;
import so.alaz.provouchers.locale.Messages;

/** What a {@link Condition} is evaluated against: the player being checked, plus the message catalog. */
public final class ConditionContext {

    private final Player player;
    private final Messages messages;

    private ConditionContext(Player player, Messages messages) {
        this.player = player;
        this.messages = messages;
    }

    public Player player() {
        return player;
    }

    /** The message catalog, used to resolve a condition's localized default denial message. */
    public Messages messages() {
        return messages;
    }

    /** A context for the given player. */
    public static ConditionContext of(Player player, Messages messages) {
        return new ConditionContext(player, messages);
    }
}
