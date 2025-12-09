package de.varo.features.guard;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GuardFeature implements Listener {
    private final Set<UUID> frozen;
    private final java.util.Set<java.util.UUID> gameMasters;
    private final Map<UUID, UUID> forcedSpectateTargets; // viewer -> target
    private final NamespacedKey specteamSelectorKey;
    private final NamespacedKey gmTpMenuKey;
    private final NamespacedKey gmReviewKey;
    private final NamespacedKey gmMiningKey;
    private final NamespacedKey gmReportsKey;

    public GuardFeature(Set<UUID> frozen, Map<UUID, UUID> forcedSpectateTargets, NamespacedKey specteamSelectorKey, java.util.Set<java.util.UUID> gameMasters,
                        NamespacedKey gmTpMenuKey, NamespacedKey gmReviewKey, NamespacedKey gmMiningKey, NamespacedKey gmReportsKey) {
        this.frozen = frozen;
        this.forcedSpectateTargets = forcedSpectateTargets;
        this.specteamSelectorKey = specteamSelectorKey;
        this.gameMasters = gameMasters;
        this.gmTpMenuKey = gmTpMenuKey;
        this.gmReviewKey = gmReviewKey;
        this.gmMiningKey = gmMiningKey;
        this.gmReportsKey = gmReportsKey;
    }

    // Note: prior blocked() helper removed; explicit checks with GM exemption are used per event.

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if ((frozen.contains(p.getUniqueId()) && !isGm(p)) || forcedSpectateTargets.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if ((frozen.contains(p.getUniqueId()) && !isGm(p)) || forcedSpectateTargets.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if ((frozen.contains(p.getUniqueId()) && !isGm(p)) || forcedSpectateTargets.containsKey(p.getUniqueId())) {
            // Allow switching to Specteam selector (any material) recognized by PDC key
            ItemStack next = p.getInventory().getItem(e.getNewSlot());
            boolean isSelector = false;
            if (next != null) {
                ItemMeta im = next.getItemMeta();
                isSelector = (im != null && im.getPersistentDataContainer().has(specteamSelectorKey, PersistentDataType.BYTE));
            }
            if (!isSelector) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if ((frozen.contains(p.getUniqueId()) && !isGm(p)) || forcedSpectateTargets.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    private boolean isGm(Player p) { return gameMasters != null && gameMasters.contains(p.getUniqueId()); }

    // Cancel interactions for frozen players except Specteam selector and GM tools; allow GMs
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!(frozen.contains(p.getUniqueId()) && !isGm(p))) return;

        switch (e.getAction()) {
            case RIGHT_CLICK_AIR:
            case RIGHT_CLICK_BLOCK:
                ItemStack sel = p.getInventory().getItemInMainHand();
                if (sel != null) {
                    ItemMeta sm = sel.getItemMeta();
                    if (sm != null) {
                        var c = sm.getPersistentDataContainer();
                        if (c.has(specteamSelectorKey, PersistentDataType.BYTE)
                                || (gmTpMenuKey != null && c.has(gmTpMenuKey, PersistentDataType.BYTE))
                                || (gmReviewKey != null && c.has(gmReviewKey, PersistentDataType.BYTE))
                                || (gmMiningKey != null && c.has(gmMiningKey, PersistentDataType.BYTE))
                                || (gmReportsKey != null && c.has(gmReportsKey, PersistentDataType.BYTE))) {
                            // allowed
                            return;
                        }
                    }
                }
                e.setCancelled(true);
                return;
            default:
                e.setCancelled(true);
        }
    }

    // Prevent movement/jumping when frozen (GMs exempt)
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (frozen.contains(p.getUniqueId()) && !isGm(p)) {
            if (!e.getFrom().getBlock().equals(e.getTo().getBlock())) e.setCancelled(true);
            if (e.getTo().getY() > e.getFrom().getY()) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId()) && !isGm(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId()) && !isGm(e.getPlayer())) e.setCancelled(true);
    }
}
