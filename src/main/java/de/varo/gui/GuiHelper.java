package de.varo.gui;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class GuiHelper {
    private GuiHelper() {}

    public static final String INV_GM_TP = ChatColor.GOLD + "GM Teleport";
    public static final String INV_GM_PLAYER_ACTIONS_PREFIX = ChatColor.GOLD + "Aktionen » ";
    public static final String INV_SPECTEAM_TEAMS = ChatColor.DARK_AQUA + "Spectate Team » Teams";
    public static final String INV_SPECTEAM_PLAYERS_PREFIX = ChatColor.DARK_AQUA + "Spectate » ";

    public static Inventory createGmTeleportMenu() {
        Inventory inv = Bukkit.createInventory(null, 54, INV_GM_TP);
        for (Player t : Bukkit.getOnlinePlayers()) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            if (sm != null) {
                sm.setOwningPlayer(t);
                sm.setDisplayName(ChatColor.YELLOW + t.getName());
                head.setItemMeta(sm);
                inv.addItem(head);
            }
        }
        return inv;
    }

    public static Inventory createGmActions(String targetName) {
        Inventory inv = Bukkit.createInventory(null, 27, INV_GM_PLAYER_ACTIONS_PREFIX + ChatColor.YELLOW + targetName);
        ItemStack to = new ItemStack(Material.LIME_DYE);
        ItemMeta toM = to.getItemMeta(); toM.setDisplayName(ChatColor.GREEN + "Zu Spieler teleportieren"); to.setItemMeta(toM);
        ItemStack here = new ItemStack(Material.ENDER_PEARL);
        ItemMeta heM = here.getItemMeta(); heM.setDisplayName(ChatColor.AQUA + "Spieler zu mir"); here.setItemMeta(heM);
        ItemStack spec = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta spM = spec.getItemMeta(); spM.setDisplayName(ChatColor.YELLOW + "Beobachten (Spectator)"); spec.setItemMeta(spM);

        inv.setItem(11, to);
        inv.setItem(13, spec);
        inv.setItem(15, here);
        return inv;
    }

    public static int invSizeFor(int count) {
        int size = ((count + 8) / 9) * 9;
        return Math.min(Math.max(9, size), 54);
    }

    public static Inventory createSpecteamTeamsMenu(Map<String, List<UUID>> teams,
                                                    Map<String, ChatColor> teamColors,
                                                    NamespacedKey specteamTeamKey) {
    int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, INV_SPECTEAM_TEAMS);
        for (Map.Entry<String, List<UUID>> e : teams.entrySet()) {
            String teamName = e.getKey();
            ChatColor color = teamColors.getOrDefault(teamName, ChatColor.WHITE);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();

            Player firstOnline = null;
            for (UUID id : e.getValue()) {
                Player pl = Bukkit.getPlayer(id);
                if (pl != null && pl.isOnline()) { firstOnline = pl; break; }
            }
            if (firstOnline != null) sm.setOwningPlayer(firstOnline);

            sm.setDisplayName(color + teamName);

            List<String> lore = new ArrayList<>();
            int onlineActive = 0;
            for (UUID id : e.getValue()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                boolean online = op.isOnline();
                boolean active = false;
                if (online) {
                    Player pl = op.getPlayer();
                    active = (pl != null && pl.getGameMode() != GameMode.SPECTATOR);
                }
                if (online && active) onlineActive++;
                String nm = (op.getName() == null ? "Spieler" : op.getName());
                lore.add((online ? (active ? ChatColor.GREEN : ChatColor.GRAY) : ChatColor.DARK_GRAY) + "• " + nm);
            }
            lore.add(ChatColor.GRAY + "Aktiv: " + ChatColor.WHITE + onlineActive + ChatColor.GRAY + " / " + e.getValue().size());
            sm.setLore(lore);

            sm.getPersistentDataContainer().set(specteamTeamKey, PersistentDataType.STRING, teamName);
            head.setItemMeta(sm);
            inv.addItem(head);
        }
        return inv;
    }

    public static Inventory createSpecteamPlayersMenu(String teamName,
                                                      List<UUID> members,
                                                      Map<String, ChatColor> teamColors,
                                                      NamespacedKey specteamPlayerKey,
                                                      NamespacedKey specteamTeamKey) {
        ChatColor color = teamColors.getOrDefault(teamName, ChatColor.WHITE);
    int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, INV_SPECTEAM_PLAYERS_PREFIX + color + teamName);
        for (UUID id : members) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            boolean online = op.isOnline();
            Player pl = online ? op.getPlayer() : null;
            boolean active = (pl != null && pl.getGameMode() != GameMode.SPECTATOR);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            if (pl != null) sm.setOwningPlayer(pl);
            String nm = (op.getName() == null ? "Spieler" : op.getName());
            sm.setDisplayName((online ? (active ? ChatColor.GREEN : ChatColor.GRAY) : ChatColor.DARK_GRAY) + nm);

            List<String> lore = new ArrayList<>();
            if (online) {
                if (active) lore.add(ChatColor.YELLOW + "Klicken, um zuzuschauen.");
                else lore.add(ChatColor.GRAY + "Spieler ist Zuschauer.");
            } else {
                lore.add(ChatColor.DARK_GRAY + "Offline");
            }
            sm.setLore(lore);
            sm.getPersistentDataContainer().set(specteamPlayerKey, PersistentDataType.STRING, id.toString());
            sm.getPersistentDataContainer().set(specteamTeamKey, PersistentDataType.STRING, teamName);
            head.setItemMeta(sm);
            inv.addItem(head);
        }
        return inv;
    }
}
