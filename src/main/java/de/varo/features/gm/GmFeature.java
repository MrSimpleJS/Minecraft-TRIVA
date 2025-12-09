package de.varo.features.gm;

import de.varo.gui.GuiHelper;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Set;
import java.util.UUID;

public class GmFeature implements Listener {
    private final Set<UUID> gameMasters;

    public GmFeature(Set<UUID> gameMasters) { this.gameMasters = gameMasters; }

    public void openTeleportMenu(Player p) {
        if (!gameMasters.contains(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return; }
        p.openInventory(GuiHelper.createGmTeleportMenu());
    }

    public void openActions(Player gm, String targetName) { gm.openInventory(GuiHelper.createGmActions(targetName)); }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player gm = (Player) e.getWhoClicked();

        // GM Teleport: Spielerköpfe
        if (e.getView().getTitle().equals(GuiHelper.INV_GM_TP)) {
            if (!gameMasters.contains(gm.getUniqueId())) { e.setCancelled(true); return; }
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item == null || item.getType() != org.bukkit.Material.PLAYER_HEAD) return;
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta == null || meta.getOwningPlayer() == null) return;
            openActions(gm, meta.getOwningPlayer().getName());
            return;
        }

        // GM Spieler-Aktionen
        if (e.getView().getTitle().startsWith(GuiHelper.INV_GM_PLAYER_ACTIONS_PREFIX)) {
            if (!gameMasters.contains(gm.getUniqueId())) { e.setCancelled(true); return; }
            e.setCancelled(true);
            String targetName = ChatColor.stripColor(e.getView().getTitle().substring(GuiHelper.INV_GM_PLAYER_ACTIONS_PREFIX.length()));
            Player target = org.bukkit.Bukkit.getPlayerExact(targetName);
            if (target == null) { gm.closeInventory(); return; }
            ItemStack item = e.getCurrentItem(); if (item == null) return;
            switch (item.getType()) {
                case LIME_DYE:
                    gm.teleport(target.getLocation());
                    gm.sendMessage(ChatColor.GREEN + "Zu " + target.getName() + " teleportiert.");
                    break;
                case ENDER_PEARL:
                    target.teleport(gm.getLocation());
                    gm.sendMessage(ChatColor.GREEN + target.getName() + " wurde zu dir teleportiert.");
                    break;
                case SPECTRAL_ARROW:
                    gm.setGameMode(GameMode.SPECTATOR);
                    gm.setSpectatorTarget(target);
                    gm.sendMessage(ChatColor.YELLOW + "Du siehst jetzt aus der Sicht von " + target.getName());
                    break;
                default: break;
            }
            gm.closeInventory();
        }
    }
}
