package so.alaz.provouchers.condition;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/** The outcome of evaluating a {@link Condition}: passed, or failed with a message to show the player. */
public final class ConditionResult {

    private static final ConditionResult PASS = new ConditionResult(true, null);

    private final boolean passed;
    @Nullable private final Component message;

    private ConditionResult(boolean passed, @Nullable Component message) {
        this.passed = passed;
        this.message = message;
    }

    /** A passing result. */
    public static ConditionResult pass() {
        return PASS;
    }

    /** A failing result carrying the message to show the player. */
    public static ConditionResult fail(Component message) {
        return new ConditionResult(false, message);
    }

    public boolean getPassed() {
        return passed;
    }

    @Nullable
    public Component getMessage() {
        return message;
    }
}
