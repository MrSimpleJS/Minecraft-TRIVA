package de.varo.services;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Encapsulates persistence of plugin state (state.yml).
 */
public class StateService {

    public static class RuntimeState {
        public boolean gameRunning;
        public boolean blitzTriggered;
        public boolean paused;
        public boolean centerSet;
        public long gameStartTime;
        public long nextShrinkAt;
    public int schemaVersion = 1; // state.yml schema version

        public double borderCenterX;
        public double borderCenterZ;
        public double borderCurrentSize;

        public Map<String, List<UUID>> teams = new HashMap<>();
        public Map<String, Boolean> teamReady = new HashMap<>();
        public Map<String, ChatColor> teamColors = new HashMap<>();

        public Set<UUID> streamers = new HashSet<>();
        public Map<UUID, Integer> playerKills = new HashMap<>();
        public Map<UUID, Long> netherUsedMs = new HashMap<>();
    public Set<UUID> rulesAccepted = new HashSet<>();
    }

    private File file;
    private YamlConfiguration yaml;

    public void init(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "state.yml");
        if (!file.exists()) {
            yaml = new YamlConfiguration();
            return;
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    /** Returns the current in-memory YAML after init(); not null. */
    public YamlConfiguration getYaml() {
        if (yaml == null) yaml = new YamlConfiguration();
        return yaml;
    }

    public RuntimeState load(double defaultCenterX, double defaultCenterZ, double defaultBorderSize,
                             Map<UUID, String> playerTeamOut) {
        RuntimeState rs = new RuntimeState();

        if (yaml == null) yaml = new YamlConfiguration();

    rs.schemaVersion = yaml.getInt("schemaVersion", 1);
    rs.gameRunning = yaml.getBoolean("running", false);
        rs.blitzTriggered = yaml.getBoolean("blitz", false);
        rs.paused = yaml.getBoolean("paused", false);
        rs.gameStartTime = yaml.getLong("gameStart", 0L);
        rs.nextShrinkAt = yaml.getLong("nextShrinkAt", 0L);
        rs.centerSet = yaml.getBoolean("centerSet", false);

        // border
        rs.borderCenterX = yaml.getDouble("border.centerX", defaultCenterX);
        rs.borderCenterZ = yaml.getDouble("border.centerZ", defaultCenterZ);
        rs.borderCurrentSize = yaml.getDouble("border.currentSize", defaultBorderSize);

        // teams
        if (yaml.contains("teams")) {
            for (String tname : yaml.getConfigurationSection("teams").getKeys(false)) {
                List<String> ids = yaml.getStringList("teams." + tname + ".members");
                List<UUID> uuids = ids.stream().map(UUID::fromString).collect(Collectors.toList());
                rs.teams.put(tname, new ArrayList<>(uuids));
                for (UUID id : uuids) if (playerTeamOut != null) playerTeamOut.put(id, tname);
                rs.teamReady.put(tname, yaml.getBoolean("teams." + tname + ".ready", false));
                ChatColor col = ChatColor.valueOf(yaml.getString("teams." + tname + ".color", "WHITE"));
                rs.teamColors.put(tname, col);
            }
        }

        for (String s : yaml.getStringList("streamers")) rs.streamers.add(UUID.fromString(s));

        if (yaml.contains("kills")) {
            for (String s : yaml.getConfigurationSection("kills").getKeys(false)) {
                rs.playerKills.put(UUID.fromString(s), yaml.getInt("kills." + s, 0));
            }
        }

        if (yaml.contains("netherUsed")) {
            for (String s : yaml.getConfigurationSection("netherUsed").getKeys(false)) {
                rs.netherUsedMs.put(UUID.fromString(s), yaml.getLong("netherUsed." + s, 0L));
            }
        }

    for (String s : yaml.getStringList("rulesAccepted")) rs.rulesAccepted.add(UUID.fromString(s));

        // Bump schema and perform migrations here if needed in future
        if (rs.schemaVersion < 1) {
            rs.schemaVersion = 1;
        }

        // Adjust nextShrinkAt if already passed
        if (rs.nextShrinkAt > 0 && System.currentTimeMillis() > rs.nextShrinkAt) {
            rs.nextShrinkAt = System.currentTimeMillis() + 5_000L;
        }

        // Also apply to live world so border matches on load
        World world = Bukkit.getWorlds().get(0);
        WorldBorder wb = world.getWorldBorder();
        wb.setCenter(rs.borderCenterX, rs.borderCenterZ);
        wb.setSize(rs.borderCurrentSize);

        return rs;
    }

    public void save(JavaPlugin plugin, RuntimeState rs) {
        if (yaml == null) yaml = new YamlConfiguration();

    yaml.set("schemaVersion", Math.max(1, rs.schemaVersion));
    yaml.set("running", rs.gameRunning);
        yaml.set("blitz", rs.blitzTriggered);
        yaml.set("paused", rs.paused);
        yaml.set("gameStart", rs.gameStartTime);
        yaml.set("nextShrinkAt", rs.nextShrinkAt);
        yaml.set("centerSet", rs.centerSet);

        World world = Bukkit.getWorlds().get(0);
        WorldBorder wb = world.getWorldBorder();
        yaml.set("border.centerX", wb.getCenter().getX());
        yaml.set("border.centerZ", wb.getCenter().getZ());
        yaml.set("border.currentSize", wb.getSize());

        yaml.set("teams", null);
        for (Map.Entry<String, List<UUID>> e : rs.teams.entrySet()) {
            String base = "teams." + e.getKey();
            List<String> members = e.getValue().stream().map(UUID::toString).collect(Collectors.toList());
            yaml.set(base + ".members", members);
            yaml.set(base + ".ready", rs.teamReady.getOrDefault(e.getKey(), false));
            yaml.set(base + ".color", rs.teamColors.getOrDefault(e.getKey(), ChatColor.WHITE).name());
        }

        yaml.set("streamers", rs.streamers.stream().map(UUID::toString).collect(Collectors.toList()));

        yaml.set("kills", null);
        for (Map.Entry<UUID, Integer> e : rs.playerKills.entrySet()) {
            yaml.set("kills." + e.getKey(), e.getValue());
        }

        yaml.set("netherUsed", null);
        for (Map.Entry<UUID, Long> e : rs.netherUsedMs.entrySet()) {
            yaml.set("netherUsed." + e.getKey(), e.getValue());
        }

    yaml.set("rulesAccepted", rs.rulesAccepted.stream().map(UUID::toString).collect(java.util.stream.Collectors.toList()));

        try {
            if (file == null) file = new File(plugin.getDataFolder(), "state.yml");
            yaml.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Konnte state.yml nicht speichern: " + ex.getMessage());
        }
    }

    /**
     * Write a compact legacy section for services that still access state.yml directly.
     * Allows an optional writer to contribute extra sections (e.g., whitelist).
     */
    public void saveLegacy(JavaPlugin plugin, RuntimeState rs, java.util.function.Consumer<YamlConfiguration> extraWriter) {
        try {
            File f = (file != null ? file : new File(plugin.getDataFolder(), "state.yml"));
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            y.set("running", rs.gameRunning);
            y.set("blitz", rs.blitzTriggered);
            y.set("paused", rs.paused);
            y.set("gameStart", rs.gameStartTime);
            y.set("nextShrinkAt", rs.nextShrinkAt);
            y.set("centerSet", rs.centerSet);
            if (extraWriter != null) extraWriter.accept(y);
            y.save(f);
        } catch (Exception ex) {
            plugin.getLogger().warning("Konnte state.yml (legacy) nicht speichern: " + ex.getMessage());
        }
    }
}
