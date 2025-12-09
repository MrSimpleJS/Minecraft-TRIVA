package de.varo.services;

import org.bukkit.*;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodically spawns a decorated wandering Loot-Llama that drops configured loot when killed.
 */
public class LootLamaService implements org.bukkit.event.Listener {
    private final JavaPlugin plugin;
    private final long intervalMs;
    private final double radius;
    private final java.util.List<String> lootLines; // raw lines (for potential reload/debug)
    private final java.util.List<LootEntry> lootParsed; // parsed once
    private Llama active;
    private long nextAt;

    public LootLamaService(JavaPlugin plugin) {
        this.plugin = plugin;
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        boolean enabled = cfg.getBoolean("lootlama.enabled", false);
        this.intervalMs = Math.max(60_000L, cfg.getLong("lootlama.intervalMinutes", 45) * 60_000L);
        this.radius = Math.max(100.0, cfg.getDouble("lootlama.radius", 1200.0));
    this.lootLines = cfg.getStringList("lootlama.loot");
    this.lootParsed = parseLoot(lootLines);
        // Always register listener so manual spawn works even if disabled
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (enabled) start();
    }

    private void start() {
        nextAt = System.currentTimeMillis() + 5000L;
        new BukkitRunnable(){ @Override public void run(){ tick(); } }.runTaskTimer(plugin, 100L, 200L);
    }

    private void tick() {
    long now = System.currentTimeMillis(); // cheap enough; retained for consistency with intervalMs
        if (active != null && active.isDead()) { active = null; }
        if (active != null) return;
        if (now < nextAt) return;
        spawn();
        nextAt = now + intervalMs;
    }

    private void spawn() {
        World w = plugin.getServer().getWorlds().get(0);
        WorldBorder wb = w.getWorldBorder();
        double cx = wb.getCenter().getX();
        double cz = wb.getCenter().getZ();
    ThreadLocalRandom tlr = ThreadLocalRandom.current();
    double ang = tlr.nextDouble()*Math.PI*2;
    double dist = tlr.nextDouble()*radius;
        int x = (int)Math.round(cx + Math.cos(ang)*dist);
        int z = (int)Math.round(cz + Math.sin(ang)*dist);
        int y = w.getHighestBlockYAt(x,z)+1;
        org.bukkit.Location loc = new org.bukkit.Location(w, x+0.5, y, z+0.5);
        try {
            active = w.spawn(loc, Llama.class, llama -> {
                llama.setCustomName(ChatColor.GOLD + "Loot-Lama");
                llama.setCustomNameVisible(true);
                llama.setAdult();
                llama.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(40.0);
                llama.setHealth(40.0);
                // color variant optional (Paper may rename enums); ignore if not available
            });
        } catch (Throwable t) { plugin.getLogger().warning("Loot-Lama spawn fehlgeschlagen: " + t.getMessage()); return; }
        // Broadcast hint (direction + distance band)
        String dir = compassDir(cx, cz, x, z);
        String band = distanceBand(cx, cz, x, z);
        Bukkit.broadcastMessage(ChatColor.GOLD + "🦙 Loot-Lama gesichtet im " + ChatColor.YELLOW + dir + ChatColor.GRAY + " (" + ChatColor.YELLOW + band + ChatColor.GRAY + ")");
    }

    // Force spawn via command; returns true if a new llama spawned.
    public boolean forceSpawn() {
        if (active != null && !active.isDead()) return false;
        spawn();
        nextAt = System.currentTimeMillis() + intervalMs;
        return true;
    }

    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onLamaDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        if (active == null) return;
        if (!e.getEntity().getUniqueId().equals(active.getUniqueId())) return;
        // Custom loot
        e.getDrops().clear();
        for (LootEntry entry : lootParsed) {
            e.getDrops().add(entry.create());
        }
        org.bukkit.entity.Entity killer = e.getEntity().getKiller();
        String killerName = (killer instanceof Player ? ((Player) killer).getName() : "Unbekannt");
        Bukkit.broadcastMessage(ChatColor.GOLD + "🦙 Loot-Lama erlegt von " + ChatColor.WHITE + killerName + ChatColor.GRAY + "!");
        active = null;
    }

    private static final String[] DIRS = {"O", "NO", "N", "NW", "W", "SW", "S", "SO"};
    private String compassDir(double cx, double cz, int x, int z) {
        double dx = x - cx, dz = z - cz;
        double ang = Math.atan2(-dz, dx);
        double deg = Math.toDegrees(ang);
        if (deg < 0) deg += 360.0;
        int idx = (int)Math.round(deg / 45.0) & 7;
        return DIRS[idx];
    }
    private String distanceBand(double cx, double cz, int x, int z) {
        double d = Math.hypot(x - cx, z - cz);
        if (d < 300) return "nah";
        if (d < 700) return "mittel";
        if (d < 1200) return "fern";
        return "sehr fern";
    }

    // --- Helper: parse loot lines once ---
    private List<LootEntry> parseLoot(List<String> lines) {
        List<LootEntry> list = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;
            try {
                String[] sp = line.split(":");
                Material m = Material.valueOf(sp[0].trim().toUpperCase());
                if (m == Material.AIR) continue;
                int amt = (sp.length > 1 ? Integer.parseInt(sp[1].trim()) : 1);
                if (amt <= 0) continue;
                list.add(new LootEntry(m, amt));
            } catch (Exception ignored) {}
        }
        return list;
    }

    private static final class LootEntry {
        private final Material material; private final int amount;
        LootEntry(Material m, int a){ this.material=m; this.amount=a; }
        ItemStack create(){ return new ItemStack(material, amount); }
    }
}
