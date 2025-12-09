package de.varo.features.specteam;

import de.varo.gui.GuiHelper;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpecteamFeature implements Listener {
    private final Map<String, List<UUID>> teams;
    private final Map<String, ChatColor> teamColors;
    private final Map<UUID, String> playerTeam;
    private final Set<UUID> gameMasters;
    private final Set<UUID> streamers;
    private final Set<UUID> forcedSpectateViewers;

    private final NamespacedKey specteamTeamKey;
    private final NamespacedKey specteamPlayerKey;
    private final NamespacedKey specteamSelectorKey;

    private final java.util.function.BiConsumer<Player, Player> onSelectTarget;

    public SpecteamFeature(Map<String, List<UUID>> teams,
                           Map<String, ChatColor> teamColors,
                           Map<UUID, String> playerTeam,
                           Set<UUID> gameMasters,
                           Set<UUID> streamers,
                           Set<UUID> forcedSpectateViewers,
                           NamespacedKey specteamTeamKey,
                           NamespacedKey specteamPlayerKey,
                           NamespacedKey specteamSelectorKey,
                           java.util.function.BiConsumer<Player, Player> onSelectTarget) {
        this.teams = teams;
        this.teamColors = teamColors;
        this.playerTeam = playerTeam;
        this.gameMasters = gameMasters;
        this.streamers = streamers;
        this.forcedSpectateViewers = forcedSpectateViewers;
        this.specteamTeamKey = specteamTeamKey;
        this.specteamPlayerKey = specteamPlayerKey;
        this.specteamSelectorKey = specteamSelectorKey;
        this.onSelectTarget = onSelectTarget;
    }

    public void openTeamsMenu(Player viewer) {
        boolean isSpectator = viewer.getGameMode() == GameMode.SPECTATOR;
        boolean isGm = gameMasters.contains(viewer.getUniqueId());
        boolean isStreamer = streamers.contains(viewer.getUniqueId());
        boolean isEliminatedOverlay = forcedSpectateViewers.contains(viewer.getUniqueId());
        boolean allowed = isSpectator || isGm || isStreamer || isEliminatedOverlay;
        if (!allowed) { viewer.sendMessage(ChatColor.RED + "Nur für Zuschauer oder Admins/Streamer."); return; }
        viewer.openInventory(GuiHelper.createSpecteamTeamsMenu(teams, teamColors, specteamTeamKey));
    }

    public void openPlayersMenu(Player viewer, String teamName) {
        viewer.openInventory(GuiHelper.createSpecteamPlayersMenu(teamName, teams.get(teamName), teamColors, specteamPlayerKey, specteamTeamKey));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player viewer = (Player) e.getWhoClicked();

        // Team list → open players list
        if (e.getView().getTitle().equals(GuiHelper.INV_SPECTEAM_TEAMS)) {
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item == null || item.getType() != org.bukkit.Material.PLAYER_HEAD) return;
            SkullMeta sm = (SkullMeta) item.getItemMeta();
            if (sm == null) return;
            String teamName = sm.getPersistentDataContainer().get(specteamTeamKey, PersistentDataType.STRING);
            if (teamName == null || !teams.containsKey(teamName)) return;
            openPlayersMenu(viewer, teamName);
            return;
        }

        // Player list → follow selected player
        if (e.getView().getTitle().startsWith(GuiHelper.INV_SPECTEAM_PLAYERS_PREFIX)) {
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item == null || item.getType() != org.bukkit.Material.PLAYER_HEAD) return;
            SkullMeta sm = (SkullMeta) item.getItemMeta();
            if (sm == null) return;
            String uuidStr = sm.getPersistentDataContainer().get(specteamPlayerKey, PersistentDataType.STRING);
            if (uuidStr == null) return;
            UUID targetId;
            try { targetId = UUID.fromString(uuidStr); } catch (Exception ex) { return; }
            Player target = org.bukkit.Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline()) { viewer.sendMessage(ChatColor.RED + "Spieler ist offline."); return; }
            if (target.getGameMode() == GameMode.SPECTATOR) { viewer.sendMessage(ChatColor.GRAY + "Spieler ist Zuschauer."); return; }

            boolean isSpectator = viewer.getGameMode() == GameMode.SPECTATOR;
            boolean isGm = gameMasters.contains(viewer.getUniqueId());
            boolean isStreamer = streamers.contains(viewer.getUniqueId());
            boolean isForcedOverlay = forcedSpectateViewers.contains(viewer.getUniqueId());

            boolean allowed = isSpectator || isGm || isStreamer || isForcedOverlay;
            if (!allowed) { viewer.sendMessage(ChatColor.RED + "Nur für Zuschauer oder Admins/Streamer."); return; }

            if (!isGm && !isStreamer && !isSpectator) {
                String vt = playerTeam.get(viewer.getUniqueId());
                String tt = playerTeam.get(target.getUniqueId());
                if (vt == null || !vt.equals(tt)) { viewer.sendMessage(ChatColor.RED + "Nur eigenes Team erlaubt."); viewer.closeInventory(); return; }
            }

            // Delegate to callback to decide overlay vs spectator camera
            if (onSelectTarget != null) onSelectTarget.accept(viewer, target);
            viewer.closeInventory();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack sel = p.getInventory().getItemInMainHand();
        if (sel == null || sel.getType() == Material.AIR) return;
        ItemMeta sm = sel.getItemMeta();
        if (sm == null) return;
        if (sm.getPersistentDataContainer().has(specteamSelectorKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            openTeamsMenu(p);
        }
    }
}
