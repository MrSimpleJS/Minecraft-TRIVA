package de.varo.services;

import de.varo.VaroPlugin;
import de.varo.features.review.ReviewFeature;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class GmUiService implements Listener {
    private final VaroPlugin plugin;
    private final NamespacedKey gmTpMenuKey, gmReviewKey, gmMiningKey, gmReportsKey, specteamSelectorKey;
    private final ReviewFeature reviewFeature;

    public GmUiService(VaroPlugin plugin,
                       NamespacedKey gmTpMenuKey,
                       NamespacedKey gmReviewKey,
                       NamespacedKey gmMiningKey,
                       NamespacedKey gmReportsKey,
                       NamespacedKey specteamSelectorKey,
                       ReviewFeature reviewFeature) {
        this.plugin = plugin;
        this.gmTpMenuKey = gmTpMenuKey;
        this.gmReviewKey = gmReviewKey;
        this.gmMiningKey = gmMiningKey;
        this.gmReportsKey = gmReportsKey;
        this.specteamSelectorKey = specteamSelectorKey;
        this.reviewFeature = reviewFeature;
    }

    public void giveGmToolkit(Player p) {
        try { p.setAllowFlight(true); } catch (Throwable ignored) {}
        p.getInventory().setItem(0, createGmTpMenuItem());
        p.getInventory().setItem(1, createSpecteamSelectorItem());
        p.getInventory().setItem(2, createGmReviewItem());
        p.getInventory().setItem(3, createGmMiningItem());
        p.getInventory().setItem(4, createGmReportsItem());
        p.updateInventory();
    }

    public void removeGmToolkit(Player p) {
        for (int i=0;i<p.getInventory().getSize();i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it == null) continue;
            ItemMeta m = it.getItemMeta(); if (m == null) continue;
            var c = m.getPersistentDataContainer();
            if (c.has(gmTpMenuKey, PersistentDataType.BYTE) || c.has(gmReviewKey, PersistentDataType.BYTE) || c.has(gmMiningKey, PersistentDataType.BYTE) || c.has(gmReportsKey, PersistentDataType.BYTE)) {
                p.getInventory().setItem(i, null);
            }
        }
        p.updateInventory();
    }

    public void stripSpecialItemsIfNotPrivileged(Player p, boolean isGm, boolean allowSelector) {
        boolean changed = false;
        for (int i=0;i<p.getInventory().getSize();i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it == null) continue;
            ItemMeta m = it.getItemMeta(); if (m == null) continue;
            var c = m.getPersistentDataContainer();
            if (!isGm && (c.has(gmTpMenuKey, PersistentDataType.BYTE) || c.has(gmReviewKey, PersistentDataType.BYTE) || c.has(gmMiningKey, PersistentDataType.BYTE) || c.has(gmReportsKey, PersistentDataType.BYTE))) {
                p.getInventory().setItem(i, null); changed = true; continue;
            }
            if (!allowSelector && c.has(specteamSelectorKey, PersistentDataType.BYTE)) { p.getInventory().setItem(i, null); changed = true; }
        }
        if (changed) p.updateInventory();
    }

    public void giveSpecteamSelector(Player p) {
        ItemStack head = createSpecteamSelectorItem();
        ItemStack mh = p.getInventory().getItemInMainHand();
        if (mh == null || mh.getType() == Material.AIR) p.getInventory().setItemInMainHand(head);
        else { int empty = p.getInventory().firstEmpty(); if (empty != -1) p.getInventory().setItem(empty, head); else p.getInventory().setItem(8, head); }
        p.updateInventory();
    }

    private ItemStack createSpecteamSelectorItem() {
        ItemStack comp = new ItemStack(Material.COMPASS);
        ItemMeta meta = comp.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Specteam-Kompass");
            List<String> lore = new ArrayList<>(); lore.add(ChatColor.GRAY + "Rechtsklick: Teams/Spieler wählen");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(specteamSelectorKey, PersistentDataType.BYTE, (byte)1);
            comp.setItemMeta(meta);
        }
        return comp;
    }

    private ItemStack createGmTpMenuItem() {
        ItemStack it = new ItemStack(Material.COMPASS);
        ItemMeta m = it.getItemMeta(); if (m != null) { m.setDisplayName(ChatColor.GOLD + "GM: TP-Menü"); m.getPersistentDataContainer().set(gmTpMenuKey, PersistentDataType.BYTE, (byte)1); it.setItemMeta(m);} return it;
    }
    private ItemStack createGmReviewItem() {
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta m = it.getItemMeta(); if (m != null) { m.setDisplayName(ChatColor.AQUA + "GM: Review"); m.getPersistentDataContainer().set(gmReviewKey, PersistentDataType.BYTE, (byte)1); it.setItemMeta(m);} return it;
    }
    private ItemStack createGmMiningItem() {
        ItemStack it = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta m = it.getItemMeta(); if (m != null) { m.setDisplayName(ChatColor.YELLOW + "GM: Mining-Stats"); m.getPersistentDataContainer().set(gmMiningKey, PersistentDataType.BYTE, (byte)1); it.setItemMeta(m);} return it;
    }
    private ItemStack createGmReportsItem() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta(); if (m != null) { m.setDisplayName(ChatColor.GREEN + "GM: Reports-Queue"); m.getPersistentDataContainer().set(gmReportsKey, PersistentDataType.BYTE, (byte)1); it.setItemMeta(m);} return it;
    }

    private static final String GM_SELECT_TITLE_PREFIX = ChatColor.DARK_AQUA + "GM Auswahl » ";

    public void openGmPlayerSelect(Player gm, String mode) {
        java.util.List<Player> list = new java.util.ArrayList<>(Bukkit.getOnlinePlayers());
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 54, GM_SELECT_TITLE_PREFIX + mode);
        int idx = 0;
        for (Player pl : list) {
            if (pl.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta im = head.getItemMeta(); if (im != null) { im.setDisplayName(ChatColor.WHITE + pl.getName()); head.setItemMeta(im);} inv.setItem(idx++, head);
            if (idx >= inv.getSize()) break;
        }
        gm.openInventory(inv);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGmSelectClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player gm = (Player) e.getWhoClicked();
        String title; try { title = gm.getOpenInventory().getTitle(); } catch (Throwable t) { return; }
        if (title == null || !title.startsWith(GM_SELECT_TITLE_PREFIX)) return;
        e.setCancelled(true);
        String mode = ChatColor.stripColor(title.substring(GM_SELECT_TITLE_PREFIX.length()));
        ItemStack it = e.getCurrentItem(); if (it == null) return;
        ItemMeta im = it.getItemMeta(); if (im == null || im.getDisplayName()==null) return;
        String name = ChatColor.stripColor(im.getDisplayName());
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) { gm.closeInventory(); return; }
        if ("Review".equalsIgnoreCase(mode)) {
            if (reviewFeature != null) reviewFeature.openReviewGui(gm, target);
        } else if ("Mining".equalsIgnoreCase(mode)) {
            plugin.cmdMiningStats(gm, new String[]{ target.getName() });
        }
        gm.closeInventory();
    }

    @EventHandler(ignoreCancelled = true)
    public void onGmToolInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack sel = p.getInventory().getItemInMainHand();
        if (sel == null) return;
        ItemMeta sm = sel.getItemMeta(); if (sm == null) return;
        var c = sm.getPersistentDataContainer();
        if (c.has(gmTpMenuKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            if (plugin.getGmFeature() != null) plugin.getGmFeature().openTeleportMenu(p);
            return;
        }
        if (c.has(gmReviewKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            openGmPlayerSelect(p, "Review");
            return;
        }
        if (c.has(gmMiningKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            openGmPlayerSelect(p, "Mining");
            return;
        }
        if (c.has(gmReportsKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            if (plugin.getReportsFeature() != null) plugin.getReportsFeature().openQueue(p);
        }
    }
}
