package de.varo.features.moderation;

import de.varo.features.anticheat.AntiCheatFeature;
import de.varo.util.ItemUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Moderation tools: staff chat toggle, mute/unmute, warn escalation, logs print,
 * mining stats and review helpers, and a simple Review GUI.
 */
public class ModerationFeature {
    private final Set<java.util.UUID> gameMasters;
    private final Map<java.util.UUID, Long> mutedUntil;
    private final Map<java.util.UUID, Integer> warnCount;
    private final Deque<String> actionLog;
    private final AntiCheatFeature antiCheat;

    public ModerationFeature(Set<java.util.UUID> gameMasters,
                             Map<java.util.UUID, Long> mutedUntil,
                             Map<java.util.UUID, Integer> warnCount,
                             Deque<String> actionLog,
                             AntiCheatFeature antiCheat) {
        this.gameMasters = gameMasters;
        this.mutedUntil = mutedUntil;
        this.warnCount = warnCount;
        this.actionLog = actionLog;
        this.antiCheat = antiCheat;
    }

    public void printLogs(Player p, String[] args) {
        int max = 50;
        String filter = null;
        if (args != null && args.length >= 1) {
            // Allow '/logs rules' or '/logs 100 rules'
            for (String a : args) {
                if (a == null) continue;
                String al = a.toLowerCase(java.util.Locale.ROOT);
                if (al.matches("^\\d{1,3}$")) { try { max = Math.max(1, Math.min(200, Integer.parseInt(al))); } catch (NumberFormatException ignored) {} }
                else filter = al;
            }
        }
        if (actionLog.isEmpty()) { p.sendMessage(ChatColor.YELLOW + "Keine Logs vorhanden."); return; }
        String head = "—— LOGS" + (filter != null ? (" [" + filter + "]") : "") + " (letzte " + actionLog.size() + ") ——";
        p.sendMessage(ChatColor.GOLD + head);
        java.util.Iterator<String> it = actionLog.descendingIterator();
        int count = 0;
        while (it.hasNext() && count < max) {
            String line = it.next();
            if (filter != null) {
                // Match case-insensitive; simple contains
                if (!org.bukkit.ChatColor.stripColor(line).toLowerCase(java.util.Locale.ROOT).contains(filter)) continue;
            }
            p.sendMessage(line);
            count++;
        }
        if (count == 0) p.sendMessage(ChatColor.GRAY + "Keine Treffer für Filter.");
    }

    public void staffChatToggle(java.util.Set<java.util.UUID> staffChatEnabled, Player p) {
        java.util.UUID id = p.getUniqueId();
        boolean on = !staffChatEnabled.contains(id);
        if (on) { staffChatEnabled.add(id); p.sendMessage(ChatColor.DARK_AQUA + "Staffchat an. Deine Nachrichten gehen nur an Staff."); }
        else { staffChatEnabled.remove(id); p.sendMessage(ChatColor.DARK_AQUA + "Staffchat aus."); }
    }

    public void mute(Player gm, String[] args) {
        if (args == null || args.length < 2) { gm.sendMessage(ChatColor.YELLOW + "/mute <Spieler> <Minuten>"); return; }
        Player t = Bukkit.getPlayerExact(args[0]); if (t == null) { gm.sendMessage(ChatColor.RED + "Spieler offline."); return; }
        long minutes;
        try { minutes = Math.max(1, Long.parseLong(args[1])); } catch (NumberFormatException ex) { gm.sendMessage(ChatColor.RED + "Zeit ungültig."); return; }
        long until = System.currentTimeMillis() + minutes * 60_000L;
        mutedUntil.put(t.getUniqueId(), until);
        gm.sendMessage(ChatColor.GREEN + t.getName() + " gemutet für " + minutes + "m.");
        t.sendMessage(ChatColor.RED + "Du wurdest gemutet für " + minutes + " Minuten.");
    }

    public void unmute(Player gm, String[] args) {
        if (args == null || args.length < 1) { gm.sendMessage(ChatColor.YELLOW + "/unmute <Spieler>"); return; }
        Player t = Bukkit.getPlayerExact(args[0]); if (t == null) { gm.sendMessage(ChatColor.RED + "Spieler offline."); return; }
        mutedUntil.remove(t.getUniqueId());
        gm.sendMessage(ChatColor.GREEN + t.getName() + " entmutet.");
        t.sendMessage(ChatColor.GREEN + "Du bist entmutet.");
    }

