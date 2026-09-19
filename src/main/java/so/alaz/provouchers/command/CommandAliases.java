package so.alaz.provouchers.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Normalizes a configured alias list before it reaches the command registrar. */
final class CommandAliases {

    private CommandAliases() {
    }

    /**
     * Trims and lower-cases each alias, dropping blanks, entries containing whitespace, duplicates, and
     * the command's own {@code label}. Order is kept.
     */
    static List<String> normalize(List<String> aliases, String label) {
        Set<String> names = new LinkedHashSet<>();
        for (String alias : aliases) {
            String name = alias.trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty() && name.chars().noneMatch(Character::isWhitespace) && !name.equals(label)) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }
}
