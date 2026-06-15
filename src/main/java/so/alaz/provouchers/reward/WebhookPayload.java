package so.alaz.provouchers.reward;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Renders a Discord webhook payload template (a nested map of strings, numbers, booleans, lists, and
 * maps, as read from config) into a JSON body. Every string value is passed through {@code resolve}
 * (placeholder and PlaceholderAPI substitution) first; the structure is then serialized with Gson, so
 * a resolved value containing quotes or braces is escaped rather than able to corrupt the JSON.
 */
public final class WebhookPayload {

    private static final Gson GSON = new Gson();

    private WebhookPayload() {
    }

    /** Resolves every string in {@code template} via {@code resolve}, then serializes to a JSON object. */
    public static String render(Map<String, Object> template, UnaryOperator<String> resolve) {
        return GSON.toJson(resolveValue(template, resolve));
    }

    private static Object resolveValue(Object value, UnaryOperator<String> resolve) {
        if (value instanceof String string) {
            return resolve.apply(string);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, child) -> out.put(String.valueOf(key), resolveValue(child, resolve)));
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(resolveValue(item, resolve));
            }
            return out;
        }
        return value;
    }
}