    public void warn(Player gm, String[] args) {
        if (args == null || args.length < 2) { gm.sendMessage(ChatColor.YELLOW + "/warn <Spieler> <Grund>"); return; }
        Player t = Bukkit.getPlayerExact(args[0]); if (t == null) { gm.sendMessage(ChatColor.RED + "Spieler offline."); return; }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        int wc = warnCount.merge(t.getUniqueId(), 1, Integer::sum);
        gm.sendMessage(ChatColor.YELLOW + t.getName() + " verwarnt (" + wc + ")");
        t.sendMessage(ChatColor.RED + "Verwarnung: " + ChatColor.WHITE + reason + ChatColor.GRAY + " (" + wc + ")");
        if (wc == 3) {
            long until = System.currentTimeMillis() + 10 * 60_000L;
            mutedUntil.put(t.getUniqueId(), until);
            t.sendMessage(ChatColor.RED + "Automatisch gemutet (10m) wegen Verwarnungen.");
        } else if (wc >= 5) {
            long until = System.currentTimeMillis() + 60 * 60_000L;
            mutedUntil.put(t.getUniqueId(), until);
            t.sendMessage(ChatColor.RED + "Automatisch gemutet (60m) wegen Verwarnungen.");
        }
    }

    public void review(Player gm, String[] args, int acWindowMinutes, int acDiamondPerBlocks, int acDebrisPerBlocks, int acBranchWithinSteps) {
        if (args == null || args.length == 0) { gm.sendMessage(ChatColor.YELLOW + "/review <Spieler>"); return; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { gm.sendMessage(ChatColor.RED + "Spieler offline."); return; }
        if (antiCheat == null) { gm.sendMessage(ChatColor.RED + "AntiCheat nicht aktiv."); return; }
        AntiCheatFeature.MiningSnapshot snap = antiCheat.snapshotMining(target.getUniqueId());
        int vio = antiCheat.getViolations(target.getUniqueId());
        gm.sendMessage(ChatColor.GOLD + "—— Review: " + ChatColor.YELLOW + target.getName() + ChatColor.GOLD + " ——");
        gm.sendMessage(ChatColor.GRAY + "Violations: " + ChatColor.WHITE + vio);
        if (snap != null) {
            gm.sendMessage(ChatColor.AQUA + "Overworld: " + ChatColor.WHITE + snap.stone + ChatColor.GRAY + ", Diamanten: " + ChatColor.WHITE + snap.diamonds
                    + ChatColor.GRAY + ", Ratio: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.2f/"+acDiamondPerBlocks, snap.diamondPerNorm));
            gm.sendMessage(ChatColor.LIGHT_PURPLE + "Nether: " + ChatColor.WHITE + snap.netherrack + ChatColor.GRAY + ", Netherit: " + ChatColor.WHITE + snap.debris
                    + ChatColor.GRAY + ", Ratio: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.2f/"+acDebrisPerBlocks, snap.debrisPerNorm));
            gm.sendMessage(ChatColor.YELLOW + "Branch→Erz (≤" + acBranchWithinSteps + "): " + ChatColor.WHITE + snap.branchHits);
        }
        java.util.List<String> flags = antiCheat.getRecentFlags(target.getUniqueId(), 10);
        if (flags.isEmpty()) gm.sendMessage(ChatColor.GRAY + "Keine Flags zuletzt.");
        else {
            gm.sendMessage(ChatColor.RED + "Letzte Flags:");
            for (String f : flags) gm.sendMessage(ChatColor.DARK_GRAY + " - " + ChatColor.WHITE + f);
        }
        boolean ok = antiCheat.saveClip(target.getUniqueId());
    gm.sendMessage((ok ? ChatColor.GREEN + "Clip gespeichert (data/plugins/TRIVA/clips)." : ChatColor.RED + "Clip konnte nicht gespeichert werden."));
    }

    public void openReviewGui(Player gm, Player target) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "Review » " + ChatColor.YELLOW + target.getName());
        inv.setItem(0, ItemUtils.named(new org.bukkit.inventory.ItemStack(Material.COMPASS), ChatColor.GOLD + "TP zu Spieler"));
        inv.setItem(1, ItemUtils.named(new org.bukkit.inventory.ItemStack(Material.ENDER_PEARL), ChatColor.GOLD + "Spieler zu mir"));
        inv.setItem(2, ItemUtils.named(new org.bukkit.inventory.ItemStack(Material.SPECTRAL_ARROW), ChatColor.GOLD + "Sicht verfolgen"));
        inv.setItem(4, ItemUtils.named(new org.bukkit.inventory.ItemStack(Material.PAPER), ChatColor.AQUA + "Clip speichern"));
        inv.setItem(6, ItemUtils.named(new org.bukkit.inventory.ItemStack(Material.DIAMOND_PICKAXE), ChatColor.YELLOW + "Mining-Stats"));
        inv.setItem(7, ItemUtils.named(new org.bukkit.inventory.ItemStack(Material.IRON_SWORD), ChatColor.YELLOW + "Combat-Info"));
        inv.setItem(8, ItemUtils.named(new org.bukkit.inventory.ItemStack(Material.BOOK), ChatColor.RED + "Flags (Seite 1)"));
        if (antiCheat != null) {
            AntiCheatFeature.MiningSnapshot snap = antiCheat.snapshotMining(target.getUniqueId());
            java.util.List<String> lore = new java.util.ArrayList<>();
            if (snap != null) {
                lore.add(ChatColor.GRAY + "Overworld: " + ChatColor.WHITE + snap.stone + ChatColor.GRAY + ", Dia: " + ChatColor.WHITE + snap.diamonds);
                lore.add(ChatColor.GRAY + "Nether: " + ChatColor.WHITE + snap.netherrack + ChatColor.GRAY + ", Debris: " + ChatColor.WHITE + snap.debris);
                lore.add(ChatColor.GRAY + "Ratio Dia: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.2f", snap.diamondPerNorm));
                lore.add(ChatColor.GRAY + "Ratio Debris: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.2f", snap.debrisPerNorm));
                lore.add(ChatColor.GRAY + "Branch→Erz: " + ChatColor.WHITE + snap.branchHits);
            } else lore.add(ChatColor.GRAY + "Keine Mining-Daten.");
            org.bukkit.inventory.meta.ItemMeta im = inv.getItem(6).getItemMeta();
            if (im != null) { im.setLore(lore); inv.getItem(6).setItemMeta(im); }
            java.util.List<String> flags = antiCheat.getRecentFlags(target.getUniqueId(), 45);
            int slot = 27; int idx = 0;
            for (String f : flags) { if (idx >= 18) break; inv.setItem(slot++, ItemUtils.named(new org.bukkit.inventory.ItemStack(Material.MAP), ChatColor.RED + f)); idx++; }
        }
        gm.openInventory(inv);
    }

    public void onReviewClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player gm = (Player) e.getWhoClicked();
        org.bukkit.inventory.InventoryView v = gm.getOpenInventory();
        String title;
        try { title = v.getTitle(); } catch (Throwable t) { return; }
        if (title == null || !title.startsWith(ChatColor.DARK_RED + "Review » ")) return;
        e.setCancelled(true);
        if (!gameMasters.contains(gm.getUniqueId())) return;
        String targetName = ChatColor.stripColor(title.substring((ChatColor.DARK_RED + "Review » ").length()));
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { gm.closeInventory(); return; }
        org.bukkit.inventory.ItemStack it = e.getCurrentItem(); if (it == null) return;
        switch (it.getType()) {
            case COMPASS:
                gm.teleport(target.getLocation()); gm.sendMessage(ChatColor.GREEN + "TP zu " + target.getName()); break;
            case ENDER_PEARL:
                target.teleport(gm.getLocation()); gm.sendMessage(ChatColor.GREEN + target.getName() + " zu dir teleportiert"); break;
            case SPECTRAL_ARROW:
                gm.setGameMode(GameMode.SPECTATOR); gm.setSpectatorTarget(target); gm.sendMessage(ChatColor.YELLOW + "Sicht: " + target.getName()); break;
            case PAPER:
                if (antiCheat != null && antiCheat.saveClip(target.getUniqueId())) gm.sendMessage(ChatColor.GREEN + "Clip gespeichert.");
                else gm.sendMessage(ChatColor.RED + "Clip fehlgeschlagen.");
                break;
            default: break;
        }
        gm.closeInventory();
    }
}
