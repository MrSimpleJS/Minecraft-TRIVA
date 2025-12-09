package de.varo.util;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public final class WorldPregenUtil {
    private WorldPregenUtil() {}

    public static void pregenAroundBorder(JavaPlugin plugin, Player sender, int radiusChunks) {
        World w = plugin.getServer().getWorlds().get(0);
        WorldBorder wb = w.getWorldBorder();
        int cx = (int) Math.floor(wb.getCenter().getX()) >> 4;
        int cz = (int) Math.floor(wb.getCenter().getZ()) >> 4;

        List<int[]> queue = new ArrayList<>();
        for (int x = cx - radiusChunks; x <= cx + radiusChunks; x++) {
            for (int z = cz - radiusChunks; z <= cz + radiusChunks; z++) {
                queue.add(new int[]{x, z});
            }
        }

        new BukkitRunnable() {
            int idx = 0;

            @Override public void run() {
                int batch = 16; // limit per tick
                for (int i = 0; i < batch && idx < queue.size(); i++, idx++) {
                    int[] c = queue.get(idx);
                    try {
                        w.getChunkAtAsync(c[0], c[1]).thenAccept(ch -> {});
                    } catch (Throwable t) {
                        try { w.getChunkAt(c[0], c[1]); } catch (Throwable ignored) {}
                    }
                }
                if (idx >= queue.size()) {
                    cancel();
                    sender.sendMessage(ChatColor.GREEN + "Pregen fertig: " + queue.size() + " Chunks.");
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
