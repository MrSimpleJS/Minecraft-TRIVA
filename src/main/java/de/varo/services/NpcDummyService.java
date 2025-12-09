package de.varo.services;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Packet-basierte Fake-Player (NPC) mit einfacher Bewegung + Mining.
 * Debug/Test ONLY – keine Game-Logik auf diese NPCs stützen.
 */
public class NpcDummyService {
    private final JavaPlugin plugin;
    // ProtocolLib (falls vorhanden) via Reflection – kein harter Compile-Time Import
    private final Object protocol; // ProtocolManager oder null
    private final Map<UUID, NpcInfo> npcs = new HashMap<>();
    private final Map<Integer, UUID> entityIdToUuid = new HashMap<>();
    private final Random rnd = new Random();
    private int taskId = -1;

    @SuppressWarnings("unused")
    private static class NpcInfo {
        UUID id; String name; Location loc; ChatColor color; int entityId; boolean mining; int ticks; org.bukkit.util.Vector dir;
        boolean shaftDone=false; int desiredY=16; org.bukkit.util.Vector tunnelDir=null; int tunnelRemaining=0; Location motionTarget=null;
        // profile/addedInfo removed to avoid unresolved types while ProtocolLib stubbed
    }

    // Default Steve skin (value/signature can be replaced with custom). Using public known textures.
    public NpcDummyService(JavaPlugin plugin) {
        this.plugin = plugin;
        Object pm;
        try {
            Class<?> lib = Class.forName("com.comphenix.protocol.ProtocolLibrary");
            pm = lib.getMethod("getProtocolManager").invoke(null);
        } catch (Throwable t) { pm = null; }
        this.protocol = pm;
    }

    public boolean isActive() { return !npcs.isEmpty(); }

    public void clear() {
        if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
        for (NpcInfo info : npcs.values()) destroyNpc(info);
        npcs.clear(); entityIdToUuid.clear();
    }

    public void spawnFromFakeTeams(Map<String, List<UUID>> teams, Map<UUID,String> playerTeam, Map<String,ChatColor> teamColors) {
        clear();
        World w = Bukkit.getWorlds().isEmpty()?null:Bukkit.getWorlds().get(0);
        if (w == null) return;
        Location center = w.getSpawnLocation();
        int idx = 0;
    for (Map.Entry<String,List<UUID>> en : teams.entrySet()) {
            ChatColor col = teamColors.getOrDefault(en.getKey(), ChatColor.WHITE);
            for (UUID vid : en.getValue()) {
                NpcInfo info = new NpcInfo();
                info.id = vid;
                info.name = buildName(en.getKey(), vid);
                info.color = col;
                info.entityId = nextEntityId();
                double angle = (idx / 10.0) * Math.PI * 0.5;
                info.loc = center.clone().add(Math.cos(angle)*4, 0, Math.sin(angle)*4);
                info.mining = rnd.nextBoolean();
                info.dir = randomHorizontal();
                info.desiredY = pickDesiredY(center.getWorld());
                // Skin/Profile nur wenn ProtocolLib live; wir speichern keine Profile ohne Lib
        npcs.put(vid, info); entityIdToUuid.put(info.entityId, vid);
        spawnNpc(info);
                idx++;
            }
        }
        if (!npcs.isEmpty()) startTicker();
    }

    private String buildName(String team, UUID id) {
        String shortTeam = team.length()>8?team.substring(0,8):team;
        return shortTeam + "_" + Integer.toHexString(id.hashCode()).substring(0,4);
    }

    private int nextEntityId() { return (int)(Math.random()*Integer.MAX_VALUE); }

    private org.bukkit.util.Vector randomHorizontal() {
        switch (rnd.nextInt(4)) {
            case 0: return new org.bukkit.util.Vector(1,0,0);
            case 1: return new org.bukkit.util.Vector(-1,0,0);
            case 2: return new org.bukkit.util.Vector(0,0,1);
            default: return new org.bukkit.util.Vector(0,0,-1);
        }
    }

