package de.varo.features.combat;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class CombatTagFeature implements Listener {
    private final JavaPlugin plugin;
    private final long combatTagMs;
    private final java.util.function.Predicate<java.util.UUID> isStaff;
    private final Map<java.util.UUID, Long> taggedUntil = new HashMap<>();
    private int taskId = -1;

    public CombatTagFeature(JavaPlugin plugin, long combatTagMs, java.util.function.Predicate<java.util.UUID> isStaff) {
        this.plugin = plugin; this.combatTagMs = combatTagMs; this.isStaff = isStaff;
    }

    public void start() {
        if (taskId != -1) return;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            long now = System.currentTimeMillis();
            java.util.Iterator<Map.Entry<java.util.UUID,Long>> it = taggedUntil.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<java.util.UUID,Long> e = it.next();
                if (e.getValue() <= now) { it.remove(); continue; }
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null && p.isOnline()) {
                    long left = Math.max(0L, e.getValue() - now);
                    String msg = ChatColor.RED + "Im Kampf: " + ChatColor.WHITE + formatTime(left);
                    try { p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg)); } catch (Throwable ignored) {}
                }
            }
        }, 20L, 20L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
        PlayerTeleportEvent.getHandlerList().unregister(this);
        PlayerCommandPreprocessEvent.getHandlerList().unregister(this);
        EntityDamageByEntityEvent.getHandlerList().unregister(this);
        PlayerQuitEvent.getHandlerList().unregister(this);
        taggedUntil.clear();
    }

    public boolean isTagged(Player p) { return taggedUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis(); }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        Player attacker = null;
        if (e.getDamager() instanceof Player) attacker = (Player) e.getDamager();
        else if (e.getDamager() instanceof org.bukkit.entity.Projectile) {
            Object sh = ((org.bukkit.entity.Projectile) e.getDamager()).getShooter();
            if (sh instanceof Player) attacker = (Player) sh;
        }
        long until = System.currentTimeMillis() + combatTagMs;
        taggedUntil.put(victim.getUniqueId(), until);
        if (attacker != null) taggedUntil.put(attacker.getUniqueId(), until);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) return;
        Player p = e.getPlayer();
        if (isStaff != null && isStaff.test(p.getUniqueId())) return;
        if (isTagged(p)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Teleport im Kampf blockiert.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (isStaff != null && isStaff.test(p.getUniqueId())) return;
        if (!isTagged(p)) return;
        String msg = e.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (msg.startsWith("/spawn") || msg.startsWith("/home") || msg.startsWith("/warp") || msg.startsWith("/tp") || msg.startsWith("/rtp")) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Befehl im Kampf blockiert.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (isTagged(e.getPlayer())) {
            // Broadcast info; NPC handling performed in main plugin
            Bukkit.broadcastMessage(ChatColor.DARK_RED + e.getPlayer().getName() + ChatColor.GRAY + " hat im Kampf geloggt.");
        }
    }

    private String formatTime(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        return s + "s";
    }
}
