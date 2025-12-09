package de.varo.services;

import de.varo.VaroPlugin;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class CombatLoggerService implements Listener {
    private final VaroPlugin plugin;
    private final NamespacedKey combatNpcKey;
    private final long combatTagMs;
    private final int loggerNpcSeconds;
    private final Map<UUID, Long> lastPvP;

    private static class CombatSnapshot { final ItemStack[] contents; final ItemStack[] armor; CombatSnapshot(ItemStack[] c, ItemStack[] a){contents=c;armor=a;} }
    private final Map<UUID, CombatSnapshot> pendingSnapshots = new HashMap<>();
    private final Map<UUID, UUID> playerToNpc = new HashMap<>();
    private final Map<UUID, UUID> npcToPlayer = new HashMap<>();
    private final Map<UUID, Integer> npcDespawnTask = new HashMap<>();

    public CombatLoggerService(VaroPlugin plugin, NamespacedKey combatNpcKey, long combatTagMs, int loggerNpcSeconds, Map<UUID, Long> lastPvP) {
        this.plugin = plugin;
        this.combatNpcKey = combatNpcKey;
        this.combatTagMs = combatTagMs;
        this.loggerNpcSeconds = loggerNpcSeconds;
        this.lastPvP = lastPvP;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Long last = lastPvP.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < combatTagMs) {
            spawnCombatNpc(p);
        }
    }

    @EventHandler
    public void onJoinRestoreNpc(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID pid = p.getUniqueId();
        // Despawn NPC and cancel task
        UUID npcId = playerToNpc.remove(pid);
        if (npcId != null) {
            org.bukkit.entity.Entity ent = null;
            for (org.bukkit.World w : Bukkit.getWorlds()) { ent = w.getEntity(npcId); if (ent != null) break; }
            if (ent != null) ent.remove();
            npcToPlayer.remove(npcId);
            Integer taskId = npcDespawnTask.remove(pid);
            if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
            p.sendMessage(ChatColor.GRAY + "Dein Combat-Logger NPC wurde entfernt.");
        }
        // Restore inventory if stored
        CombatSnapshot snap = pendingSnapshots.remove(pid);
        if (snap != null) {
            try {
                p.getInventory().setContents(snap.contents);
                if (snap.armor != null) {
                    p.getInventory().setHelmet(snap.armor[0]);
                    p.getInventory().setChestplate(snap.armor[1]);
                    p.getInventory().setLeggings(snap.armor[2]);
                    p.getInventory().setBoots(snap.armor[3]);
                    p.getInventory().setItemInOffHand(snap.armor[4]);
                }
                p.updateInventory();
                p.sendMessage(ChatColor.GREEN + "Dein Inventar wurde nach Combat-Log wiederhergestellt.");
            } catch (Throwable ignored) {}
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity)) return;
        LivingEntity le = (LivingEntity) e.getEntity();
        try { if (!le.getPersistentDataContainer().has(combatNpcKey, PersistentDataType.BYTE)) return; } catch (Throwable t) { return; }
        UUID npcId = le.getUniqueId();
        UUID pid = npcToPlayer.remove(npcId);
        if (pid == null) return;
        playerToNpc.remove(pid);
        Integer taskId = npcDespawnTask.remove(pid);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
        CombatSnapshot snap = pendingSnapshots.remove(pid);
        e.getDrops().clear();
        if (snap != null) {
            org.bukkit.Location loc = le.getLocation();
            org.bukkit.World w = loc.getWorld();
            if (w != null) {
                for (ItemStack it : snap.contents) if (it != null && it.getType() != Material.AIR) w.dropItemNaturally(loc, it.clone());
                if (snap.armor != null) for (ItemStack it : snap.armor) if (it != null && it.getType() != Material.AIR) w.dropItemNaturally(loc, it.clone());
            }
        }
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "Combat-Logger von " + ChatColor.RED + Bukkit.getOfflinePlayer(pid).getName() + ChatColor.DARK_RED + " wurde getötet.");
    }

    private void spawnCombatNpc(Player p) {
        try {
            Location loc = p.getLocation();
            World w = loc.getWorld(); if (w == null) return;
            Zombie z = (Zombie) w.spawnEntity(loc, org.bukkit.entity.EntityType.ZOMBIE);
            z.setCustomName(ChatColor.RED + "Logger: " + ChatColor.WHITE + p.getName());
            z.setCustomNameVisible(true);
            try { z.setBaby(false); } catch (Throwable ignored) {}
            try { z.setAI(false); } catch (Throwable ignored) {}
            try { z.setRemoveWhenFarAway(false); } catch (Throwable ignored) {}
            try { z.setCanPickupItems(false); } catch (Throwable ignored) {}
            try { z.setTarget(null); } catch (Throwable ignored) {}
            double hp = Math.max(1.0, Math.min(20.0, p.getHealth()));
            try { z.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0); z.setHealth(hp); } catch (Throwable ignored) {}
            z.getPersistentDataContainer().set(combatNpcKey, PersistentDataType.BYTE, (byte)1);
            EntityEquipment eq = z.getEquipment();
            if (eq != null) {
                try {
                    eq.setHelmet(p.getInventory().getHelmet());
                    eq.setChestplate(p.getInventory().getChestplate());
                    eq.setLeggings(p.getInventory().getLeggings());
                    eq.setBoots(p.getInventory().getBoots());
                    eq.setItemInMainHand(p.getInventory().getItemInMainHand());
                    eq.setItemInOffHand(p.getInventory().getItemInOffHand());
                } catch (Throwable ignored) {}
            }
            // Snapshot and clear inventory
            ItemStack[] cont = p.getInventory().getContents();
            ItemStack[] armor = new ItemStack[]{ p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots(), p.getInventory().getItemInOffHand() };
            pendingSnapshots.put(p.getUniqueId(), new CombatSnapshot(cont, armor));
            try { p.getInventory().clear(); p.getInventory().setArmorContents(new ItemStack[4]); p.getInventory().setItemInOffHand(null); p.updateInventory(); } catch (Throwable ignored) {}
            // Track NPC
            playerToNpc.put(p.getUniqueId(), z.getUniqueId());
            npcToPlayer.put(z.getUniqueId(), p.getUniqueId());
            // Despawn after timeout
            int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                UUID npcId = playerToNpc.remove(p.getUniqueId());
                if (npcId != null) {
                    org.bukkit.entity.Entity ent2 = null;
                    for (org.bukkit.World ww : Bukkit.getWorlds()) { ent2 = ww.getEntity(npcId); if (ent2 != null) break; }
                    if (ent2 != null) ent2.remove();
                    npcToPlayer.remove(npcId);
                }
                npcDespawnTask.remove(p.getUniqueId());
            }, loggerNpcSeconds * 20L);
            npcDespawnTask.put(p.getUniqueId(), taskId);
            Bukkit.broadcastMessage(ChatColor.YELLOW + p.getName() + ChatColor.GRAY + " hat im Kampf ausgeloggt — NPC für " + loggerNpcSeconds + "s gespawnt.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Combat NPC spawn failed: " + t.getMessage());
        }
    }
}
