package de.varo.features;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.function.Supplier;

public class SupplyDropService {
    private final JavaPlugin plugin;
    private final Supplier<List<String>> lootSupplier;
    private final java.util.function.Predicate<java.util.UUID> isStreamer; // kept for possible future toggles
    private final Supplier<Boolean> maskCoordsForStreamers; // kept for possible future toggles
    // Track last drop coords
    private volatile org.bukkit.Location lastDrop;
    // Masking window removed in hint-mode; fields not used anymore

    public SupplyDropService(JavaPlugin plugin,
                             Supplier<List<String>> lootSupplier,
                             java.util.function.Predicate<java.util.UUID> isStreamer,
                             Supplier<Boolean> maskCoordsForStreamers) {
        this.plugin = plugin;
        this.lootSupplier = lootSupplier;
        this.isStreamer = isStreamer;
        this.maskCoordsForStreamers = maskCoordsForStreamers;
    }

    public void startScheduled(int intervalMinutes) {
        new BukkitRunnable() {
            @Override public void run() { spawnNow(); }
        }.runTaskTimer(plugin, 20L * 60 * intervalMinutes, 20L * 60 * intervalMinutes);
    }

    public void spawnNow() {
        World world = plugin.getServer().getWorlds().get(0);
        WorldBorder wb = world.getWorldBorder();

        double centerX = wb.getCenter().getX();
        double centerZ = wb.getCenter().getZ();
        double radius = Math.max(1.0, wb.getSize() / 2.0 - 10);

        Random rand = new Random();
        double angle = rand.nextDouble() * 2 * Math.PI;
        double r = rand.nextDouble() * radius;

        int x = (int) Math.round(centerX + r * Math.cos(angle));
        int z = (int) Math.round(centerZ + r * Math.sin(angle));
        int y = world.getHighestBlockYAt(x, z) + 1;

    Location dropLoc = new Location(world, x, y, z);
    lastDrop = dropLoc.clone();
    Block chestBlock = dropLoc.getBlock();
    chestBlock.setType(Material.CHEST);

    boolean isMega = false;
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        try { isMega = cfg.getBoolean("supplydrop.mega.enabled", true) && (new java.util.Random().nextDouble() < cfg.getDouble("supplydrop.mega.chance", 0.1)); } catch (Throwable ignored) {}

        if (chestBlock.getState() instanceof Chest chest) {
            // Weighted loot per phase: fall back to simple list if provided directly
            List<String> loot = lootSupplier.get();
            // Mega override if configured
            List<String> megaLoot = null;
            if (isMega) {
                java.util.List<String> ml = cfg.getStringList("supplydrop.mega.loot");
                if (ml != null && !ml.isEmpty()) megaLoot = ml;
            }
            if (megaLoot != null && !megaLoot.isEmpty()) {
                for (String entry : megaLoot) addLootLine(chest, entry);
            } else if (loot != null && !loot.isEmpty()) {
                for (String entry : loot) addLootLine(chest, entry);
            } else {
                // Try tiered loots from config: supplydrop.tiers.phaseN: [item:amount@weight]
                int phase = computePhase();
                java.util.List<String> lines = cfg.getStringList("supplydrop.tiers.phase" + phase);
                if (lines == null || lines.isEmpty()) lines = cfg.getStringList("supplydrop.tiers.default");
                if (lines != null && !lines.isEmpty())
                    fillWeighted(chest, lines, (isMega ? 12 : 6) + plugin.getServer().getOnlinePlayers().size() / (isMega ? 6 : 10));
            }
        }

        // Beacon beam (glass atop beacon for sky beam)
        try {
            world.getBlockAt(x, y - 1, z).setType(Material.BEACON);
            world.getBlockAt(x, y, z).setType(Material.CHEST); // keep chest
            // glass around beacon for effect (optional)
            for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) if (dx!=0 || dz!=0) world.getBlockAt(x+dx, y, z+dz).setType(Material.GLASS);
        } catch (Throwable ignored) {}

