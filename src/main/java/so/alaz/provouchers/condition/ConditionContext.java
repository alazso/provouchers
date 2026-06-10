package so.alaz.provouchers.condition;

import org.bukkit.entity.Player;

/** What a {@link Condition} is evaluated against: the player being checked. */
public final class ConditionContext {

    private final Player player;

    private ConditionContext(Player player) {
        this.player = player;
    }

    public Player player() {
        return player;
    }

    /** A context for the given player. */
    public static ConditionContext of(Player player) {
        return new ConditionContext(player);
    }
}
