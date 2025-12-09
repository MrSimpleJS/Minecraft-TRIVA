package de.varo.services;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NetherService implements Listener {
    private final java.util.function.Supplier<Long> netherLimitMs;
    private final Map<UUID, Long> netherUsedMs;
    private final Map<UUID, Long> netherSessionStart;
    private final Set<UUID> warned30, warned10, warned5, warned1, netherOvertime;

    public NetherService(java.util.function.Supplier<Long> netherLimitMs,
                         Map<UUID, Long> netherUsedMs,
                         Map<UUID, Long> netherSessionStart,
                         Set<UUID> warned30,
                         Set<UUID> warned10,
                         Set<UUID> warned5,
                         Set<UUID> warned1,
                         Set<UUID> netherOvertime) {
        this.netherLimitMs = netherLimitMs;
        this.netherUsedMs = netherUsedMs;
        this.netherSessionStart = netherSessionStart;
        this.warned30 = warned30; this.warned10 = warned10; this.warned5 = warned5; this.warned1 = warned1; this.netherOvertime = netherOvertime;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (p.getWorld().getEnvironment() == World.Environment.NETHER) {
            netherSessionStart.put(p.getUniqueId(), System.currentTimeMillis());
            warned30.remove(p.getUniqueId()); warned10.remove(p.getUniqueId()); warned5.remove(p.getUniqueId()); warned1.remove(p.getUniqueId());
        } else {
            if (netherSessionStart.containsKey(p.getUniqueId())) {
                long start = netherSessionStart.remove(p.getUniqueId());
                netherUsedMs.put(p.getUniqueId(), netherUsedMs.getOrDefault(p.getUniqueId(), 0L) + (System.currentTimeMillis() - start));
            }
            netherOvertime.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (netherSessionStart.containsKey(p.getUniqueId())) {
            long start = netherSessionStart.remove(p.getUniqueId());
            netherUsedMs.put(p.getUniqueId(), netherUsedMs.getOrDefault(p.getUniqueId(), 0L) + (System.currentTimeMillis() - start));
        }
        warned30.remove(p.getUniqueId()); warned10.remove(p.getUniqueId()); warned5.remove(p.getUniqueId()); warned1.remove(p.getUniqueId());
        netherOvertime.remove(p.getUniqueId());
    }

    public void resetNetherTime(Player gm, Player target, Long setUsedMs) {
        long used = (setUsedMs != null ? setUsedMs : 0L);
        if (netherSessionStart.containsKey(target.getUniqueId())) {
            long start = netherSessionStart.remove(target.getUniqueId());
            netherUsedMs.put(target.getUniqueId(), netherUsedMs.getOrDefault(target.getUniqueId(), 0L) + (System.currentTimeMillis() - start));
        }
        netherUsedMs.put(target.getUniqueId(), used);
        gm.sendMessage(ChatColor.GREEN + "Nether-Zeit von " + target.getName() + " gesetzt. Verbleibend: " + formatTime(netherLimitMs.get() - used));
        target.sendMessage(ChatColor.YELLOW + "Deine Nether-Zeit wurde angepasst.");
        warned30.remove(target.getUniqueId()); warned10.remove(target.getUniqueId()); warned5.remove(target.getUniqueId()); warned1.remove(target.getUniqueId());
        netherOvertime.remove(target.getUniqueId());
    }

    private String formatTime(long ms) {
        long totalSec = Math.max(0L, ms) / 1000L;
        long h = totalSec / 3600L, m = (totalSec % 3600L) / 60L, s = totalSec % 60L;
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        return String.format("%02dm %02ds", m, s);
    }
}
