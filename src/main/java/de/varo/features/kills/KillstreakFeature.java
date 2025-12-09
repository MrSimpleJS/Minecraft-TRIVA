package de.varo.features.kills;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KillstreakFeature implements Listener {
    private final Map<UUID,Integer> streaks = new HashMap<>();

    public KillstreakFeature(JavaPlugin plugin) { }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) {
            int s = streaks.getOrDefault(killer.getUniqueId(), 0) + 1;
            streaks.put(killer.getUniqueId(), s);
            if (s >= 2) {
                Bukkit.broadcastMessage(ChatColor.GOLD + killer.getName() + ChatColor.GRAY + " Killstreak: " + ChatColor.YELLOW + s + ChatColor.GRAY + "!");
                // small reward: 1 golden apple for 3+ (keine Speed-Items)
                try {
                    if (s == 3) killer.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
                } catch (Throwable ignored) {}
            }
        }
        // reset victim's streak
        streaks.remove(victim.getUniqueId());
    }
}
