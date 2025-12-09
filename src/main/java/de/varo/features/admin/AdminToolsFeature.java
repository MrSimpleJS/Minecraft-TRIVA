package de.varo.features.admin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Admin utilities: Vanish toggle, Freeze toggle via provided callbacks, Inspect (inventory view), and silent TP helper.
 */
public class AdminToolsFeature implements Listener {
    private final JavaPlugin plugin;
    private final Set<UUID> vanished = new HashSet<>();
    private final java.util.function.Consumer<Player> freezer;
    private final java.util.function.Consumer<Player> unfreezer;
    private final java.util.function.Predicate<java.util.UUID> isFrozen;

    public AdminToolsFeature(JavaPlugin plugin,
                             java.util.function.Consumer<Player> freezer,
                             java.util.function.Consumer<Player> unfreezer,
                             java.util.function.Predicate<java.util.UUID> isFrozen) {
        this.plugin = plugin;
        this.freezer = freezer;
        this.unfreezer = unfreezer;
        this.isFrozen = isFrozen;
    }

    public boolean isVanished(Player p) { return vanished.contains(p.getUniqueId()); }

    public void toggleVanish(Player p) {
        UUID id = p.getUniqueId();
        if (vanished.contains(id)) {
            vanished.remove(id);
            for (Player o : Bukkit.getOnlinePlayers()) { try { o.showPlayer(plugin, p); } catch (Throwable ignored) {} }
            try { p.setCollidable(true); } catch (Throwable ignored) {}
            try { p.setCanPickupItems(true); } catch (Throwable ignored) {}
            p.sendMessage(ChatColor.YELLOW + "Vanish aus.");
        } else {
            vanished.add(id);
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o.equals(p)) continue;
                // Staff/OP can still see vanished staff
                if (o.isOp()) continue;
                try { o.hidePlayer(plugin, p); } catch (Throwable ignored) {}
            }
            try { p.setCollidable(false); } catch (Throwable ignored) {}
            try { p.setCanPickupItems(false); } catch (Throwable ignored) {}
            p.sendMessage(ChatColor.GREEN + "Vanish an.");
        }
    }

    public void toggleFreeze(Player gm, Player target) {
        if (target == null) { gm.sendMessage(ChatColor.RED + "Spieler offline."); return; }
        boolean frozen = (isFrozen != null && isFrozen.test(target.getUniqueId()));
        if (!frozen) { freezer.accept(target); gm.sendMessage(ChatColor.YELLOW + target.getName() + " gefreezed."); }
        else { unfreezer.accept(target); gm.sendMessage(ChatColor.GREEN + target.getName() + " entfreezed."); }
    }

    public void inspect(Player viewer, Player target) {
        if (target == null) { viewer.sendMessage(ChatColor.RED + "Spieler offline."); return; }
        // Simple inventory view: open a copy of target inventory in a chest GUI
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "Inspect » " + ChatColor.YELLOW + target.getName());
        ItemStack[] contents = target.getInventory().getContents();
        for (int i=0;i<Math.min(contents.length,36);i++) inv.setItem(i, contents[i]);
        inv.setItem(45, target.getInventory().getHelmet());
        inv.setItem(46, target.getInventory().getChestplate());
        inv.setItem(47, target.getInventory().getLeggings());
        inv.setItem(48, target.getInventory().getBoots());
        inv.setItem(49, target.getInventory().getItemInOffHand());
        viewer.openInventory(inv);
        viewer.sendMessage(ChatColor.GRAY + "Sicht nur lesend. Nutze /tpmenu oder Specteam-GUI für Live-Sicht.");
    }

    public void tpSilent(Player p, Location to) {
        if (to == null) { p.sendMessage(ChatColor.RED + "Ziel ungültig."); return; }
        try { p.teleport(to); } catch (Throwable ignored) {}
        p.sendMessage(ChatColor.GRAY + "(Silent-TP)");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (isVanished(p)) {
            // Ensure vanish applied against non-ops
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o.equals(p)) continue;
                if (o.isOp()) continue;
                try { o.hidePlayer(plugin, p); } catch (Throwable ignored) {}
            }
            try { p.setCollidable(false); } catch (Throwable ignored) {}
            try { p.setCanPickupItems(false); } catch (Throwable ignored) {}
            // Hide join message by clearing after
            e.setJoinMessage(null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (isVanished(e.getPlayer())) e.setQuitMessage(null);
    }
}
