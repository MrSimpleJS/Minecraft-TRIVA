package de.varo.features.review;

import de.varo.VaroPlugin;
import de.varo.features.anticheat.AntiCheatFeature;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
public class ReviewFeature implements Listener {
    @SuppressWarnings("unused")
    private final VaroPlugin plugin;
    private final AntiCheatFeature antiCheat;
    private final java.util.Set<UUID> gameMasters;

    public ReviewFeature(VaroPlugin plugin, AntiCheatFeature antiCheat, java.util.Set<UUID> gameMasters) {
        this.plugin = plugin;
        this.antiCheat = antiCheat;
        this.gameMasters = gameMasters;
    }

    public void openReviewGui(Player gm, Player target) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "Review » " + ChatColor.YELLOW + target.getName());
        inv.setItem(0, de.varo.util.ItemUtils.named(new ItemStack(Material.COMPASS), ChatColor.GOLD + "TP zu Spieler"));
        inv.setItem(1, de.varo.util.ItemUtils.named(new ItemStack(Material.ENDER_PEARL), ChatColor.GOLD + "Spieler zu mir"));
        inv.setItem(2, de.varo.util.ItemUtils.named(new ItemStack(Material.SPECTRAL_ARROW), ChatColor.GOLD + "Sicht verfolgen"));
    // New quick actions
    inv.setItem(3, de.varo.util.ItemUtils.named(new ItemStack(Material.CHEST), ChatColor.AQUA + "Inventar ansehen"));
    inv.setItem(4, de.varo.util.ItemUtils.named(new ItemStack(Material.ENDER_CHEST), ChatColor.AQUA + "Enderchest ansehen"));
    inv.setItem(5, de.varo.util.ItemUtils.named(new ItemStack(Material.WRITABLE_BOOK), ChatColor.YELLOW + "Spieler-Stats"));
    inv.setItem(7, de.varo.util.ItemUtils.named(new ItemStack(Material.ICE), ChatColor.RED + "Freeze umschalten"));
    inv.setItem(8, de.varo.util.ItemUtils.named(new ItemStack(Material.GLASS), ChatColor.GRAY + "Vanish (du) umschalten"));
    inv.setItem(10, de.varo.util.ItemUtils.named(new ItemStack(Material.PAPER), ChatColor.AQUA + "Clip speichern"));
    inv.setItem(12, de.varo.util.ItemUtils.named(new ItemStack(Material.DIAMOND_PICKAXE), ChatColor.YELLOW + "Mining-Stats"));
    inv.setItem(13, de.varo.util.ItemUtils.named(new ItemStack(Material.IRON_SWORD), ChatColor.YELLOW + "Combat-Info"));
    inv.setItem(14, de.varo.util.ItemUtils.named(new ItemStack(Material.BOOK), ChatColor.RED + "Flags (Seite 1)"));
        if (antiCheat != null) {
            de.varo.features.anticheat.AntiCheatFeature.MiningSnapshot snap = antiCheat.snapshotMining(target.getUniqueId());
            java.util.List<String> lore = new java.util.ArrayList<>();
            if (snap != null) {
                lore.add(ChatColor.GRAY + "Overworld: " + ChatColor.WHITE + snap.stone + ChatColor.GRAY + ", Dia: " + ChatColor.WHITE + snap.diamonds);
                lore.add(ChatColor.GRAY + "Nether: " + ChatColor.WHITE + snap.netherrack + ChatColor.GRAY + ", Debris: " + ChatColor.WHITE + snap.debris);
                lore.add(ChatColor.GRAY + "Ratio Dia: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.2f", snap.diamondPerNorm));
                lore.add(ChatColor.GRAY + "Ratio Debris: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.2f", snap.debrisPerNorm));
                lore.add(ChatColor.GRAY + "Branch→Erz: " + ChatColor.WHITE + snap.branchHits);
            } else lore.add(ChatColor.GRAY + "Keine Mining-Daten.");
            org.bukkit.inventory.meta.ItemMeta im = inv.getItem(12).getItemMeta();
            if (im != null) { im.setLore(lore); inv.getItem(12).setItemMeta(im); }
            java.util.List<de.varo.features.anticheat.AntiCheatFeature.FlagInfo> flags = antiCheat.getRecentFlagInfo(target.getUniqueId(), 45);
            int slot = 27; int idx = 0;
            long now = System.currentTimeMillis();
            for (de.varo.features.anticheat.AntiCheatFeature.FlagInfo f : flags) {
                if (idx >= 18) break;
                long age = Math.max(0L, now - f.ts) / 1000L;
                boolean xray = "XRAY".equalsIgnoreCase(f.category);
                String name = (xray ? ChatColor.GOLD + "[XRAY] " : ChatColor.RED + "") + ChatColor.WHITE + age + "s: " + f.reason;
                inv.setItem(slot++, de.varo.util.ItemUtils.named(new ItemStack(Material.MAP), name));
                idx++;
            }
        }
        gm.openInventory(inv);
    }

    @EventHandler(ignoreCancelled = true)
    public void onReviewClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player gm = (Player) e.getWhoClicked();
        org.bukkit.inventory.InventoryView v = gm.getOpenInventory();
        String title; try { title = v.getTitle(); } catch (Throwable t) { return; }
        if (title == null || !title.startsWith(ChatColor.DARK_RED + "Review » ")) return;
        e.setCancelled(true);
        if (!gameMasters.contains(gm.getUniqueId())) return;
        String targetName = ChatColor.stripColor(title.substring((ChatColor.DARK_RED + "Review » ").length()));
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { gm.closeInventory(); return; }
        ItemStack it = e.getCurrentItem(); if (it == null) return;
        switch (it.getType()) {
            case COMPASS:
                gm.teleport(target.getLocation());
                gm.sendMessage(ChatColor.GREEN + "TP zu " + target.getName());
                break;
            case ENDER_PEARL:
                target.teleport(gm.getLocation());
                gm.sendMessage(ChatColor.GREEN + target.getName() + " zu dir teleportiert");
                break;
            case SPECTRAL_ARROW:
                gm.setGameMode(GameMode.SPECTATOR);
                gm.setSpectatorTarget(target);
                gm.sendMessage(ChatColor.YELLOW + "Sicht: " + target.getName());
                break;
            case CHEST:
                if (plugin.getAdminTools() != null) plugin.getAdminTools().inspect(gm, target);
                else gm.sendMessage(ChatColor.RED + "Inspect nicht verfügbar.");
                break;
            case ENDER_CHEST:
                try { gm.openInventory(target.getEnderChest()); gm.sendMessage(ChatColor.AQUA + "Enderchest von " + target.getName()); }
                catch (Throwable t) { gm.sendMessage(ChatColor.RED + "Enderchest nicht verfügbar."); }
                break;
            case WRITABLE_BOOK:
                // Quick stats in chat
                gm.sendMessage(ChatColor.GOLD + "— Stats: " + ChatColor.YELLOW + target.getName());
                gm.sendMessage(ChatColor.GRAY + "HP: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.1f/%.1f", target.getHealth(), target.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
                gm.sendMessage(ChatColor.GRAY + "Hunger/Sättigung: " + ChatColor.WHITE + target.getFoodLevel() + ChatColor.GRAY + " / " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.1f", target.getSaturation()));
                gm.sendMessage(ChatColor.GRAY + "XP-Level: " + ChatColor.WHITE + target.getLevel());
                org.bukkit.Location l = target.getLocation();
                gm.sendMessage(ChatColor.GRAY + "Pos: " + ChatColor.WHITE + l.getWorld().getName() + ChatColor.GRAY + " @ " + ChatColor.WHITE + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
                java.util.Collection<org.bukkit.potion.PotionEffect> eff = target.getActivePotionEffects();
                if (eff.isEmpty()) gm.sendMessage(ChatColor.GRAY + "Effekte: " + ChatColor.DARK_GRAY + "keine");
                else {
                    StringBuilder sb = new StringBuilder(); boolean first = true;
                    for (org.bukkit.potion.PotionEffect pe : eff) { if (!first) sb.append(ChatColor.GRAY).append(", "); first=false; sb.append(ChatColor.WHITE).append(pe.getType().getKey().getKey()).append(ChatColor.DARK_GRAY).append("(").append(pe.getAmplifier()+1).append(")"); }
                    gm.sendMessage(ChatColor.GRAY + "Effekte: " + sb);
                }
                break;
            case ICE:
                if (plugin.getAdminTools() != null) plugin.getAdminTools().toggleFreeze(gm, target);
                else gm.sendMessage(ChatColor.RED + "Freeze nicht verfügbar.");
                break;
            case GLASS:
                if (plugin.getAdminTools() != null) plugin.getAdminTools().toggleVanish(gm);
                else gm.sendMessage(ChatColor.RED + "Vanish nicht verfügbar.");
                break;
            case PAPER:
                if (antiCheat != null && antiCheat.saveClip(target.getUniqueId())) gm.sendMessage(ChatColor.GREEN + "Clip gespeichert.");
                else gm.sendMessage(ChatColor.RED + "Clip fehlgeschlagen.");
                break;
            default:
                break;
        }
        gm.closeInventory();
    }

    // /review <Spieler> [gui]
    public void cmdReview(Player gm, String[] args, int acWindowMinutes, int acDiamondPerBlocks, int acDebrisPerBlocks, int acBranchWithinSteps) {
        if (args == null || args.length == 0) { gm.sendMessage(ChatColor.YELLOW + "/review <Spieler>"); return; }
        Player target = Bukkit.getPlayerExact(args[0]); if (target == null) { gm.sendMessage(ChatColor.RED + "Spieler offline."); return; }
        if (antiCheat == null) { gm.sendMessage(ChatColor.RED + "AntiCheat nicht aktiv."); return; }
        de.varo.features.anticheat.AntiCheatFeature.MiningSnapshot snap = antiCheat.snapshotMining(target.getUniqueId());
        int vio = antiCheat.getViolations(target.getUniqueId());
        gm.sendMessage(ChatColor.GOLD + "—— Review: " + ChatColor.YELLOW + target.getName() + ChatColor.GOLD + " ——");
        gm.sendMessage(ChatColor.GRAY + "Violations: " + ChatColor.WHITE + vio);
        if (snap != null) {
            gm.sendMessage(ChatColor.AQUA + "Overworld: " + ChatColor.WHITE + snap.stone + ChatColor.GRAY + " Blöcke" + ChatColor.GRAY + ", Diamanten: " + ChatColor.WHITE + snap.diamonds + ChatColor.GRAY + ", Ratio: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.2f/"+acDiamondPerBlocks, snap.diamondPerNorm));
            gm.sendMessage(ChatColor.LIGHT_PURPLE + "Nether: " + ChatColor.WHITE + snap.netherrack + ChatColor.GRAY + " Blöcke" + ChatColor.GRAY + ", Netherit: " + ChatColor.WHITE + snap.debris + ChatColor.GRAY + ", Ratio: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.2f/"+acDebrisPerBlocks, snap.debrisPerNorm));
            gm.sendMessage(ChatColor.YELLOW + "Branch→Erz (≤" + acBranchWithinSteps + "): " + ChatColor.WHITE + snap.branchHits);
        }
        java.util.List<String> flags = antiCheat.getRecentFlags(target.getUniqueId(), 10);
        if (flags.isEmpty()) gm.sendMessage(ChatColor.GRAY + "Keine Flags zuletzt."); else {
            gm.sendMessage(ChatColor.RED + "Letzte Flags:"); for (String f : flags) gm.sendMessage(ChatColor.DARK_GRAY + " - " + ChatColor.WHITE + f);
        }
        boolean ok = antiCheat.saveClip(target.getUniqueId());
    gm.sendMessage((ok ? ChatColor.GREEN + "Clip gespeichert (data/plugins/TRIVA/clips)." : ChatColor.RED + "Clip konnte nicht gespeichert werden."));
        gm.sendMessage(ChatColor.GRAY + "TP-Optionen: " + ChatColor.YELLOW + "/tpmenu" + ChatColor.GRAY + " oder Specteam-GUI nutzen.");
    }
}
