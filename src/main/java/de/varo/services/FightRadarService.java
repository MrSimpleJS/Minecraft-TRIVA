package de.varo.services;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * GM Fight Radar: actionbar pulse pointing out fresh PvP with distance and team.
 * Toggle per GM via /fightradar.
 */
public class FightRadarService {
    private final Map<UUID, Long> lastPvP;
    private final Map<UUID, String> playerTeam;
    private final Set<UUID> gameMasters;
    private final Set<UUID> enabled = new HashSet<>();

    private final int lookbackSeconds;
    private final int pulseEverySeconds;
    private final int maxDistance;
    private final boolean showTeamName;

    public FightRadarService(org.bukkit.plugin.java.JavaPlugin plugin,
                             Map<UUID, Long> lastPvP,
                             Map<String, List<UUID>> teams,
                             Map<UUID, String> playerTeam,
                             Set<UUID> gameMasters) {
        this.lastPvP = lastPvP; this.playerTeam = playerTeam; this.gameMasters = gameMasters;
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        this.lookbackSeconds = Math.max(5, cfg.getInt("fightradar.lookbackSeconds", 15));
        this.pulseEverySeconds = Math.max(2, cfg.getInt("fightradar.pulseEverySeconds", 3));
        this.maxDistance = Math.max(100, cfg.getInt("fightradar.maxDistance", 600));
        this.showTeamName = cfg.getBoolean("fightradar.showTeamName", true);

        new BukkitRunnable(){
            @Override public void run(){ tick(); }
        }.runTaskTimer(plugin, 40L, pulseEverySeconds * 20L);
    }

    public void toggle(Player p) {
        UUID id = p.getUniqueId();
        if (!gameMasters.contains(id) && !p.isOp()) { p.sendMessage(ChatColor.RED + "Nur GMs/OP."); return; }
        if (enabled.contains(id)) { enabled.remove(id); p.sendMessage(ChatColor.GRAY + "Fight-Radar aus."); return; }
        enabled.add(id);
        p.sendMessage(ChatColor.GREEN + "Fight-Radar an.");
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long lookbackMs = lookbackSeconds * 1000L;
        // Build candidate list once
        List<Player> fighters = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.getGameMode() == GameMode.SPECTATOR || pl.isDead()) continue;
            Long t = lastPvP.get(pl.getUniqueId());
            if (t != null && now - t <= lookbackMs) fighters.add(pl);
        }
        if (fighters.isEmpty()) return;
        for (UUID viewerId : new ArrayList<>(enabled)) {
            Player gm = Bukkit.getPlayer(viewerId);
            if (gm == null || !gm.isOnline() || (!gameMasters.contains(viewerId) && !gm.isOp())) { enabled.remove(viewerId); continue; }
            // Find nearest fighter in same world
            Player nearest = null; double bestDist2 = Double.MAX_VALUE;
            for (Player f : fighters) {
                if (!f.getWorld().equals(gm.getWorld())) continue;
                double d2;
                try { d2 = gm.getLocation().distanceSquared(f.getLocation()); } catch (Throwable ignored) { continue; }
                if (d2 < bestDist2) { bestDist2 = d2; nearest = f; }
            }
            if (nearest == null) continue;
            double dist = Math.sqrt(bestDist2);
            if (dist > maxDistance) continue;
            String team = playerTeam.get(nearest.getUniqueId());
            String label = (showTeamName && team != null) ? (ChatColor.YELLOW + team) : (ChatColor.WHITE + nearest.getName());
            String txt = ChatColor.RED + "Fight: " + label + ChatColor.GRAY + " [" + ChatColor.WHITE + (int)Math.round(dist) + "m" + ChatColor.GRAY + "]";
            try { gm.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(txt)); } catch (Throwable ignored) {}
        }
    }
}
