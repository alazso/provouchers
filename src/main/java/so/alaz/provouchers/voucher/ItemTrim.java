package so.alaz.provouchers.voucher;

/**
 * An armor trim by material and pattern key (for example {@code quartz} / {@code sentry}),
 * kept as names and resolved against the registry when the item is built. Applies only to
 * armor; a non-armor item silently ignores it.
 */
public record ItemTrim(String material, String pattern) {

    public ItemTrim {
        if (material == null || material.isBlank() || pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("armor trim needs both a material and a pattern");
        }
    }
}
