package so.alaz.provouchers.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public final class ConfigFile {

    private ConfigFile() {
    }

    /**
     * Why {@code file} cannot be read as YAML, or {@code null} when it parses. A reload checks this before it
     * replaces the settings in effect: {@code JavaPlugin.reloadConfig()} answers a missing or malformed file with
     * an empty configuration, which reads back as every setting at its default.
     */
    public static @Nullable String unreadableReason(File file) {
        try {
            new YamlConfiguration().load(file);
            return null;
        } catch (IOException | InvalidConfigurationException e) {
            String message = e.getMessage();
            return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
        }
    }
}
