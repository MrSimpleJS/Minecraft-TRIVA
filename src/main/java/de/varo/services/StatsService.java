package de.varo.services;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class StatsService {
    private final de.varo.VaroPlugin plugin;
    private final Map<String, List<UUID>> teams;
    private final Map<UUID, Integer> playerKills;
    private final Map<UUID, Integer> playerDeaths;

    public StatsService(de.varo.VaroPlugin plugin,
                        Map<String, List<UUID>> teams,
                        Map<UUID, Integer> playerKills,
                        Map<UUID, Integer> playerDeaths) {
        this.plugin = plugin; this.teams = teams; this.playerKills = playerKills; this.playerDeaths = playerDeaths;
    }

    public void cmdSummary(Player sender, String[] args) {
        Map<String, Integer> teamAlive = new HashMap<>();
        Map<String, Integer> teamKills = new HashMap<>();
        for (Map.Entry<String, List<UUID>> en : teams.entrySet()) {
            String tname = en.getKey(); int alive = 0;
            for (UUID id : en.getValue()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                boolean isAlive = true;
                if (op.isOnline() && op.getPlayer()!=null) isAlive = (op.getPlayer().getGameMode()!=GameMode.SPECTATOR && !op.getPlayer().isDead());
                else { try { isAlive = !Bukkit.getBanList(org.bukkit.BanList.Type.NAME).isBanned(op.getName()); } catch (Throwable ignored) {} }
                if (isAlive) alive++;
                teamKills.merge(tname, playerKills.getOrDefault(id, 0), Integer::sum);
            }
            teamAlive.put(tname, alive);
        }
        List<String> teamOrder = new ArrayList<>(teams.keySet());
        teamOrder.sort((a,b) -> {
            int cmpA = teamAlive.getOrDefault(a,0), cmpB = teamAlive.getOrDefault(b,0);
            if (cmpA != cmpB) return Integer.compare(cmpB, cmpA);
            int kA = teamKills.getOrDefault(a,0), kB = teamKills.getOrDefault(b,0);
            if (kA != kB) return Integer.compare(kB, kA);
            return a.compareToIgnoreCase(b);
        });
        UUID topId = null; int maxKills = 0;
        for (Map.Entry<UUID,Integer> en : playerKills.entrySet()) if (en.getValue() > maxKills) { maxKills = en.getValue(); topId = en.getKey(); }
        String topKillerName = (topId != null ? Objects.toString(Bukkit.getOfflinePlayer(topId).getName(), "Spieler") : "-");
        List<String> lines = new ArrayList<>();
    lines.add("=== TRIVA SUMMARY ===");
        lines.add("Teams: " + teams.size());
        lines.add("Top-Killer: " + topKillerName + " (" + maxKills + ")");
        lines.add(""); lines.add("Platzierungen:");
        int rank = 1; for (String t : teamOrder) { lines.add(String.format(Locale.ROOT, "%d) %s  —  alive:%d  kills:%d", rank++, t, teamAlive.getOrDefault(t,0), teamKills.getOrDefault(t,0))); }
        lines.add(""); lines.add("Spieler K/D:");
        List<UUID> allPlayers = new ArrayList<>(); for (List<UUID> l : teams.values()) allPlayers.addAll(l);
        allPlayers.sort((a,b) -> Integer.compare(playerKills.getOrDefault(b,0), playerKills.getOrDefault(a,0)));
        for (UUID id : allPlayers) {
            String name = Objects.toString(Bukkit.getOfflinePlayer(id).getName(), id.toString());
            int k = playerKills.getOrDefault(id, 0); int d = playerDeaths.getOrDefault(id, 0);
            String kd = (d == 0 ? String.format(Locale.ROOT, "%.2f", (double)k) : String.format(Locale.ROOT, "%.2f", (double)k / Math.max(1,d)));
            lines.add(String.format(Locale.ROOT, "- %s: Kills=%d, Deaths=%d, K/D=%s", name, k, d, kd));
        }
    sender.sendMessage(ChatColor.GOLD + "—— TRIVA SUMMARY ——");
        int shown = 0; for (String l : lines) { sender.sendMessage(ChatColor.GRAY + l); if (++shown >= 15) break; }
        if (lines.size() > shown) sender.sendMessage(ChatColor.DARK_GRAY + "+" + (lines.size()-shown) + " weitere Zeilen in Datei");
        String fname = (args != null && args.length >= 1 && !args[0].isEmpty()) ? args[0] : (new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + "_summary.txt");
        File outDir = new File(plugin.getDataFolder(), "summaries"); if (!outDir.exists()) outDir.mkdirs();
        File out = new File(outDir, fname);
        try (PrintWriter pw = new PrintWriter(out, StandardCharsets.UTF_8.name())) { for (String l : lines) pw.println(l); }
        catch (Exception ex) { sender.sendMessage(ChatColor.RED + "Export fehlgeschlagen: " + ex.getMessage()); return; }
        sender.sendMessage(ChatColor.GREEN + "Summary exportiert: " + ChatColor.WHITE + out.getName());
    }
}