    private void spawnNpc(NpcInfo info) {
    // Ohne ProtocolLib: kein echter Packet-Spawn, Debug-NPC unsichtbar für Clients.
    // Optional: könnte hier ArmorStand-Fallback spawnen.
    }

    private void destroyNpc(NpcInfo info) {
    // Kein Packet Destroy notwendig ohne Lib
    }

    private void startTicker() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (NpcInfo info : npcs.values()) tickNpc(info);
            simulateInteractions();
        }, 40L, 1L); // start after 2s for player list sync
    }

    private void tickNpc(NpcInfo info) {
        info.ticks++;
        if (info.mining) handleMining(info); else handleRoam(info);
        // move towards target smoothly
        if (info.motionTarget != null) {
            org.bukkit.util.Vector v = info.motionTarget.clone().subtract(info.loc).toVector();
            double len = v.length();
            double step = 0.32; // speed
            if (len <= step) { info.loc = info.motionTarget; info.motionTarget = null; }
            else { v.multiply(step/len); info.loc.add(v); }
        }
    // Teleport-Paket-Ausgabe entfällt im Stub (kein ProtocolLib Hard-Link)
    }

    private int pickDesiredY(World w) {
        int base = rnd.nextBoolean()?16:-5;
        return Math.max(w.getMinHeight()+6, Math.min(w.getMaxHeight()-16, base));
    }

    private void handleRoam(NpcInfo info) {
    if (info.motionTarget == null || info.loc.distanceSquared(info.motionTarget) < 1) {
            double radius = 25 + rnd.nextDouble()*15;
            double ang = rnd.nextDouble()*Math.PI*2;
            Location nt = info.loc.clone();
            nt.add(Math.cos(ang)*radius, 0, Math.sin(ang)*radius);
            nt.setY(info.loc.getWorld().getHighestBlockYAt(nt)+1.1);
            info.motionTarget = nt;
        }
        if (info.ticks % 240 == 0 && rnd.nextDouble() < 0.25) info.mining = true;
    }

    private void handleMining(NpcInfo info) {
        Location l = info.loc;
        World w = l.getWorld();
        if (!info.shaftDone) {
            if (l.getY() > info.desiredY) {
                Block below = w.getBlockAt(l.getBlockX(), l.getBlockY()-1, l.getBlockZ());
                breakBlock(below);
                info.loc.subtract(0,1,0);
            } else { info.shaftDone = true; info.tunnelDir = randomHorizontal(); info.tunnelRemaining = 6 + rnd.nextInt(10); }
            return;
        }
        if (info.tunnelRemaining <=0 || info.tunnelDir == null) { info.tunnelDir = randomHorizontal(); info.tunnelRemaining = 4 + rnd.nextInt(12); }
        Location front = l.clone().add(info.tunnelDir);
        breakBlock(front.getBlock());
        info.motionTarget = front;
        info.tunnelRemaining--;
        if (info.ticks % 360 == 0 && rnd.nextDouble() < 0.2) { info.mining = false; info.shaftDone=false; }
    }

    private void breakBlock(Block b) {
        if (b == null) return; Material t = b.getType();
        if (!t.isSolid()) return; if (t.name().endsWith("_ORE") || t==Material.ANCIENT_DEBRIS) return;
        try { b.setType(Material.AIR,false); } catch (Throwable ignored) {}
    }


    // Simple proximity-based fake hit events
    private void simulateInteractions() {
        if (npcs.isEmpty()) return;
        if (rnd.nextDouble() > 0.15) return; // limit frequency
        for (Player pl : Bukkit.getOnlinePlayers()) {
            Location plLoc = pl.getLocation();
            for (NpcInfo info : npcs.values()) {
                if (plLoc.getWorld()!=info.loc.getWorld()) continue;
                if (plLoc.distanceSquared(info.loc) <= 4.0) { // within 2 blocks
                    if (rnd.nextDouble() < 0.4) {
                        pl.sendMessage(ChatColor.GRAY + "[Hit] Du triffst NPC " + info.color + info.name + ChatColor.GRAY + " (simuliert)");
                    }
                }
            }
        }
    }

    // legacy stub removed
}
