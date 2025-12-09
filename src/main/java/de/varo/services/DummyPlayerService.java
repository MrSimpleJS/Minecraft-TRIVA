package de.varo.services;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Spawns simple ArmorStand based dummy "players" for debug purposes and moves them around randomly.
 * They simulate movement so spectator tools / visual tests have something to look at.
 */
public class DummyPlayerService {
    private final JavaPlugin plugin;
    private final Map<UUID, ArmorStand> dummys = new HashMap<>();
    private final Map<UUID, Location> roamTargets = new HashMap<>();
    private final Random rnd = new Random();
    private BukkitTask task;

    private enum Mode { ROAM, MINING }
    private static class DummyState {
        Mode mode = Mode.ROAM;
        // Mining specifics
        boolean shaftDone = false;
        int desiredY = 16; // target Y level for tunnel (adjust for version)
        org.bukkit.util.Vector tunnelDir = null;
        int tunnelRemaining = 0;
    }
    private final Map<UUID, DummyState> states = new HashMap<>();

    private final Set<Material> breakable = EnumSet.of(
            Material.STONE, Material.DEEPSLATE, Material.TUFF, Material.GRANITE, Material.DIORITE,
            Material.ANDESITE, Material.DIRT, Material.COARSE_DIRT, Material.GRAVEL);

    public DummyPlayerService(JavaPlugin plugin) { this.plugin = plugin; }

    public boolean hasDummys() { return !dummys.isEmpty(); }

    public void clear() {
        if (task != null) { try { task.cancel(); } catch (Throwable ignored) {} task = null; }
        for (ArmorStand as : dummys.values()) {
            try { as.remove(); } catch (Throwable ignored) {}
        }
        dummys.clear();
    roamTargets.clear();
    states.clear();
    }

    /**
     * Spawn one dummy ArmorStand per fake UUID. Existing dummys are cleared first.
     */
    public void spawnForFakeTeams(Map<String, List<UUID>> teams, Map<UUID, String> playerTeam, Map<String, ChatColor> teamColors) {
        clear();
        World w = Bukkit.getWorlds().isEmpty()?null:Bukkit.getWorlds().get(0);
        if (w == null) return;
        Location center = w.getSpawnLocation();
        double spacing = 2.5; // grid spacing
        int idx = 0;
        for (Map.Entry<String,List<UUID>> en : teams.entrySet()) {
            String tName = en.getKey();
            ChatColor col = teamColors.getOrDefault(tName, ChatColor.WHITE);
            for (UUID id : en.getValue()) {
                int gx = idx % 10; int gy = idx / 10; // simple grid near spawn
                Location base = center.clone().add(gx * spacing, 0, gy * spacing);
                ArmorStand as = (ArmorStand) w.spawnEntity(base, EntityType.ARMOR_STAND);
                as.setCustomName(col + buildDummyName(playerTeam, id, tName));
                as.setCustomNameVisible(true);
                as.setMarker(false);
                as.setArms(true);
                as.setGravity(false); // we teleport manually
                as.setBasePlate(false);
                as.setSmall(false);
                as.setCanPickupItems(false);
                try { as.setInvulnerable(true); } catch (Throwable ignored) {}
                // Give them a colored leather helmet for quick team recognition
                try {
                    ItemStack helm = new ItemStack(Material.LEATHER_HELMET);
                    org.bukkit.inventory.meta.LeatherArmorMeta meta = (org.bukkit.inventory.meta.LeatherArmorMeta) helm.getItemMeta();
                    if (meta != null) {
                        java.awt.Color awt = mapToAwt(col);
                        meta.setColor(org.bukkit.Color.fromRGB(awt.getRed(), awt.getGreen(), awt.getBlue()));
                        helm.setItemMeta(meta);
                    }
                    as.getEquipment().setHelmet(helm);
                } catch (Throwable ignored) {}
                dummys.put(id, as);
                DummyState st = new DummyState();
                // 50% directly go mining
                if (rnd.nextBoolean()) st.mode = Mode.MINING;
                st.desiredY = pickDesiredY(w);
                states.put(id, st);
                idx++;
            }
        }
        if (!dummys.isEmpty()) startTicker(center);
    }

    private String buildDummyName(Map<UUID,String> playerTeam, UUID id, String fallbackTeam) {
        String team = playerTeam.getOrDefault(id, fallbackTeam);
        String shortTeam = team.length() > 8 ? team.substring(0,8) : team;
        return shortTeam + "_" + Integer.toHexString(id.hashCode()).substring(0,4);
    }

