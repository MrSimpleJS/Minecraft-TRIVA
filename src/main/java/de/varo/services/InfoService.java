package de.varo.services;

import de.varo.util.Lang;
import de.varo.util.Messages;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

public class InfoService {
    private final Map<String, java.util.List<java.util.UUID>> teams;
    @SuppressWarnings("unused")
    private final Map<java.util.UUID, String> playerTeam;
    @SuppressWarnings("unused")
    private final Map<String, org.bukkit.ChatColor> teamColors;
    private final Set<java.util.UUID> streamers;
    private final Map<java.util.UUID, Integer> playerKills;
    private final java.util.function.Supplier<Boolean> isGameRunning;
    private final java.util.function.Supplier<Long> getProjectRemainingMs;
    private final java.util.function.Supplier<Boolean> privacyMaskCoords;

    public InfoService(Map<String, java.util.List<java.util.UUID>> teams,
                       Map<java.util.UUID, String> playerTeam,
                       Map<String, org.bukkit.ChatColor> teamColors,
                       Set<java.util.UUID> streamers,
                       Map<java.util.UUID, Integer> playerKills,
                       java.util.function.Supplier<Boolean> isGameRunning,
                       java.util.function.Supplier<Long> getProjectRemainingMs,
                       java.util.function.Supplier<Boolean> privacyMaskCoords) {
        this.teams = teams; this.playerTeam = playerTeam; this.teamColors = teamColors; this.streamers = streamers; this.playerKills = playerKills;
        this.isGameRunning = isGameRunning; this.getProjectRemainingMs = getProjectRemainingMs; this.privacyMaskCoords = privacyMaskCoords;
    }

    public void cmdWho(Player sender) {
        StringBuilder sb = new StringBuilder(ChatColor.GOLD + Lang.tr("who.header") + "\n");
        for (Map.Entry<String, java.util.List<java.util.UUID>> e : teams.entrySet()) {
            String t = e.getKey(); sb.append(ChatColor.YELLOW).append("  ").append(t).append(ChatColor.GRAY).append(": ");
            java.util.List<String> names = new java.util.ArrayList<>();
            for (java.util.UUID id : e.getValue()) {
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                boolean online = op.isOnline(); boolean active = false;
                if (online && op.getPlayer()!=null) active = (op.getPlayer().getGameMode()!=GameMode.SPECTATOR);
                names.add((online ? (active ? ChatColor.GREEN : ChatColor.GRAY) : ChatColor.DARK_GRAY) + (op.getName() == null ? "Spieler" : op.getName()));
            }
            sb.append(String.join(ChatColor.GRAY + ", ", names)).append("\n");
        }
        sender.sendMessage(sb.toString());
    }

    public void cmdVaroInfo(Player p) {
        WorldBorder wb = p.getWorld().getWorldBorder();
        boolean mask = privacyMaskCoords.get() && streamers.contains(p.getUniqueId());
        String centerStr = mask ? (ChatColor.DARK_GRAY + "X:??? Z:???")
                : (ChatColor.WHITE + "" + (int)wb.getCenter().getX() + ChatColor.GRAY + "," + ChatColor.WHITE + (int)wb.getCenter().getZ());
        p.sendMessage(Messages.title("title.varoInfo"));
        p.sendMessage(ChatColor.YELLOW + Lang.tr("info.border") + ": " + ChatColor.WHITE + (int)wb.getSize() + " " + Lang.tr("info.blocks") + ChatColor.GRAY + "  " + Lang.tr("info.center") + ": " + centerStr);
        p.sendMessage(ChatColor.YELLOW + Lang.tr("info.projectTime") + ": " + ChatColor.WHITE + (isGameRunning.get() ? formatTime(getProjectRemainingMs.get()) : Lang.tr("info.waiting")));
        p.sendMessage(ChatColor.YELLOW + Lang.tr("info.teams") + ": " + ChatColor.WHITE + teams.size());
        if (!playerKills.isEmpty()) {
            java.util.UUID topId = null; int max = 0;
            for (Map.Entry<java.util.UUID,Integer> en : playerKills.entrySet()) if (en.getValue() > max) { max=en.getValue(); topId=en.getKey(); }
            String nm = topId!=null ? java.util.Objects.toString(Bukkit.getOfflinePlayer(topId).getName(),"Spieler") : "-";
            p.sendMessage(ChatColor.YELLOW + Lang.tr("info.topKiller") + ": " + ChatColor.GOLD + nm + ChatColor.GRAY + " ("+max+")");
        }
    }

    private String formatTime(long ms) {
        long totalSec = Math.max(0L, ms) / 1000L;
        long h = totalSec / 3600L, m = (totalSec % 3600L) / 60L, s = totalSec % 60L;
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        return String.format("%02dm %02ds", m, s);
    }
}
