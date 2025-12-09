package de.varo.features.tracker;

import de.varo.services.TrackerService;
import de.varo.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

public class TrackerFeature implements Listener {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final TrackerService trackerService;

    public TrackerFeature(org.bukkit.plugin.java.JavaPlugin plugin, TrackerService trackerService) {
        this.plugin = plugin;
        this.trackerService = trackerService;
        registerTrackerRecipe();
    }

    @SuppressWarnings("deprecation")
    private void registerTrackerRecipe() {
        ItemStack tracker = new ItemStack(Material.COMPASS);
        ItemMeta im = tracker.getItemMeta();
        im.setDisplayName(ChatColor.AQUA + "Spieler-Tracker (inaktiv)");
        im.setLore(java.util.Arrays.asList(
                ChatColor.GRAY + "Rechtsklick mit 9 Smaragden im Inventar,",
                ChatColor.GRAY + "um zu aktivieren."
        ));
        tracker.setItemMeta(im);

        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "player_tracker_recipe"), tracker);
        recipe.shape(" E ", "ECE", " E ");
        recipe.setIngredient('E', Material.ENDER_EYE);
        recipe.setIngredient('C', Material.COMPASS);
        Bukkit.addRecipe(recipe);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it == null || it.getType() != Material.COMPASS) return;

        if (!trackerService.isTracker(it)) {
            int have = ItemUtils.countItems(p, Material.EMERALD);
            if (have >= 9) {
                ItemUtils.removeItems(p, Material.EMERALD, 9);
                trackerService.activateTrackerItem(it);
                p.updateInventory();
                p.sendMessage(ChatColor.GREEN + "Dein Spieler-Tracker wurde aktiviert. (-9 Smaragde)");
            } else {
                int missing = 9 - have;
                p.sendMessage(ChatColor.RED + "Dir fehlen noch " + missing + " Smaragde für den Spieler-Tracker.");
            }
            return;
        }
        Location target = findNearestEnemy(p);
        trackerService.onUseActiveTracker(p, target);
    }

    private Location findNearestEnemy(Player p) {
        Player best = null; double bestD = Double.MAX_VALUE;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o == p) continue;
            if (!o.getWorld().equals(p.getWorld())) continue;
            if (o.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (o.isOp()) continue; // avoid ops/admins
            double d = o.getLocation().distanceSquared(p.getLocation());
            if (d < bestD) { bestD = d; best = o; }
        }
        return best != null ? best.getLocation() : null;
    }
}
