package de.varo.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class AcProfileUtil {
    private AcProfileUtil() {}

    private static File profilesDir(JavaPlugin plugin) {
        File dir = new File(plugin.getDataFolder(), "acprofiles");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static List<String> list(JavaPlugin plugin) {
        File dir = profilesDir(plugin);
        String[] names = dir.list((d, n) -> n.endsWith(".yml"));
        if (names == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String n : names) result.add(n.replaceFirst("\\.yml$", ""));
        result.sort(String::compareToIgnoreCase);
        return result;
    }

    public static void save(JavaPlugin plugin, String name, Map<String, Object> thresholds) throws Exception {
        File f = new File(profilesDir(plugin), name + ".yml");
        YamlConfiguration y = new YamlConfiguration();
        y.createSection("thresholds", thresholds);
        y.save(f);
    }

    public static Map<String, Object> load(JavaPlugin plugin, String name) throws Exception {
        File f = new File(profilesDir(plugin), name + ".yml");
        if (!f.exists()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection s = y.getConfigurationSection("thresholds");
        if (s == null) return Collections.emptyMap();
        Map<String, Object> m = new HashMap<>();
        for (String k : s.getKeys(false)) {
            m.put(k, s.get(k));
        }
        return m;
    }
}
