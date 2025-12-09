package de.varo.util;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Builds summary lines and handles export to file for the /summary command.
 */
public final class SummaryUtil {
    private SummaryUtil() {}

    public static List<String> buildSummaryLines(Map<String, List<UUID>> teams,
                                                 Map<UUID, Integer> playerKills,
                                                 Map<UUID, Integer> playerDeaths) {
        Map<String, Integer> teamAlive = new HashMap<>();
        Map<String, Integer> teamKills = new HashMap<>();

        for (Map.Entry<String, List<UUID>> en : teams.entrySet()) {
            String tname = en.getKey();
            int alive = 0;
            for (UUID id : en.getValue()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                boolean isAlive = true;
                if (op.isOnline() && op.getPlayer() != null) {
                    isAlive = (op.getPlayer().getGameMode() != GameMode.SPECTATOR && !op.getPlayer().isDead());
                } else {
                    try {
                        isAlive = !Bukkit.getBanList(BanList.Type.NAME).isBanned(op.getName());
                    } catch (Throwable ignored) {}
                }
                if (isAlive) alive++;
                teamKills.merge(tname, playerKills.getOrDefault(id, 0), Integer::sum);
            }
            teamAlive.put(tname, alive);
        }

        List<String> teamOrder = new ArrayList<>(teams.keySet());
        teamOrder.sort((a, b) -> {
            int cmpA = teamAlive.getOrDefault(a, 0);
            int cmpB = teamAlive.getOrDefault(b, 0);
            if (cmpA != cmpB) return Integer.compare(cmpB, cmpA);
            int kA = teamKills.getOrDefault(a, 0);
            int kB = teamKills.getOrDefault(b, 0);
            if (kA != kB) return Integer.compare(kB, kA);
            return a.compareToIgnoreCase(b);
        });

        // Top killer (player)
        UUID topId = null; int maxKills = 0;
        for (Map.Entry<UUID, Integer> en : playerKills.entrySet()) {
            if (en.getValue() > maxKills) { maxKills = en.getValue(); topId = en.getKey(); }
        }
        String topKillerName = (topId != null ? Objects.toString(Bukkit.getOfflinePlayer(topId).getName(), "Spieler") : "-");

        List<String> lines = new ArrayList<>();
    lines.add("=== TRIVA SUMMARY ===");
        lines.add("Teams: " + teams.size());
        lines.add("Top-Killer: " + topKillerName + " (" + maxKills + ")");
        lines.add("");
        lines.add("Platzierungen:");
        int rank = 1;
        for (String t : teamOrder) {
            int alive = teamAlive.getOrDefault(t, 0);
            int tk = teamKills.getOrDefault(t, 0);
            lines.add(String.format(Locale.ROOT, "%d) %s  —  alive:%d  kills:%d", rank++, t, alive, tk));
        }
        lines.add("");
        lines.add("Spieler K/D:");
        List<UUID> allPlayers = new ArrayList<>();
        for (List<UUID> l : teams.values()) allPlayers.addAll(l);
        allPlayers.sort((a, b) -> Integer.compare(playerKills.getOrDefault(b, 0), playerKills.getOrDefault(a, 0)));
        for (UUID id : allPlayers) {
            String name = Objects.toString(Bukkit.getOfflinePlayer(id).getName(), id.toString());
            int k = playerKills.getOrDefault(id, 0);
            int d = playerDeaths.getOrDefault(id, 0);
            String kd = (d == 0
                    ? String.format(Locale.ROOT, "%.2f", (double) k)
                    : String.format(Locale.ROOT, "%.2f", (double) k / Math.max(1, d)));
            lines.add(String.format(Locale.ROOT, "- %s: Kills=%d, Deaths=%d, K/D=%s", name, k, d, kd));
        }

        return lines;
    }

    public static File writeSummary(JavaPlugin plugin, List<String> lines, String desiredFileName) throws Exception {
        String fname = (desiredFileName != null && !desiredFileName.isEmpty())
                ? desiredFileName
                : (new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + "_summary.txt");
        File outDir = new File(plugin.getDataFolder(), "summaries");
        if (!outDir.exists()) outDir.mkdirs();
        File out = new File(outDir, fname);
        try (PrintWriter pw = new PrintWriter(out, StandardCharsets.UTF_8.name())) {
            for (String l : lines) pw.println(l);
        }
        return out;
    }
}
