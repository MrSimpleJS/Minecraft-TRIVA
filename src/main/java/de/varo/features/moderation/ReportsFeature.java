package de.varo.features.moderation;

import de.varo.features.anticheat.AntiCheatFeature;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Reports queue with GUI. Stores snapshots on report: position, inventory, simple stats via AntiCheat.
 */
public class ReportsFeature implements Listener {
    public static class Snapshot {
        public final long ts;
        public final UUID accused;
        public final UUID reporter;
        public final String reason;
        public final String world;
        public final double x,y,z; public final float yaw,pitch;
        public final ItemStack[] inv;
        public final AntiCheatFeature.MiningSnapshot mining;
        Snapshot(long ts, UUID accused, UUID reporter, String reason, Location loc, ItemStack[] inv, AntiCheatFeature.MiningSnapshot mining){
            this.ts=ts; this.accused=accused; this.reporter=reporter; this.reason=reason;
            this.world = (loc!=null && loc.getWorld()!=null) ? loc.getWorld().getName() : "world";
            this.x = loc!=null?loc.getX():0; this.y=loc!=null?loc.getY():0; this.z=loc!=null?loc.getZ():0; this.yaw=loc!=null?loc.getYaw():0; this.pitch=loc!=null?loc.getPitch():0;
            this.inv = inv;
            this.mining = mining;
        }
    }

    // no plugin reference needed currently
    private final Deque<Snapshot> queue = new ArrayDeque<>();
    private final AntiCheatFeature antiCheat;
    private static final String GUI_TITLE = ChatColor.DARK_RED + "Reports-Queue";

    public ReportsFeature(JavaPlugin plugin, AntiCheatFeature antiCheat) { this.antiCheat = antiCheat; }

    public void submitReport(Player reporter, String accusedName, String reason) {
        Player accused = Bukkit.getPlayerExact(accusedName);
        if (accused == null) { reporter.sendMessage(ChatColor.RED + "Spieler offline."); return; }
        AntiCheatFeature.MiningSnapshot snap = (antiCheat != null ? antiCheat.snapshotMining(accused.getUniqueId()) : null);
        Snapshot s = new Snapshot(System.currentTimeMillis(), accused.getUniqueId(), reporter.getUniqueId(),
                (reason==null||reason.isEmpty()?"-":reason), accused.getLocation().clone(), accused.getInventory().getContents().clone(), snap);
        queue.addLast(s);
        reporter.sendMessage(ChatColor.GREEN + "Report aufgenommen. Admins prüfen die Queue.");
        // Notify online GMs
        for (Player gm : Bukkit.getOnlinePlayers()) if (gm.isOp()) gm.sendMessage(ChatColor.DARK_RED + "[REPORT] " + ChatColor.WHITE + reporter.getName() + ChatColor.GRAY + " -> " + ChatColor.YELLOW + accused.getName() + ChatColor.GRAY + ": " + ChatColor.WHITE + s.reason);
    }

    public void openQueue(Player gm) {
        if (!gm.isOp()) { gm.sendMessage(ChatColor.RED + "Keine Rechte."); return; }
        List<Snapshot> list = new ArrayList<>(queue);
        int size = ((list.size() + 8) / 9) * 9; size = Math.min(Math.max(9, size), 54);
        Inventory inv = Bukkit.createInventory(null, size, GUI_TITLE);
        int i = 0;
        for (Snapshot s : list) {
            OfflinePlayer acc = Bukkit.getOfflinePlayer(s.accused);
            String nm = acc.getName() != null ? acc.getName() : "Spieler";
            ItemStack it = new ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.ItemMeta im = it.getItemMeta(); if (im != null) {
                im.setDisplayName(ChatColor.YELLOW + nm);
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(ChatColor.GRAY + "Grund: " + ChatColor.WHITE + s.reason);
                lore.add(ChatColor.GRAY + "Zeit: " + ChatColor.WHITE + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(s.ts)));
                lore.add(ChatColor.GRAY + "Pos: " + ChatColor.WHITE + s.world + ChatColor.GRAY + " (" + (int)s.x + "," + (int)s.z + ")");
                if (s.mining != null) lore.add(ChatColor.GRAY + "Dia: " + ChatColor.WHITE + s.mining.diamonds + ChatColor.GRAY + ", Debris: " + ChatColor.WHITE + s.mining.debris);
                im.setLore(lore);
                it.setItemMeta(im);
            }
            inv.setItem(i++, it);
            if (i >= size) break;
        }
        gm.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player gm = (Player) e.getWhoClicked();
        org.bukkit.inventory.InventoryView v = gm.getOpenInventory();
        String title; try { title = v.getTitle(); } catch (Throwable t) { return; }
        if (title == null || !title.equals(GUI_TITLE)) return;
        e.setCancelled(true);
        if (!gm.isOp()) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= v.getTopInventory().getSize()) return;
        // Map slot to snapshot index
        int idx = slot;
        if (idx < 0) return;
        Snapshot[] arr = queue.toArray(new Snapshot[0]);
        if (idx >= arr.length) return;
        Snapshot s = arr[idx];
        // Pop this snapshot from queue
        queue.remove(s);
        gm.closeInventory();
        // Teleport silently near the stored position (y+1)
        Location to = null;
        try { org.bukkit.World w = Bukkit.getWorld(s.world); if (w != null) to = new Location(w, s.x, Math.max(1.0, s.y+1.0), s.z, s.yaw, s.pitch); } catch (Throwable ignored) {}
        if (to != null) { try { gm.teleport(to); } catch (Throwable ignored) {} }
        gm.sendMessage(ChatColor.GOLD + "Report geöffnet: " + ChatColor.YELLOW + Bukkit.getOfflinePlayer(s.accused).getName() + ChatColor.GRAY + " — Grund: " + ChatColor.WHITE + s.reason);
    }
}
