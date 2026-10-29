package so.alaz.provouchers.comficonf;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.charset.StandardCharsets;

final class Yaml {

    private Yaml() {
    }

    /**
     * Parses YAML bytes with comments kept, so the document can be saved again without losing them. A
     * {@link YamlConfiguration} is not thread-safe: every callback parses its own.
     */
    static YamlConfiguration parse(byte[] content) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.loadFromString(new String(content, StandardCharsets.UTF_8));
        return yaml;
    }

    /**
     * Encodes an emitted document with the line endings of the file it replaces. YAML emission always writes LF,
     * which would otherwise rewrite every line of a file saved with CRLF.
     */
    static byte[] encodeLike(String yaml, byte[] original) {
        String text = usesCarriageReturns(original) ? yaml.replace("\r\n", "\n").replace("\n", "\r\n") : yaml;
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** Whether most of the line breaks in {@code content} are CRLF. */
    static boolean usesCarriageReturns(byte[] content) {
        int breaks = 0;
        int carriageReturns = 0;
        for (int i = 0; i < content.length; i++) {
            if (content[i] == '\n') {
                breaks++;
                if (i > 0 && content[i - 1] == '\r') {
                    carriageReturns++;
                }
            }
        }
        return carriageReturns * 2 > breaks;
    }
}
