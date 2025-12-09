package de.varo.services;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class AutoCamService {
    private final JavaPlugin plugin;
    private final Set<java.util.UUID> enabled = new HashSet<>();
    private final Map<java.util.UUID, Long> lastPvP;
    private final Set<java.util.UUID> gameMasters;
    private final Map<java.util.UUID, Long> lastSwitch = new HashMap<>();
    private final int switchSeconds;
    private final boolean showHint;

    public AutoCamService(JavaPlugin plugin, Map<java.util.UUID, Long> lastPvP, Set<java.util.UUID> gameMasters) {
        this.plugin = plugin; this.lastPvP = lastPvP; this.gameMasters = gameMasters;
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        this.switchSeconds = Math.max(3, cfg.getInt("spectator.autocam.switchSeconds", 8));
        this.showHint = cfg.getBoolean("spectator.autocam.showHint", true);
        new BukkitRunnable(){
            @Override public void run(){ tick(); }
        }.runTaskTimer(plugin, 40L, 60L);
    }

    public void toggle(Player p) {
        java.util.UUID id = p.getUniqueId();
        if (enabled.contains(id)) { enabled.remove(id); p.sendMessage(org.bukkit.ChatColor.GRAY + "Auto-Cam aus."); return; }
        if (!(p.getGameMode() == GameMode.SPECTATOR || gameMasters.contains(id))) { p.sendMessage(org.bukkit.ChatColor.RED + "Nur Zuschauer/GM."); return; }
        enabled.add(id);
        p.sendMessage(org.bukkit.ChatColor.GREEN + "Auto-Cam an.");
        try { plugin.getLogger().fine("AutoCam enabled for " + p.getName()); } catch (Throwable ignored) {}
    }

    private void tick() {
        long now = System.currentTimeMillis();
        // Find candidates: players with very recent PvP
        java.util.List<Player> candidates = new java.util.ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.getGameMode() == GameMode.SPECTATOR || pl.isDead()) continue;
            Long t = lastPvP.get(pl.getUniqueId());
            if (t != null && now - t <= 25_000L) candidates.add(pl);
        }
        if (candidates.isEmpty()) return;
        candidates.sort((a,b) -> Long.compare(lastPvP.getOrDefault(b.getUniqueId(),0L), lastPvP.getOrDefault(a.getUniqueId(),0L)));
        for (java.util.UUID viewerId : new java.util.ArrayList<>(enabled)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) { enabled.remove(viewerId); continue; }
            if (!(viewer.getGameMode()==GameMode.SPECTATOR || gameMasters.contains(viewerId))) { enabled.remove(viewerId); continue; }
            // Cooldown: avoid switching too fast
            long last = lastSwitch.getOrDefault(viewerId, 0L);
            if (now - last < switchSeconds * 1000L) continue;
            // Cycle target based on hash + round robin
            int idx = Math.floorMod(viewer.getName().hashCode() + (int)((now/(switchSeconds*1000L))%Math.max(1,candidates.size())), candidates.size());
            Player target = candidates.get(idx);
            if (target == null) continue;
            try {
                viewer.setGameMode(GameMode.SPECTATOR);
                viewer.setSpectatorTarget(target);
                lastSwitch.put(viewerId, now);
                if (showHint) {
                    try { viewer.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.ChatColor.AQUA + "Cam: " + net.md_5.bungee.api.ChatColor.WHITE + target.getName())); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }
}