    private int pickDesiredY(World w) {
        // choose a plausible mining depth (stone/deepslate boundary around y=0 in modern versions); keep above deep lava lakes
        int y = rnd.nextBoolean() ? 16 :  -5; // two layers to diversify
        // clamp inside world min/max
        int min = w.getMinHeight()+5;
        int max = w.getMaxHeight()-10;
        return Math.max(min, Math.min(max, y));
    }

    private void startTicker(Location center) {
        World w = center.getWorld();
        if (w == null) return;
        double borderRadius = 100; // default radius around center if world border not accessible
        try {
            WorldBorder wb = w.getWorldBorder();
            if (wb != null) borderRadius = wb.getSize()/2.5; // keep well inside
        } catch (Throwable ignored) {}
        final double maxR = borderRadius;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<Map.Entry<UUID,ArmorStand>> it = dummys.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID,ArmorStand> e = it.next();
                ArmorStand as = e.getValue();
                if (as == null || as.isDead()) { it.remove(); continue; }
                DummyState st = states.get(e.getKey());
                if (st == null) { st = new DummyState(); states.put(e.getKey(), st); }
                if (st.mode == Mode.ROAM) {
                    roam(as, center, maxR, st);
                    // small chance to switch to mining
                    if (rnd.nextDouble() < 0.005) { st.mode = Mode.MINING; st.desiredY = pickDesiredY(as.getWorld()); decorateMining(as); }
                } else {
                    mine(as, st);
                    // small chance to go back to roam if surfaced
                    if (as.getLocation().getY() > st.desiredY + 40 && rnd.nextDouble() < 0.002) { st.mode = Mode.ROAM; as.setCustomName(stripPick(as.getCustomName())); }
                }
            }
            if (dummys.isEmpty()) { clear(); }
        }, 20L, 2L); // start after 1s, update every 2 ticks for smoother movement
    }

    private void roam(ArmorStand as, Location center, double maxR, DummyState st) {
        Location tgt = roamTargets.get(as.getUniqueId());
        if (tgt == null || tgt.getWorld()!=as.getWorld() || as.getLocation().distanceSquared(tgt) < 1.0) {
            double rx = (rnd.nextDouble()*2-1) * maxR;
            double rz = (rnd.nextDouble()*2-1) * maxR;
            double y = center.getY();
            try { y = as.getWorld().getHighestBlockYAt((int)Math.round(center.getX()+rx), (int)Math.round(center.getZ()+rz)) + 0.1; } catch (Throwable ignored) {}
            tgt = new Location(as.getWorld(), center.getX()+rx, y, center.getZ()+rz);
            roamTargets.put(as.getUniqueId(), tgt);
        }
        moveTowards(as, tgt, 0.5);
    }

    private void mine(ArmorStand as, DummyState st) {
        Location loc = as.getLocation();
        World w = loc.getWorld();
        if (w == null) return;
        // decorate mining name if not done
        if (!as.getCustomName().contains("⛏")) decorateMining(as);
        if (!st.shaftDone) {
            // dig vertical shaft until desiredY
            if (loc.getY() - 1 > st.desiredY) {
                Location below = loc.clone().add(0, -1, 0);
                breakBlock(below.getBlock());
                Location next = loc.clone().add(0, -0.9, 0);
                as.teleport(next);
            } else {
                st.shaftDone = true;
                st.tunnelDir = pickHorizontalDir();
                st.tunnelRemaining = 6 + rnd.nextInt(10);
            }
            return;
        }
        // horizontal tunneling
        if (st.tunnelRemaining <= 0 || st.tunnelDir == null) {
            st.tunnelDir = pickHorizontalDir();
            st.tunnelRemaining = 4 + rnd.nextInt(12);
            // occasional small vertical variation
            if (rnd.nextDouble() < 0.15) {
                int dy = rnd.nextBoolean() ? 1 : -1;
                int newY = (int)Math.round(loc.getY()) + dy;
                if (newY > st.desiredY - 6 && newY < st.desiredY + 6) {
                    Location step = loc.clone().add(0, dy, 0);
                    breakBlock(step.getBlock());
                    as.teleport(step);
                }
            }
        }
        // block in front
        Location front = loc.clone().add(st.tunnelDir.clone().normalize());
        breakBlock(front.getBlock());
        moveTowards(as, front, 0.45);
        st.tunnelRemaining--;
    }

    private org.bukkit.util.Vector pickHorizontalDir() {
        switch (rnd.nextInt(4)) {
            case 0: return new org.bukkit.util.Vector(1,0,0);
            case 1: return new org.bukkit.util.Vector(-1,0,0);
            case 2: return new org.bukkit.util.Vector(0,0,1);
            default: return new org.bukkit.util.Vector(0,0,-1);
        }
    }

    private void moveTowards(ArmorStand as, Location tgt, double speed) {
        Location cur = as.getLocation();
        org.bukkit.util.Vector dir = tgt.clone().subtract(cur).toVector();
        double len = dir.length();
        if (len > 0.001) {
            double dist = Math.min(speed, len);
            dir.multiply(dist / len);
            Location next = cur.add(dir);
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
            next.setYaw(yaw);
            as.teleport(next);
        }
    }

    private void breakBlock(Block b) {
        if (b == null) return;
        Material t = b.getType();
        if (!breakable.contains(t)) return;
        // tiny chance to leave ores untouched (makes tunnels realistic)
        try {
            try {
                b.getWorld().spawnParticle(Particle.BLOCK, b.getLocation().add(0.5,0.5,0.5), 8, 0.3,0.3,0.3, b.getBlockData());
            } catch (Throwable ignored2) {}
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_STONE_BREAK, 0.4f, 0.9f + rnd.nextFloat()*0.2f);
        } catch (Throwable ignored) {}
        // Actually remove block
        try { b.setType(Material.AIR, false); } catch (Throwable ignored) {}
    }

    private void decorateMining(ArmorStand as) {
        try { as.setCustomName(as.getCustomName() + ChatColor.GRAY + " ⛏"); } catch (Throwable ignored) {}
        try { if (as.getEquipment()!=null && as.getEquipment().getItemInMainHand()==null) as.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE)); } catch (Throwable ignored) {}
    }

    private String stripPick(String name) {
        if (name == null) return null;
        return name.replace(" ⛏"," ");
    }

    // ==== Public helper API ====
    public List<ArmorStand> getDummysSnapshot() { return new ArrayList<>(dummys.values()); }

    public ArmorStand findDummyByNamePart(String part) {
        if (part == null || part.isEmpty()) return null;
        String low = ChatColor.stripColor(part).toLowerCase(Locale.ROOT);
        for (ArmorStand as : dummys.values()) {
            String n = ChatColor.stripColor(as.getCustomName()==null?"":as.getCustomName()).toLowerCase(Locale.ROOT);
            if (n.contains(low)) return as;
        }
        return null;
    }

    public void spectate(ArmorStand as, org.bukkit.entity.Player viewer) {
        if (as == null || viewer == null) return;
        try {
            if (viewer.getGameMode() != GameMode.SPECTATOR) viewer.setGameMode(GameMode.SPECTATOR);
            viewer.setSpectatorTarget(as);
            viewer.sendMessage(ChatColor.LIGHT_PURPLE + "Du beobachtest jetzt Dummy: " + ChatColor.WHITE + ChatColor.stripColor(as.getCustomName()));
        } catch (Throwable ignored) {}
    }

    private java.awt.Color mapToAwt(ChatColor c) {
        switch (c) {
            case RED: return new java.awt.Color(0xFF5555);
            case BLUE: return new java.awt.Color(0x5555FF);
            case GREEN: return new java.awt.Color(0x55FF55);
            case YELLOW: return new java.awt.Color(0xFFFF55);
            case AQUA: return new java.awt.Color(0x55FFFF);
            case LIGHT_PURPLE: return new java.awt.Color(0xFF55FF);
            case GOLD: return new java.awt.Color(0xFFAA00);
            case DARK_AQUA: return new java.awt.Color(0x00AAAA);
            case DARK_GREEN: return new java.awt.Color(0x00AA00);
            case DARK_PURPLE: return new java.awt.Color(0xAA00AA);
            case DARK_RED: return new java.awt.Color(0xAA0000);
            case WHITE: return new java.awt.Color(0xFFFFFF);
            case DARK_BLUE: return new java.awt.Color(0x0000AA);
            default: return new java.awt.Color(0xAAAAAA);
        }
    }
}
