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
}
