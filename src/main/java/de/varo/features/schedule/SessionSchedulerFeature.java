package de.varo.features.schedule;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Simple session scheduler: configure start/stop timestamps; triggers broadcasts and toggles whitelist.
 * Config file: dataFolder/scheduler.yml
 *   startAt: 2025-09-07 18:00
 *   stopAt:  2025-09-07 21:00
 *   whitelist:
 *     enabledBetween: true
 *     allowList: [Player1, Player2]
 */
public class SessionSchedulerFeature {
    private final JavaPlugin plugin;
    private java.util.concurrent.ScheduledExecutorService exec;
    private volatile long startAtMs = -1, stopAtMs = -1;
    private boolean whitelistBetween = false;
    private final Set<String> allowList = new HashSet<>();
    private File cfgFile; private YamlConfiguration cfg;

    public SessionSchedulerFeature(JavaPlugin plugin) { this.plugin = plugin; }

    public void start() {
        load();
        exec = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "triva-scheduler"); t.setDaemon(true); return t; });
        exec.scheduleAtFixedRate(this::tick, 1, 5, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void stop() {
        if (exec != null) { exec.shutdownNow(); exec = null; }
    }

    private void load() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfgFile = new File(plugin.getDataFolder(), "scheduler.yml");
            if (!cfgFile.exists()) cfgFile.createNewFile();
            cfg = YamlConfiguration.loadConfiguration(cfgFile);
            String start = cfg.getString("startAt", null);
            String stop = cfg.getString("stopAt", null);
            whitelistBetween = cfg.getBoolean("whitelist.enabledBetween", false);
            allowList.clear(); allowList.addAll(cfg.getStringList("whitelist.allowList"));
            startAtMs = parseTs(start);
            stopAtMs = parseTs(stop);
        } catch (Exception ignored) {}
    }

    private long parseTs(String s) {
        if (s == null || s.isEmpty()) return -1;
        try {
            java.text.DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            df.setTimeZone(java.util.TimeZone.getDefault());
            return df.parse(s).getTime();
        } catch (Exception e) { return -1; }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (startAtMs > 0 && now >= startAtMs && now < startAtMs + 10_000L) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.broadcastMessage(ChatColor.GREEN + "Session startet jetzt.");
                if (whitelistBetween) applyWhitelist(true);
            });
        }
        if (stopAtMs > 0 && now >= stopAtMs && now < stopAtMs + 10_000L) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Session endet jetzt.");
                if (whitelistBetween) applyWhitelist(false);
            });
        }
    }

    private void applyWhitelist(boolean enable) {
        try {
            Bukkit.setWhitelist(enable);
            // keep existing whitelist entries as-is
            if (enable) {
                for (String n : allowList) {
                    try { OfflinePlayer op = Bukkit.getOfflinePlayer(n); op.setWhitelisted(true); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }
}