        // Particle column for visibility (short burst) — color differs for mega
        try {
            final Location base = new Location(world, x + 0.5, y + 1, z + 0.5);
            final boolean megaFx = isMega;
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks++ > 60) { cancel(); return; }
                    for (double dy = 0; dy <= 30; dy += 1.5) {
                        Location at = base.clone().add(0, dy, 0);
                        org.bukkit.Color col = megaFx ? org.bukkit.Color.fromRGB(255, 128, 0) : org.bukkit.Color.fromRGB(160,32,240);
                        world.spawnParticle(Particle.DUST, at, megaFx ? 8 : 4, 0.2, 0.2, 0.2, 0,
                            new org.bukkit.Particle.DustOptions(col, megaFx ? 1.8f : 1.4f));
                    }
                }
            }.runTaskTimer(plugin, 0L, 10L);
        } catch (Throwable ignored) {}

        // Streamerfreundlicher Hinweis: nur Richtung + Distanz-Band öffentlich, exakte Koords nur für GMs/OPs
        // Keep fields referenced to avoid static analysis warnings
        if (maskCoordsForStreamers != null && isStreamer != null) {
            // no-op reference; masking handled earlier design
        }
        String tag = isMega ? (ChatColor.GOLD + "☄ MEGA DROP ☄ ") : (ChatColor.LIGHT_PURPLE + "⚡ Supply Drop ");
        String area = approxArea(x, z);
        Bukkit.broadcastMessage(tag + ChatColor.GRAY + "im Umkreis von " + ChatColor.YELLOW + area + ChatColor.GRAY + " (ca.)");
        for (Player pl : plugin.getServer().getOnlinePlayers()) {
            try { pl.sendTitle((isMega ? ChatColor.GOLD + "MEGA DROP!" : ChatColor.LIGHT_PURPLE + "Supply Drop!"), ChatColor.YELLOW + area + ChatColor.GRAY + " (ca.)", 10, 40, 10); } catch (Throwable ignored) {}
        }
        // Exact coords and [TP] only to GMs/OPs + gestorbene (Spectator) Streamer
        try {
            for (Player pl : plugin.getServer().getOnlinePlayers()) {
                boolean allowed = false;
                try {
                    if (plugin instanceof de.varo.VaroPlugin vp) {
                        allowed = vp.isGameMaster(pl) || pl.isOp() || (vp.isStreamer(pl) && pl.getGameMode()==GameMode.SPECTATOR);
                    }
                } catch (Throwable ignored) {}
                if (!allowed) continue;
                net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent((isMega ? net.md_5.bungee.api.ChatColor.GOLD + "☄ MEGA DROP ☄ " : net.md_5.bungee.api.ChatColor.LIGHT_PURPLE + "⚡ Drop ") + net.md_5.bungee.api.ChatColor.RESET + "@ " + x + "," + y + "," + z + " ");
                net.md_5.bungee.api.chat.TextComponent tp = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.ChatColor.RED + "[TP]");
                tp.setUnderlined(true);
                tp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                        String.format(java.util.Locale.ROOT, "/poitp atw %s %d %d %d", world.getName(), x, y, z)));
                pl.spigot().sendMessage(new net.md_5.bungee.api.chat.TextComponent[]{ msg, tp });
            }
        } catch (Throwable ignored) {}
    }

    // Command helper: reveal last drop coords to player (GM/OP only)
    public void showLastCoords(Player p) {
        Location d = lastDrop;
        if (d == null) { p.sendMessage(ChatColor.RED + "Aktuell keine Drop-Koordinaten gespeichert."); return; }
        boolean gm = (p.isOp());
        try { if (!gm && (plugin instanceof de.varo.VaroPlugin)) gm = ((de.varo.VaroPlugin) plugin).isGameMaster(p); } catch (Throwable ignored) {}
        if (!gm) { p.sendMessage(ChatColor.RED + "Nur für GMs."); return; }
        p.sendMessage(ChatColor.LIGHT_PURPLE + "Drop: " + ChatColor.YELLOW + d.getWorld().getName() + ChatColor.GRAY + " @ " + ChatColor.WHITE + d.getBlockX() + "," + d.getBlockY() + "," + d.getBlockZ());
    }

    public Location getLastDrop() { return lastDrop == null ? null : lastDrop.clone(); }

    // (alte Methoden compassDir/distanceBand entfernt)

    // Approximate area string: round to 100er Raster und kennzeichne Unschaerfe
    private String approxArea(int x, int z) {
        int rx = (int) Math.floor(x / 100.0) * 100;
        int rz = (int) Math.floor(z / 100.0) * 100;
        return rx + "±100, " + rz + "±100";
    }

    private void addLootLine(Chest chest, String line) {
        try {
            String[] split = line.split(":");
            Material mat = Material.valueOf(split[0]);
            int amount = (split.length > 1) ? Integer.parseInt(split[1]) : 1;
            if (mat != Material.AIR && amount > 0) chest.getBlockInventory().addItem(new ItemStack(mat, amount));
        } catch (Exception ignored) {}
    }

    private int computePhase() {
        // Basic phase: shrink count or time since start from state is unknown here; use world border size as proxy (bigger -> early)
        World world = plugin.getServer().getWorlds().get(0);
        double size = world.getWorldBorder().getSize();
        if (size > 2000) return 1;
        if (size > 1000) return 2;
        if (size > 500) return 3;
        return 4;
    }

    private void fillWeighted(Chest chest, List<String> lines, int rolls) {
        // Format: MATERIAL:amount@weight, e.g., DIAMOND:2@3
        class Entry { Material m; int amt; int w; }
        List<Entry> entries = new ArrayList<>();
        int total = 0;
        for (String s : lines) {
            try {
                String[] parts = s.split("@");
                String item = parts[0];
                int w = parts.length>1 ? Integer.parseInt(parts[1]) : 1;
                String[] sp = item.split(":");
                Entry e = new Entry();
                e.m = Material.valueOf(sp[0]);
                e.amt = (sp.length>1) ? Integer.parseInt(sp[1]) : 1;
                e.w = Math.max(1, w);
                if (e.m != Material.AIR && e.amt>0) { entries.add(e); total += e.w; }
            } catch (Exception ignored) {}
        }
        if (entries.isEmpty()) return;
        Random r = new Random();
        for (int i=0;i<rolls;i++) {
            int pick = r.nextInt(total) + 1;
            int cur = 0;
            for (Entry e : entries) { cur += e.w; if (pick <= cur) { chest.getBlockInventory().addItem(new ItemStack(e.m, e.amt)); break; } }
        }
    }
}
