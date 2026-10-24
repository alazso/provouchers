package so.alaz.provouchers.comficonf;

import com.comficonf.api.ConfigPath;
import com.comficonf.api.ConfigValues;
import com.comficonf.api.schema.Choice;
import com.comficonf.api.schema.ConfigField;
import com.comficonf.api.schema.ConfigSchema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Checks values against the declared constraints this schema uses (required, type, choices, numeric bounds, string
 * patterns and lengths, unique list items), as the bridge does before {@code validate}. Returns one line per problem.
 */
final class SchemaConformance {

    private SchemaConformance() {
    }

    static List<String> problems(ConfigSchema schema, ConfigValues values) {
        List<String> problems = new ArrayList<>();
        check(schema.fields(), values.asMap(), "", problems);
        return problems;
    }

    private static void check(List<ConfigField> fields, Map<String, ?> values, String parent, List<String> problems) {
        for (ConfigField field : fields) {
            String path = ConfigPath.child(parent, field.key());
            Object value = values.get(field.key());
            switch (field.type()) {
                case OBJECT -> check(field.fields(), value instanceof Map<?, ?> map ? cast(map) : Map.of(), path,
                    problems);
                case STRING, TEXT -> {
                    if (requiredPresent(field, value, path, problems) && expect(value, String.class, path, problems)) {
                        checkText(field, (String) value, path, problems);
                    }
                }
                case BOOLEAN -> {
                    if (requiredPresent(field, value, path, problems)) {
                        expect(value, Boolean.class, path, problems);
                    }
                }
                case INTEGER -> {
                    if (requiredPresent(field, value, path, problems) && expect(value, Long.class, path, problems)) {
                        long number = (Long) value;
                        if (field.min() != null && number < field.min().longValue()
                            || field.max() != null && number > field.max().longValue()) {
                            problems.add(path + ": out of range");
                        }
                    }
                }
                case ENUM -> {
                    if (requiredPresent(field, value, path, problems) && expect(value, String.class, path, problems)
                        && field.choices().stream().map(Choice::value).noneMatch(value::equals)) {
                        problems.add(path + ": not one of the choices");
                    }
                }
                case STRING_LIST -> {
                    if (requiredPresent(field, value, path, problems) && expect(value, List.class, path, problems)) {
                        List<?> items = (List<?>) value;
                        if (field.uniqueItems() && new HashSet<>(items).size() != items.size()) {
                            problems.add(path + ": duplicate items");
                        }
                        for (int i = 0; i < items.size(); i++) {
                            String itemPath = ConfigPath.index(path, i);
                            if (expect(items.get(i), String.class, itemPath, problems)) {
                                checkText(field, (String) items.get(i), itemPath, problems);
                            }
                        }
                    }
                }
                default -> problems.add(path + ": field type " + field.type() + " is not checked here");
            }
        }
    }

    /** Whether a value is present to check further; a missing required value is a problem. */
    private static boolean requiredPresent(ConfigField field, Object value, String path, List<String> problems) {
        if (value == null) {
            if (field.required()) {
                problems.add(path + ": required");
            }
            return false;
        }
        return true;
    }

    private static boolean expect(Object value, Class<?> type, String path, List<String> problems) {
        if (!type.isInstance(value)) {
            problems.add(path + ": expected " + type.getSimpleName() + ", found " + value.getClass().getSimpleName());
            return false;
        }
        return true;
    }

    private static void checkText(ConfigField field, String text, String path, List<String> problems) {
        int length = text.codePointCount(0, text.length());
        if (field.minLength() != null && length < field.minLength()
            || field.maxLength() != null && length > field.maxLength()) {
            problems.add(path + ": length out of range");
        }
        if (field.pattern() != null && !text.matches(field.pattern())) {
            problems.add(path + ": does not match the pattern");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> cast(Map<?, ?> map) {
        return (Map<String, ?>) map;
    }
}
