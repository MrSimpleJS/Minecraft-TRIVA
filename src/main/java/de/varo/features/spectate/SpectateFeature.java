package de.varo.features.spectate;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpectateFeature implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, UUID> forcedTargets; // viewer -> target (shared)
    private final Map<UUID, Integer> overlayTasks = new HashMap<>();
    private static final String TITLE_PREFIX = ChatColor.DARK_AQUA + "Spectate Inventar » " + ChatColor.YELLOW;

    private final java.util.function.Supplier<Integer> spectateDelaySeconds;
    private final java.util.function.Predicate<java.util.UUID> isStreamer;

    // Per-target buffered samples to support delayed display for streamers
    private static class Sample {
        final long ts; final Location eye; final double hp; final double max; final int food; final float yaw; final float pitch;
        Sample(long ts, Location eye, double hp, double max, int food, float yaw, float pitch) { this.ts=ts; this.eye=eye; this.hp=hp; this.max=max; this.food=food; this.yaw=yaw; this.pitch=pitch; }
    }
    private final Map<UUID, java.util.Deque<Sample>> samplesByTarget = new HashMap<>();

    public SpectateFeature(JavaPlugin plugin, Map<UUID, UUID> forcedTargets,
                           java.util.function.Supplier<Integer> spectateDelaySeconds,
                           java.util.function.Predicate<java.util.UUID> isStreamer) {
        this.plugin = plugin;
        this.forcedTargets = forcedTargets;
        this.spectateDelaySeconds = spectateDelaySeconds;
        this.isStreamer = isStreamer;
    }

    public boolean isForced(UUID viewerId) { return forcedTargets.containsKey(viewerId); }
    public boolean isSpectateInventory(InventoryView view) {
        try { return view != null && view.getTitle() != null && view.getTitle().startsWith(TITLE_PREFIX); }
        catch (Throwable ignored) { return false; }
    }

    public void startOverlay(Player viewer, Player target, boolean mimicPerspective) {
        stopOverlay(viewer.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PREFIX + target.getName());
        fillInventory(inv, target);
        viewer.openInventory(inv);

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            try {
                if (!viewer.isOnline()) { stopOverlay(viewer.getUniqueId()); return; }
                Player st = null;
                UUID forced = forcedTargets.get(viewer.getUniqueId());
                if (forced != null) {
                    Player forcedPlayer = Bukkit.getPlayer(forced);
                    if (forcedPlayer == null || !forcedPlayer.isOnline()) {
                        forcedTargets.remove(viewer.getUniqueId());
                        stopOverlay(viewer.getUniqueId());
                        return;
                    }
                    st = forcedPlayer;
                } else if (!mimicPerspective && viewer.getGameMode() == GameMode.SPECTATOR && viewer.getSpectatorTarget() instanceof Player) {
                    st = (Player) viewer.getSpectatorTarget();
                }
                if (st == null || !st.isOnline()) { stopOverlay(viewer.getUniqueId()); return; }

                Location useEye = null; double hpVal; double maxVal; int foodVal; float useYaw; float usePitch;
                boolean doDelay = mimicPerspective && isStreamer != null && isStreamer.test(viewer.getUniqueId());
                int delaySec = Math.max(0, spectateDelaySeconds != null ? spectateDelaySeconds.get() : 0);
                if (doDelay && delaySec > 0) {
                    long now = System.currentTimeMillis();
                    java.util.Deque<Sample> dq = samplesByTarget.computeIfAbsent(st.getUniqueId(), k -> new java.util.ArrayDeque<>());
                    // append current
                    Location eyeNow = st.getEyeLocation();
                    double hpNow = Math.max(0.0, st.getHealth());
                    double maxNow = (st.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null)
                            ? st.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue() : 20.0;
                    dq.addLast(new Sample(now, eyeNow.clone(), hpNow, maxNow, st.getFoodLevel(), st.getLocation().getYaw(), st.getLocation().getPitch()));
                    while (!dq.isEmpty() && now - dq.peekFirst().ts > (delaySec + 5) * 1000L) dq.removeFirst();
                    // pick oldest sample that is at least delaySec old
                    Sample chosen = null;
                    java.util.Iterator<Sample> it = dq.iterator();
                    while (it.hasNext()) { Sample s = it.next(); if (now - s.ts >= delaySec * 1000L) { chosen = s; break; } }
                    if (chosen != null) {
                        useEye = chosen.eye;
                        useYaw = chosen.yaw; usePitch = chosen.pitch;
                        hpVal = chosen.hp; maxVal = chosen.max; foodVal = chosen.food;
                    } else {
                        // not enough buffer yet, keep still (no teleport) and show placeholder
                        useEye = null; useYaw = st.getLocation().getYaw(); usePitch = st.getLocation().getPitch();
                        hpVal = -1; maxVal = -1; foodVal = -1;
                    }
                } else {
                    Location eye = st.getEyeLocation();
                    useEye = eye; useYaw = st.getLocation().getYaw(); usePitch = st.getLocation().getPitch();
                    hpVal = Math.max(0.0, st.getHealth());
                    maxVal = (st.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null)
                            ? st.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue() : 20.0;
                    foodVal = st.getFoodLevel();
                }

                if (mimicPerspective && useEye != null) {
                    Location to = new Location(useEye.getWorld(), useEye.getX(), useEye.getY(), useEye.getZ(), useYaw, usePitch);
                    viewer.teleport(to);
                    viewer.setAllowFlight(true);
                    viewer.setFlying(true);
                }

                InventoryView view = viewer.getOpenInventory();
                String title = null;
                try { title = (view != null ? view.getTitle() : null); } catch (Throwable ignored) {}
                if (view != null && title != null && title.startsWith(TITLE_PREFIX)) fillInventory(view.getTopInventory(), st);
                double hp = (hpVal >= 0 ? hpVal : Math.max(0.0, st.getHealth()));
                double max = (maxVal >= 0 ? maxVal : ((st.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null)
                        ? st.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() : 20.0));
                int food = (foodVal >= 0 ? foodVal : st.getFoodLevel());
                String bar = ChatColor.RED + "❤ " + (int)Math.round(hp) + ChatColor.GRAY + "/" + (int)Math.round(max)
                        + ChatColor.DARK_GRAY + "  |  " + ChatColor.YELLOW + food + ChatColor.GRAY + "/20";
                viewer.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar));
            } catch (Throwable t) {
                plugin.getLogger().warning("Spectate overlay task error for " + viewer.getName() + ": " + t.getMessage());
                stopOverlay(viewer.getUniqueId());
            }
        }, 0L, 2L);
        overlayTasks.put(viewer.getUniqueId(), taskId);
    }

    public void stopOverlay(UUID viewerId) {
        Integer id = overlayTasks.remove(viewerId);
        if (id != null) Bukkit.getScheduler().cancelTask(id);
        forcedTargets.remove(viewerId);
    }

    private void fillInventory(Inventory inv, Player target) {
        inv.clear();
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < Math.min(36, contents.length); i++) inv.setItem(i, contents[i]);
        inv.setItem(45, target.getInventory().getHelmet());
        inv.setItem(46, target.getInventory().getChestplate());
        inv.setItem(47, target.getInventory().getLeggings());
        inv.setItem(48, target.getInventory().getBoots());
        inv.setItem(49, target.getInventory().getItemInOffHand());
    }

    // Events
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        if (isSpectateInventory(e.getView()) && !isForced(p.getUniqueId())) stopOverlay(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (isForced(p.getUniqueId()) || isSpectateInventory(e.getView())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stopOverlay(e.getPlayer().getUniqueId());
    }
}
