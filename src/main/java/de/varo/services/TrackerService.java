package de.varo.services;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

public class TrackerService {
    private final Map<UUID, Long> trackerCooldown;
    private final long trackerCooldownMs;
    private final NamespacedKey trackerKey;

    public TrackerService(Map<UUID, Long> trackerCooldown, long trackerCooldownMs, NamespacedKey trackerKey) {
        this.trackerCooldown = trackerCooldown;
        this.trackerCooldownMs = trackerCooldownMs;
        this.trackerKey = trackerKey;
    }

    // Provide inactive tracker
    @SuppressWarnings("deprecation")
    public void giveInactiveTracker(Player p) {
        ItemStack tracker = new ItemStack(Material.COMPASS);
        ItemMeta im = tracker.getItemMeta();
        im.setDisplayName(ChatColor.AQUA + "Spieler-Tracker (inaktiv)");
        im.setLore(Arrays.asList(
                ChatColor.GRAY + "Rechtsklick mit 9 Smaragden im Inventar,",
                ChatColor.GRAY + "um zu aktivieren."
        ));
        tracker.setItemMeta(im);
        p.getInventory().addItem(tracker);
        p.sendMessage(ChatColor.GREEN + "Du hast einen (inaktiven) Spieler-Tracker erhalten.");
    }

    // Activate tracker item (adds PDC flag and enchant)
    @SuppressWarnings("deprecation")
    public void activateTrackerItem(ItemStack it) {
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(ChatColor.AQUA + "Spieler-Tracker");
    im.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        im.getPersistentDataContainer().set(trackerKey, PersistentDataType.BYTE, (byte)1);
        im.setLore(null);
        it.setItemMeta(im);
    }

    // Whether item is an active tracker
    public boolean isTracker(ItemStack it) {
        if (it == null || it.getType() != Material.COMPASS || !it.hasItemMeta()) return false;
        Byte tag = it.getItemMeta().getPersistentDataContainer().get(trackerKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte)1;
    }

    public boolean onUseActiveTracker(Player p, Location target) {
        long now = System.currentTimeMillis();
        long cd = trackerCooldown.getOrDefault(p.getUniqueId(), 0L);
        if (now < cd) {
            long s = (cd - now + 999) / 1000;
            p.sendMessage(ChatColor.RED + "Tracker-Cooldown: " + s + "s");
            return false;
        }
        if (target == null) {
            p.sendMessage(ChatColor.GRAY + "Kein Gegner in deiner Welt.");
            return false;
        }
        // One-time ping: set compass briefly, play a sound and spawn a short particle pulse at target for this player
        p.setCompassTarget(target);
        double dist = 0.0;
        try { dist = p.getLocation().distance(target); } catch (Throwable ignored) {}
        p.sendMessage(ChatColor.AQUA + "Ping bei " + ChatColor.YELLOW + target.getBlockX() + "," + target.getBlockZ()
                + ChatColor.AQUA + " (~" + (int)dist + "m)" + ChatColor.GRAY + " — kein Live-Tracking");
        try { p.playSound(p.getLocation(), org.bukkit.Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1f, 1.2f); } catch (Throwable ignored) {}
        try {
            org.bukkit.World w = target.getWorld();
            org.bukkit.Location at = target.clone().add(0.5, 1.0, 0.5);
            for (double dy = 0; dy <= 4; dy += 0.5) {
                w.spawnParticle(org.bukkit.Particle.END_ROD, at.clone().add(0, dy, 0), 6, 0.2, 0.2, 0.2, 0.0);
            }
        } catch (Throwable ignored) {}
        trackerCooldown.put(p.getUniqueId(), now + trackerCooldownMs);
        return true;
    }
}
