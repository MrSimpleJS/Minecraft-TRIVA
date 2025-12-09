package de.varo.features.portal;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Handles portal-related restrictions and messaging.
 *
 * - Disables End access entirely (both via End portals and frame activation)
 * - Restricts Nether access during the final project hour
 * - Enforces a per-player Nether time limit
 * - Remembers the last Nether portal location for a player
 */
public class PortalFeature implements Listener {

    private static final String MSG_END_DISABLED = ChatColor.RED + "Das End ist deaktiviert!";
    private static final String MSG_NETHER_LAST_HOUR_DISABLED = ChatColor.RED + "Nether ist in der letzten Projekt-Stunde deaktiviert.";
    private static final String MSG_NETHER_TIME_EXHAUSTED = ChatColor.RED + "Deine Nether-Zeit ist aufgebraucht.";

    private static final long ONE_HOUR_MS = 60L * 60_000L;

    private final Supplier<Long> projectRemainingMs;
    private final Supplier<Long> netherLimitMs;
    private final Map<UUID, Long> netherUsedMs;
    private final Map<UUID, Long> netherSessionStart;
    private final Map<UUID, Location> lastNetherPortal;

    public PortalFeature(
            Supplier<Long> projectRemainingMs,
            Supplier<Long> netherLimitMs,
            Map<UUID, Long> netherUsedMs,
            Map<UUID, Long> netherSessionStart,
            Map<UUID, Location> lastNetherPortal
    ) {
        this.projectRemainingMs = Objects.requireNonNull(projectRemainingMs, "projectRemainingMs");
        this.netherLimitMs = Objects.requireNonNull(netherLimitMs, "netherLimitMs");
        this.netherUsedMs = Objects.requireNonNull(netherUsedMs, "netherUsedMs");
        this.netherSessionStart = Objects.requireNonNull(netherSessionStart, "netherSessionStart");
        this.lastNetherPortal = Objects.requireNonNull(lastNetherPortal, "lastNetherPortal");
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        final Player player = e.getPlayer();

        // End portals are globally disabled
        if (e.getCause() == TeleportCause.END_PORTAL) {
            denyWithMessage(e, player, MSG_END_DISABLED);
            return;
        }

        // Additional checks for entering the Nether
        if (!isEnteringNether(e)) {
            return;
        }

        // Block Nether during the last project hour
        final long remaining = projectRemainingMs.get();
        if (remaining <= ONE_HOUR_MS) {
            denyWithMessage(e, player, MSG_NETHER_LAST_HOUR_DISABLED);
            return;
        }

        // Enforce per-player Nether time budget
        final UUID uuid = player.getUniqueId();
        final long usedMs = getUsedNetherMs(uuid);
        final long limitMs = netherLimitMs.get();
        if (usedMs >= limitMs) {
            denyWithMessage(e, player, MSG_NETHER_TIME_EXHAUSTED);
            return;
        }

        // Remember the intended Nether location (portal exit)
        // e.getTo() is non-null here due to isEnteringNether check
        lastNetherPortal.put(uuid, e.getTo());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        // Prevent activating End portals with Eyes of Ender
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.END_PORTAL_FRAME) return;

        final ItemStack inHand = e.getItem();
        if (inHand == null || inHand.getType() != Material.ENDER_EYE) return;

        denyWithMessage(e, e.getPlayer(), MSG_END_DISABLED);
    }

    private boolean isEnteringNether(PlayerPortalEvent e) {
        if (e.getTo() == null) return false;
        final Location to = e.getTo();
        return to.getWorld() != null && to.getWorld().getEnvironment() == Environment.NETHER;
    }

    private long getUsedNetherMs(UUID uuid) {
        long used = netherUsedMs.getOrDefault(uuid, 0L);
        final Long sessionStart = netherSessionStart.get(uuid);
        if (sessionStart != null) {
            used += (System.currentTimeMillis() - sessionStart);
        }
        return used;
    }

    private void denyWithMessage(Cancellable e, Player player, String message) {
        e.setCancelled(true);
        player.sendMessage(message);
    }
}
