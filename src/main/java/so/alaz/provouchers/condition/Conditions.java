package so.alaz.provouchers.condition;

import java.util.List;

/** Helpers for evaluating groups of conditions. */
public final class Conditions {

    private Conditions() {
    }

    /**
     * Tests the conditions with AND semantics, returning the first failure (so its message can be
     * shown), or a passing result if all pass (or the list is empty).
     */
    public static ConditionResult testAll(List<Condition> conditions, ConditionContext context) {
        for (Condition condition : conditions) {
            ConditionResult result = condition.test(context);
            if (!result.getPassed()) {
                return result;
            }
        }
        return ConditionResult.pass();
    }
}
