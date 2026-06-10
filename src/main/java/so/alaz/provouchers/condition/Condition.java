package so.alaz.provouchers.condition;

/** A predicate over a {@link ConditionContext} that passes or fails with a message. */
@FunctionalInterface
public interface Condition {
    ConditionResult test(ConditionContext context);
}
