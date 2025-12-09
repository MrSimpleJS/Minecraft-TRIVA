package de.varo.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight YAML-based translation loader.
 * Reads lang/messages_<code>language</code>.yml from the plugin resources,
 * falls back to German (de) if a key is missing.
 */
public final class Lang {
    private static final Map<String, String> STRINGS = new HashMap<>();
    private static String activeLanguage = "de";

    private Lang() {}

    /**
     * Load translations for the given language code (e.g. "de", "en").
     * Falls back to German for missing keys.
     */
    public static void load(JavaPlugin plugin, String language) {
        activeLanguage = (language == null || language.isEmpty())
                ? "de"
                : language.toLowerCase(Locale.ROOT);

        STRINGS.clear();
        // Fallback first, then override with requested language
        loadFile(plugin, "lang/messages_de.yml");
        if (!"de".equals(activeLanguage)) {
            loadFile(plugin, "lang/messages_" + activeLanguage + ".yml");
        }
    }

    private static void loadFile(JavaPlugin plugin, String path) {
        InputStream stream = null;
        try {
            File out = new File(plugin.getDataFolder(), path);
            if (out.exists()) {
                stream = new FileInputStream(out);
            } else {
                stream = plugin.getResource(path);
            }
            if (stream == null) return;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (String key : yaml.getKeys(true)) {
                if (yaml.isConfigurationSection(key)) continue;
                STRINGS.put(key, Objects.toString(yaml.get(key), key));
            }
        } catch (Exception ignored) {
            // Swallow and continue with whatever we already have
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Translate a key and format it with MessageFormat.
     */
    public static String tr(String key, Object... args) {
        String base = STRINGS.getOrDefault(key, key);
        if (args != null && args.length > 0) {
            try {
                return MessageFormat.format(base, args);
            } catch (IllegalArgumentException ignored) {
                return base;
            }
        }
        return base;
    }

    public static String getActiveLanguage() {
        return activeLanguage;
    }
}
