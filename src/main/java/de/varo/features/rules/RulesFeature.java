package de.varo.features.rules;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;

public class RulesFeature implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (e.getRecipe() == null) return;
        org.bukkit.inventory.ItemStack res = e.getRecipe().getResult();
        if (res != null && res.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player) {
                ((Player) e.getWhoClicked()).sendMessage(ChatColor.RED + "Verzauberte Goldäpfel sind deaktiviert.");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (e.getItem() != null && e.getItem().getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Verzauberte Goldäpfel sind deaktiviert.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickupEnchantedApple(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (e.getItem() != null && e.getItem().getItemStack() != null
                && e.getItem().getItemStack().getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            e.setCancelled(true);
            ((Player) e.getEntity()).sendMessage(ChatColor.RED + "Verzauberte Goldäpfel sind deaktiviert.");
        }
    }
}
