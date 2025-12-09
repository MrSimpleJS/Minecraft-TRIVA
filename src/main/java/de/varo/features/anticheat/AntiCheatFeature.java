package de.varo.features.anticheat;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AntiCheatFeature implements Listener {
    private final Set<UUID> gameMasters;
    private final Set<UUID> streamers;
    private final Consumer<String> log;
    private final Supplier<File> dataFolderSupplier;

    // Movement/combat tracking
    private final Map<UUID, org.bukkit.Location> lastLoc = new HashMap<>();
    private final Map<UUID, Long> lastLocAt = new HashMap<>();
    private final Map<UUID, Float> lastYaw = new HashMap<>();
    private final Map<UUID, Float> lastPitch = new HashMap<>();
    private final Map<UUID, Integer> airborneTicks = new HashMap<>();
    private final Map<UUID, Deque<Long>> attackTimes = new HashMap<>();
    private final Map<UUID, Deque<UUID>> recentVictims = new HashMap<>();
    private final Map<UUID, Integer> violations = new HashMap<>();
    private final Map<UUID, Deque<FlagEntry>> recentFlags = new HashMap<>();
    private final Map<UUID, Deque<LocationSample>> recentPositions = new HashMap<>();
    private final Map<UUID, Long> lastGmAlertAt = new HashMap<>();
    private final Map<UUID, Integer> suppressedAlerts = new HashMap<>();
    private final Map<UUID, Deque<Double>> recentRangedAngles = new HashMap<>();
    private final Map<UUID, Deque<Long>> placeTimes = new HashMap<>();
    // Extra heuristics
    private final Map<UUID, Deque<Long>> fastBreakTimes = new HashMap<>(); // timestamps of recent non-instant block breaks
    private final Map<UUID, Long> lastBlockBreakAt = new HashMap<>();
    private final Map<UUID, Double> lastAirStartY = new HashMap<>();
    private final Map<UUID, Long> lastFallDamageAt = new HashMap<>();
    private final Map<UUID, Integer> waterSurfaceTicks = new HashMap<>();
    // Fly reset support
    private final Map<UUID, org.bukkit.Location> lastGroundLocation = new HashMap<>();
    private final Map<UUID, Long> lastFlyResetAt = new HashMap<>();

    // Configurable mining analytics thresholds
    private int windowMinutes;
    private long windowMs;
    private int diamondPerBlocks;
    private double diamondMaxRatio;
    private Double diamondMaxRatioOverworld = null; // optional per-world override
    private Double diamondMaxRatioNether = null;    // optional per-world override
    private int diamondFastFind;
    private int debrisPerBlocks;
    private double debrisMaxRatio;
    private Double debrisMaxRatioOverworld = null;  // optional per-world override
    private Double debrisMaxRatioNether = null;     // optional per-world override
    private int debrisFastFind;
    private int branchWithinSteps;
    private int branchWarnCount;
    // Burst detection (per 1 minute)
    private int oreBurstPerMinute = 6; // default; configurable via /acset ore.burstPerMinute <n>
    private Integer oreBurstPerMinuteOverworld = null; // optional override via /acset ore.burstPerMinute.overworld <n>
    private Integer oreBurstPerMinuteNether = null;    // optional override via /acset ore.burstPerMinute.nether <n>
    // Audit CSV toggle
    private boolean auditEnabled = true; // configurable via /acset audit.enabled 0|1
    // TPS warning monitor
    private boolean tpsWarnEnabled = true; // configurable via /acset tps.warn.enabled 0|1
    private int tpsWarnYellow = 15; // warn yellow if below
    private int tpsWarnRed = 10;    // warn red if below
    private int tpsCheckIntervalTicks = 100; // every 5s
    private long lastTpsCheckNs = 0L;
    private long lastTpsWarnAt = 0L;
    private int lastTpsSeverity = 0; // 0 none, 1 yellow, 2 red

    // Ore tracking for Xray and fast-find warnings
    private static class OreStats { Deque<Long> diamondTimes = new ArrayDeque<>(); Deque<Long> debrisTimes = new ArrayDeque<>(); }
    private final Map<UUID, OreStats> oreStats = new HashMap<>();

    // Mining analytics: track mined stone-like blocks and branch-to-ore patterns (sliding window)
    private static class MiningStats {
        Deque<Long> stoneTimes = new ArrayDeque<>();
        Deque<Long> netherrackTimes = new ArrayDeque<>();
        Character lastAxis = null; // 'X' or 'Z'
        Character branchAxis = null; // active branch axis
        int branchSteps = 0; // steps inside current branch
        Deque<Long> branchToOreTimes = new ArrayDeque<>();
        org.bukkit.Location lastBreak = null;
    }

    private void sendTpsWarning(double tps, int severity) {
        ChatColor color = severity >= 2 ? ChatColor.RED : ChatColor.YELLOW;
        String prefix = severity >= 2 ? "TPS KRITISCH" : "TPS WARNUNG";
        String msg = color + prefix + ChatColor.GRAY + ": Server-TPS = " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.1f", tps);
        for (Player gm : Bukkit.getOnlinePlayers()) {
            if (gameMasters.contains(gm.getUniqueId()) || gm.isOp()) {
                gm.sendMessage(msg);
            }
        }
        if (log != null) log.accept("TPS Warn (" + prefix + "): " + String.format(java.util.Locale.ROOT, "%.2f", tps));
    }

    // Live config setters
    public synchronized boolean setThreshold(String key, double value) {
        switch (key.toLowerCase(java.util.Locale.ROOT)) {
            case "windowminutes": this.windowMinutes = (int)Math.max(1, Math.round(value)); this.windowMs = this.windowMinutes * 60_000L; return true;
            case "diamond.perblocks": this.diamondPerBlocks = (int)Math.max(1, Math.round(value)); return true;
            case "diamond.maxperwindowratio": this.diamondMaxRatio = value; return true;
            case "diamond.maxperwindowratio.overworld": this.diamondMaxRatioOverworld = value; return true;
            case "diamond.maxperwindowratio.nether": this.diamondMaxRatioNether = value; return true;
            case "diamond.fastfindperwindow": this.diamondFastFind = (int)Math.max(1, Math.round(value)); return true;
            case "debris.perblocks": this.debrisPerBlocks = (int)Math.max(1, Math.round(value)); return true;
            case "debris.maxperwindowratio": this.debrisMaxRatio = value; return true;
            case "debris.maxperwindowratio.overworld": this.debrisMaxRatioOverworld = value; return true;
            case "debris.maxperwindowratio.nether": this.debrisMaxRatioNether = value; return true;
            case "debris.fastfindperwindow": this.debrisFastFind = (int)Math.max(1, Math.round(value)); return true;
            case "branch.withinsteps": this.branchWithinSteps = (int)Math.max(1, Math.round(value)); return true;
            case "branch.warncountinwindow": this.branchWarnCount = (int)Math.max(1, Math.round(value)); return true;
            case "ore.burstperminute": this.oreBurstPerMinute = (int)Math.max(1, Math.round(value)); return true;
            case "ore.burstperminute.overworld": this.oreBurstPerMinuteOverworld = (int)Math.max(1, Math.round(value)); return true;
            case "ore.burstperminute.nether": this.oreBurstPerMinuteNether = (int)Math.max(1, Math.round(value)); return true;
            case "audit.enabled": this.auditEnabled = (int)Math.round(value) != 0; return true;
            case "audit": this.auditEnabled = (int)Math.round(value) != 0; return true;
            case "tps.warn.enabled": this.tpsWarnEnabled = (int)Math.round(value) != 0; return true;
            case "tps.warn.yellow": this.tpsWarnYellow = (int)Math.max(1, Math.round(value)); return true;
            case "tps.warn.red": this.tpsWarnRed = (int)Math.max(1, Math.round(value)); return true;
            case "tps.warn.intervalticks": this.tpsCheckIntervalTicks = (int)Math.max(1, Math.round(value)); return true;
            default: return false;
        }
    }

    public Map<String, Object> dumpThresholds() {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("windowMinutes", windowMinutes);
        m.put("diamond.perBlocks", diamondPerBlocks);
        m.put("diamond.maxPerWindowRatio", diamondMaxRatio);
    if (diamondMaxRatioOverworld != null) m.put("diamond.maxPerWindowRatio.overworld", diamondMaxRatioOverworld);
    if (diamondMaxRatioNether != null) m.put("diamond.maxPerWindowRatio.nether", diamondMaxRatioNether);
        m.put("diamond.fastFindPerWindow", diamondFastFind);
        m.put("debris.perBlocks", debrisPerBlocks);
        m.put("debris.maxPerWindowRatio", debrisMaxRatio);
    if (debrisMaxRatioOverworld != null) m.put("debris.maxPerWindowRatio.overworld", debrisMaxRatioOverworld);
    if (debrisMaxRatioNether != null) m.put("debris.maxPerWindowRatio.nether", debrisMaxRatioNether);
        m.put("debris.fastFindPerWindow", debrisFastFind);
        m.put("branch.withinSteps", branchWithinSteps);
        m.put("branch.warnCountInWindow", branchWarnCount);
    m.put("ore.burstPerMinute", oreBurstPerMinute);
    if (oreBurstPerMinuteOverworld != null) m.put("ore.burstPerMinute.overworld", oreBurstPerMinuteOverworld);
    if (oreBurstPerMinuteNether != null) m.put("ore.burstPerMinute.nether", oreBurstPerMinuteNether);
    m.put("audit.enabled", auditEnabled);
    m.put("tps.warn.enabled", tpsWarnEnabled);
    m.put("tps.warn.yellow", tpsWarnYellow);
    m.put("tps.warn.red", tpsWarnRed);
    m.put("tps.warn.intervalTicks", tpsCheckIntervalTicks);
        return m;
    }
    private final Map<UUID, MiningStats> miningStats = new HashMap<>();

    // Snapshot DTO for GM command
    public static class MiningSnapshot {
        public final int stone;
        public final int netherrack;
        public final int diamonds;
        public final int debris;
        public final int branchHits;
        public final double diamondPerNorm;
        public final double debrisPerNorm;

        public MiningSnapshot(int stone, int netherrack, int diamonds, int debris, int branchHits,
                              double diamondPerNorm, double debrisPerNorm) {
            this.stone = stone; this.netherrack = netherrack; this.diamonds = diamonds; this.debris = debris;
            this.branchHits = branchHits; this.diamondPerNorm = diamondPerNorm; this.debrisPerNorm = debrisPerNorm;
        }
    }

    public AntiCheatFeature(Set<UUID> gameMasters, Set<UUID> streamers, Consumer<String> log,
                            Supplier<File> dataFolderSupplier,
                            int windowMinutes,
                            int diamondPerBlocks, double diamondMaxRatio, int diamondFastFind,
                            int debrisPerBlocks, double debrisMaxRatio, int debrisFastFind,
                            int branchWithinSteps, int branchWarnCount) {
        this.gameMasters = gameMasters;
        this.streamers = streamers;
        this.log = log;
        this.dataFolderSupplier = dataFolderSupplier;
        this.windowMinutes = windowMinutes;
        this.windowMs = Math.max(1, windowMinutes) * 60_000L;
        this.diamondPerBlocks = Math.max(1, diamondPerBlocks);
        this.diamondMaxRatio = diamondMaxRatio;
        this.diamondFastFind = Math.max(1, diamondFastFind);
        this.debrisPerBlocks = Math.max(1, debrisPerBlocks);
        this.debrisMaxRatio = debrisMaxRatio;
        this.debrisFastFind = Math.max(1, debrisFastFind);
        this.branchWithinSteps = Math.max(1, branchWithinSteps);
        this.branchWarnCount = Math.max(1, branchWarnCount);
        // Start TPS monitor task
        try {
            org.bukkit.plugin.Plugin pl = org.bukkit.Bukkit.getPluginManager().getPlugin("TRIVA");
            if (pl != null && pl.isEnabled()) {
                org.bukkit.Bukkit.getScheduler().runTaskTimer(pl, () -> {
                    if (!tpsWarnEnabled) { lastTpsCheckNs = System.nanoTime(); return; }
                    long nowNs = System.nanoTime();
                    if (lastTpsCheckNs != 0L) {
                        long dtNs = nowNs - lastTpsCheckNs;
                        double dtSec = Math.max(0.000001, dtNs / 1_000_000_000.0);
                        double tps = Math.min(20.0, tpsCheckIntervalTicks / dtSec);
                        int sev = (tps < tpsWarnRed) ? 2 : (tps < tpsWarnYellow ? 1 : 0);
                        long now = System.currentTimeMillis();
                        if (sev > 0) {
                            boolean shouldNotify = sev > lastTpsSeverity || (now - lastTpsWarnAt) > 30_000L;
                            if (shouldNotify) {
                                lastTpsWarnAt = now;
                                lastTpsSeverity = sev;
                                sendTpsWarning(tps, sev);
                            }
                        } else {
                            lastTpsSeverity = 0;
                        }
                    }
                    lastTpsCheckNs = nowNs;
                }, tpsCheckIntervalTicks, tpsCheckIntervalTicks);
            }
        } catch (Throwable ignored) {}
    }
    public MiningSnapshot snapshotMining(UUID id) {
        long now = System.currentTimeMillis();
        MiningStats ms = miningStats.get(id);
        OreStats os = oreStats.get(id);
        if (ms == null && os == null) return null;
        int stoneCnt = 0, netCnt = 0, diaCnt = 0, debCnt = 0, branchCnt = 0;
        if (ms != null) {
            for (Long t : ms.stoneTimes) if (now - t <= windowMs) stoneCnt++;
            for (Long t : ms.branchToOreTimes) if (now - t <= windowMs) branchCnt++;
        }
        if (os != null) {
            for (Long t : os.diamondTimes) if (now - t <= windowMs) diaCnt++;
            for (Long t : os.debrisTimes) if (now - t <= windowMs) debCnt++;
        }
        double diaPer = diaCnt / Math.max(1.0, stoneCnt / (double) diamondPerBlocks);
        double debPer = debCnt / Math.max(1.0, netCnt / (double) debrisPerBlocks);
        return new MiningSnapshot(stoneCnt, netCnt, diaCnt, debCnt, branchCnt, diaPer, debPer);
    }
    public int getViolations(UUID id) { return violations.getOrDefault(id, 0); }

    public List<String> getRecentFlags(UUID id, int limit) {
        Deque<FlagEntry> dq = recentFlags.get(id);
        if (dq == null || dq.isEmpty()) return java.util.Collections.emptyList();
        List<String> out = new ArrayList<>();
        java.util.Iterator<FlagEntry> it = dq.descendingIterator();
        int c = 0; long now = System.currentTimeMillis();
        while (it.hasNext() && c < limit) {
            FlagEntry fe = it.next();
            long age = Math.max(0L, now - fe.ts);
            String prefix = ("XRAY".equals(fe.category) ? "[XRAY] " : "");
            out.add("" + (age/1000) + "s: " + prefix + fe.reason);
            c++;
        }
        return out;
    }

    // Detailed flag info (for GUIs)
    public static class FlagInfo {
        public final long ts; public final String category; public final String reason;
        public FlagInfo(long ts, String category, String reason) { this.ts = ts; this.category = category; this.reason = reason; }
    }

    public List<FlagInfo> getRecentFlagInfo(UUID id, int limit) {
        Deque<FlagEntry> dq = recentFlags.get(id);
        if (dq == null || dq.isEmpty()) return java.util.Collections.emptyList();
        List<FlagInfo> out = new ArrayList<>();
        java.util.Iterator<FlagEntry> it = dq.descendingIterator();
        int c = 0;
        while (it.hasNext() && c < limit) {
            FlagEntry fe = it.next();
            out.add(new FlagInfo(fe.ts, fe.category, fe.reason));
            c++;
        }
        return out;
    }

    public boolean saveClip(UUID id) {
        Deque<LocationSample> dq = recentPositions.get(id);
        if (dq == null || dq.isEmpty()) return false;
        try {
            File data = dataFolderSupplier.get();
            File clipsDir = new File(data, "clips");
            if (!clipsDir.exists()) clipsDir.mkdirs();
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = (op != null && op.getName() != null) ? op.getName() : id.toString().substring(0,8);
            String fname = name + "_" + System.currentTimeMillis() + ".yml";
            File f = new File(clipsDir, fname);
            YamlConfiguration y = new YamlConfiguration();
            List<Map<String,Object>> list = new ArrayList<>();
            for (LocationSample s : dq) list.add(s.toMap());
            y.set("player", name);
            y.set("uuid", id.toString());
            y.set("samples", list);
            y.save(f);
            if (log != null) log.accept("Clip gespeichert: " + f.getName());
            return true;
        } catch (Exception ex) {
            if (log != null) log.accept("Clip speichern fehlgeschlagen: " + ex.getMessage());
            return false;
        }
    }

    private boolean exempt(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR
                || gameMasters.contains(p.getUniqueId()) || streamers.contains(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (exempt(p)) return;
        if (p.isFlying() || p.isGliding() || p.isInsideVehicle()) return;
        if (p.getLocation().getBlock().isLiquid()) return;

        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        org.bukkit.Location prev = lastLoc.get(id);
        Long prevAt = lastLocAt.get(id);
        lastLoc.put(id, e.getTo());
        lastLocAt.put(id, now);
        if (prev == null || prevAt == null) return;

        double dx = e.getTo().getX() - prev.getX();
        double dz = e.getTo().getZ() - prev.getZ();
        double dist = Math.hypot(dx, dz);
        double dt = Math.max(1, now - prevAt) / 1000.0; // seconds
        // Guard against jittery, ultra-short samples which cause false spikes
        if (dt < 0.12) {
            return; // wait for a larger window
        }
    double bps = dist / dt; // blocks per second

    // Sample recent positions for replay/clip
    Deque<LocationSample> samples = recentPositions.computeIfAbsent(id, k -> new ArrayDeque<>());
    samples.addLast(LocationSample.of(now, e.getTo()));
    while (samples.size() > 120) samples.removeFirst(); // ~last few seconds

        // Base sprint ~5.6 bps. Allow more headroom to avoid false flags.
        double allow = 7.2; // baseline generous for sprint+jump variance
        PotionEffect spd = p.getPotionEffect(PotionEffectType.SPEED);
        if (spd != null) allow *= (1.0 + 0.2 * (spd.getAmplifier() + 1));
        allow += 1.0; // lag tolerance
        // Terrain allowances: ice and downhill give big boosts
        try {
            org.bukkit.block.Block under = p.getLocation().clone().subtract(0, 1, 0).getBlock();
            org.bukkit.Material m = under.getType();
            if (m == org.bukkit.Material.ICE || m == org.bukkit.Material.PACKED_ICE || m == org.bukkit.Material.BLUE_ICE || m == org.bukkit.Material.FROSTED_ICE) {
                allow += 2.5;
            }
            // Soul Speed on soul blocks
            if ((m == org.bukkit.Material.SOUL_SAND || m == org.bukkit.Material.SOUL_SOIL)) {
                org.bukkit.inventory.ItemStack boots = p.getInventory().getBoots();
                if (boots != null && boots.hasItemMeta()) {
                    try { if (boots.getItemMeta().hasEnchant(Enchantment.SOUL_SPEED)) allow += 1.0; } catch (Throwable ignored) {}
                }
            }
            // Downhill running boost
            double vy = e.getTo().getY() - prev.getY();
            if (vy < -0.30) allow += 1.5;
        } catch (Throwable ignored) {}
        // Knockback or other velocity boosts
        try { if (p.getVelocity().length() > 0.7) allow += 1.5; } catch (Throwable ignored) {}

        if (bps > allow) {
            flag(p, "Speed (" + String.format(Locale.ROOT, "%.2f", bps) + " b/s > " + String.format(Locale.ROOT, "%.2f", allow) + ")");
        }

        // NoSlow (blocking but still too fast)
        try {
            if (p.isBlocking() && dt >= 0.20 && bps > 5.0) {
                flag(p, "NoSlow (Blocking)");
            }
        } catch (Throwable ignored) {}

        // Elytra extreme boost (very conservative)
        try {
            if (p.isGliding() && bps > 80.0) {
                flag(p, "ElytraBoost (extrem)");
            }
        } catch (Throwable ignored) {}

        // Fly/hover detection
        boolean inLiquid = p.getLocation().getBlock().isLiquid();
        boolean levitating = p.hasPotionEffect(PotionEffectType.LEVITATION);
        boolean onGroundApprox = p.isOnGround();
        // remember last safe ground position
        if (onGroundApprox) {
            try {
                // store a clone to avoid later mutation
                lastGroundLocation.put(id, p.getLocation().clone());
            } catch (Throwable ignored) {}
        }
        if (!inLiquid && !levitating && !p.isGliding() && !p.isFlying() && !p.isInsideVehicle()) {
            double vy = e.getTo().getY() - prev.getY();
            boolean hovering = Math.abs(vy) < 0.02;
            int air = airborneTicks.getOrDefault(id, 0);
            if (!onGroundApprox) air++; else air = 0;
            airborneTicks.put(id, air);
            if (air >= 30 && hovering) { // ~1.5s hover
                flag(p, "Fly/Hover");
                // attempt reset to last grounded location (anti-fly)
                resetSuspectedFly(p, id);
            }
        } else {
            airborneTicks.remove(id);
        }

        // Rotation speed (for KillAura)
    Float ly = lastYaw.get(id); Float lp = lastPitch.get(id);
        float cy = e.getTo().getYaw(); float cp = e.getTo().getPitch();
        if (ly != null && lp != null) {
            float dyaw = Math.abs(cy - ly); if (dyaw > 180) dyaw = 360 - dyaw;
            float dpitch = Math.abs(cp - lp);
            if (dyaw > 180 && dpitch > 45) {
                flag(p, "KillAura Rotations");
            }
        }
        lastYaw.put(id, cy); lastPitch.put(id, cp);

        // SneakSpeed: moving too fast while sneaking
        try {
            if (p.isSneaking() && bps > 3.2 && dt >= 0.25) {
                flag(p, "SneakSpeed (" + String.format(Locale.ROOT, "%.2f", bps) + " b/s)");
            }
        } catch (Throwable ignored) {}

        // Jesus (water walk): staying on water surface without sinking while moving
        try {
            org.bukkit.block.Block under = p.getLocation().clone().subtract(0, 1, 0).getBlock();
            boolean onWaterSurface = (under.getType() == org.bukkit.Material.WATER) && !p.getLocation().getBlock().isLiquid();
            if (onWaterSurface && bps > 2.2) {
                int t = waterSurfaceTicks.merge(id, 1, Integer::sum);
                if (t >= 25) { // ~25 movement samples (~ >2-3s real time depending on dt filtering)
                    flag(p, "Jesus (Wasseroberfläche)");
                    waterSurfaceTicks.put(id, 0);
                }
            } else {
                waterSurfaceTicks.remove(id);
            }
        } catch (Throwable ignored) {}

        // NoFall tracker: record start of air time for fall detection
        if (!p.isOnGround()) {
            lastAirStartY.putIfAbsent(id, p.getLocation().getY());
        } else {
            Double startY = lastAirStartY.remove(id);
            if (startY != null) {
                double drop = startY - p.getLocation().getY();
                if (drop >= 6.0) { // significant fall
                    long lastFall = lastFallDamageAt.getOrDefault(id, 0L);
                    if (System.currentTimeMillis() - lastFall > 500L) {
                        flag(p, "NoFall (" + String.format(Locale.ROOT, "%.1f", drop) + " Blöcke)");
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        Player attacker = null;
        if (e.getDamager() instanceof Player) attacker = (Player) e.getDamager();
        else if (e.getDamager() instanceof Projectile) {
            Object sh = ((Projectile) e.getDamager()).getShooter();
            if (sh instanceof Player) attacker = (Player) sh;
        }
        if (attacker == null) return;
        if (exempt(attacker)) return;

        // Reach check (only melee)
    if (e.getDamager() instanceof Player) {
            double dist = attacker.getLocation().distance(victim.getLocation());
            double maxReach = 3.6;
            PotionEffect spd = attacker.getPotionEffect(PotionEffectType.SPEED);
            if (spd != null) maxReach += 0.2 * (spd.getAmplifier() + 1);
            if (dist > maxReach) {
                e.setCancelled(true);
                flag(attacker, "Reach (" + String.format(Locale.ROOT, "%.2f", dist) + "m)");
                return;
            }

            // Facing check
            org.bukkit.util.Vector toVictim = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
            org.bukkit.util.Vector dir = attacker.getLocation().getDirection();
            double angle = dir.angle(toVictim);
            if (angle > Math.PI * 0.85) {
                e.setCancelled(true);
                flag(attacker, "Aura/Facing");
                return;
            }

            // Line of sight
            try { if (!attacker.hasLineOfSight(victim)) { flag(attacker, "No LoS"); } } catch (Throwable ignored) {}
        }

        // CPS check
        long now = System.currentTimeMillis();
        Deque<Long> times = attackTimes.computeIfAbsent(attacker.getUniqueId(), k -> new ArrayDeque<>());
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > 1000L) times.removeFirst();
        if (times.size() > 15) {
            e.setCancelled(true);
            flag(attacker, "CPS>15");
        }

        // Multi-target
        Deque<UUID> tv = recentVictims.computeIfAbsent(attacker.getUniqueId(), k -> new ArrayDeque<>());
        tv.addLast(victim.getUniqueId());
        while (tv.size() > 10) tv.removeFirst();
        long distinct = tv.stream().distinct().count();
        if (distinct >= 4 && tv.size() >= 6) {
            flag(attacker, "Multi-target");
        }

        // Bow Aimbot heuristic: projectile-based precise hits repeatedly
        if (!(e.getDamager() instanceof Player) && attacker != null && e.getDamager() instanceof Projectile) {
            UUID aid = attacker.getUniqueId();
            org.bukkit.util.Vector toVictim = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
            org.bukkit.util.Vector dir = attacker.getLocation().getDirection();
            double angle = dir.angle(toVictim); // radians
            double deg = Math.toDegrees(angle);
            Deque<Double> ang = recentRangedAngles.computeIfAbsent(aid, k -> new ArrayDeque<>());
            ang.addLast(deg);
            while (ang.size() > 10) ang.removeFirst();
            long now2 = System.currentTimeMillis();
            Deque<Long> times2 = attackTimes.computeIfAbsent(aid, k -> new ArrayDeque<>());
            times2.addLast(now2);
            while (!times2.isEmpty() && now2 - times2.peekFirst() > 4000L) times2.removeFirst();
            long hits = times2.size();
            if (hits >= 5) {
                double small = ang.stream().filter(a -> a < 3.0).count();
                if (small >= 4) {
                    flag(attacker, "Bow Aimbot Heuristik");
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (exempt(p)) return;
        org.bukkit.Material type = e.getBlock().getType();
        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        MiningStats ms = miningStats.computeIfAbsent(id, k -> new MiningStats());

        // FastBreak heuristic: too many fast consecutive breaks of non-instant blocks
        try {
            long last = lastBlockBreakAt.getOrDefault(id, 0L);
            long dtMs = (last == 0L ? 9999L : now - last);
            lastBlockBreakAt.put(id, now);
            boolean instant = isInstantBreak(type);
            if (!instant && dtMs < 90L) { // <90ms between non-instant breaks
                Deque<Long> fb = fastBreakTimes.computeIfAbsent(id, k -> new ArrayDeque<>());
                fb.addLast(now);
                while (!fb.isEmpty() && now - fb.peekFirst() > 1500L) fb.removeFirst();
                if (fb.size() >= 8) {
                    flag(p, "FastBreak (" + fb.size() + " in 1.5s)");
                    fb.clear();
                }
            }
        } catch (Throwable ignored) {}

        // Track stone-like/netherrack blocks in sliding window
        if (p.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER) {
            if (type == org.bukkit.Material.NETHERRACK || type == org.bukkit.Material.BLACKSTONE || type == org.bukkit.Material.BASALT) {
                ms.netherrackTimes.addLast(now);
                while (!ms.netherrackTimes.isEmpty() && now - ms.netherrackTimes.peekFirst() > windowMs) ms.netherrackTimes.removeFirst();
            }
        } else {
            if (type == org.bukkit.Material.STONE || type == org.bukkit.Material.DEEPSLATE || type == org.bukkit.Material.TUFF
                    || type == org.bukkit.Material.ANDESITE || type == org.bukkit.Material.DIORITE || type == org.bukkit.Material.GRANITE) {
                ms.stoneTimes.addLast(now);
                while (!ms.stoneTimes.isEmpty() && now - ms.stoneTimes.peekFirst() > windowMs) ms.stoneTimes.removeFirst();
            }
        }

        // Branch axis inference between consecutive block breaks
        org.bukkit.Location cur = e.getBlock().getLocation();
        if (ms.lastBreak != null && ms.lastBreak.getWorld().equals(cur.getWorld())) {
            int dx = Math.abs(cur.getBlockX() - ms.lastBreak.getBlockX());
            int dz = Math.abs(cur.getBlockZ() - ms.lastBreak.getBlockZ());
            int dy = Math.abs(cur.getBlockY() - ms.lastBreak.getBlockY());
            char axis;
            if (dx > dz && dx >= dy) axis = 'X';
            else if (dz > dx && dz >= dy) axis = 'Z';
            else axis = (dx >= dz ? 'X' : 'Z');

            if (ms.lastAxis == null || ms.lastAxis == axis) {
                ms.lastAxis = axis;
                if (ms.branchAxis != null && ms.branchAxis == axis) {
                    ms.branchSteps++;
                } else {
                    ms.branchAxis = null;
                    ms.branchSteps = 0;
                }
            } else {
                // Axis changed: start a new branch
                ms.lastAxis = axis;
                ms.branchAxis = axis;
                ms.branchSteps = 1;
            }
        }
        ms.lastBreak = cur;

        // Ore logic and heuristics
        if (type == org.bukkit.Material.DIAMOND_ORE || type == org.bukkit.Material.DEEPSLATE_DIAMOND_ORE
                || type == org.bukkit.Material.ANCIENT_DEBRIS) {
            OreStats st = oreStats.computeIfAbsent(p.getUniqueId(), k -> new OreStats());
            boolean buried = isBuriedOre(e.getBlock());
            if (type == org.bukkit.Material.ANCIENT_DEBRIS) {
                st.debrisTimes.addLast(now);
                while (!st.debrisTimes.isEmpty() && now - st.debrisTimes.peekFirst() > windowMs) st.debrisTimes.removeFirst();
                if (st.debrisTimes.size() >= debrisFastFind) {
                    warnGMs(p, "schnell Netherit gefunden (" + st.debrisTimes.size() + "/" + windowMinutes + "min)" + (buried?" buried":""));
                }
                int mined = ms.netherrackTimes.size();
                if (mined >= debrisPerBlocks && st.debrisTimes.size() > 0) {
                    double ratio = st.debrisTimes.size() / Math.max(1.0, mined / (double) debrisPerBlocks);
                    double maxRatio = debrisMaxRatio;
                    boolean nether = p.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER;
                    if (nether && debrisMaxRatioNether != null) maxRatio = debrisMaxRatioNether;
                    if (!nether && debrisMaxRatioOverworld != null) maxRatio = debrisMaxRatioOverworld;
                    if (ratio > maxRatio) {
                        warnGMs(p, String.format(Locale.ROOT, "auffälliges Verhältnis Netherit/Netherrack (%.2f/" + debrisPerBlocks + ")", ratio));
                    }
                }
            } else {
                st.diamondTimes.addLast(now);
                while (!st.diamondTimes.isEmpty() && now - st.diamondTimes.peekFirst() > windowMs) st.diamondTimes.removeFirst();
                if (st.diamondTimes.size() >= diamondFastFind) {
                    warnGMs(p, "schnell Diamanten gefunden (" + st.diamondTimes.size() + "/" + windowMinutes + "min)" + (buried?" buried":""));
                }
                int mined = ms.stoneTimes.size();
                if (mined >= diamondPerBlocks && st.diamondTimes.size() > 0) {
                    double ratio = st.diamondTimes.size() / Math.max(1.0, mined / (double) diamondPerBlocks);
                    double maxRatio = diamondMaxRatio;
                    boolean nether = p.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER;
                    if (nether && diamondMaxRatioNether != null) maxRatio = diamondMaxRatioNether;
                    if (!nether && diamondMaxRatioOverworld != null) maxRatio = diamondMaxRatioOverworld;
                    if (ratio > maxRatio) {
                        warnGMs(p, String.format(Locale.ROOT, "auffälliges Verhältnis Diamant/Stein (%.2f/" + diamondPerBlocks + ")", ratio));
                    }
                }
            }
            if (buried) {
                flag(p, "Xray-Heuristik (begrabene Erze)");
            }
            if (ms.branchAxis != null && ms.branchSteps > 0 && ms.branchSteps <= branchWithinSteps) {
                ms.branchToOreTimes.addLast(now);
                while (!ms.branchToOreTimes.isEmpty() && now - ms.branchToOreTimes.peekFirst() > windowMs) ms.branchToOreTimes.removeFirst();
                if (ms.branchToOreTimes.size() >= branchWarnCount) {
                    warnGMs(p, "verdächtiges Mining-Muster: Branch→Erz mehrfach (≤" + branchWithinSteps + " Blöcke)");
                }
            }

            // 1-minute ore burst detection (all ores combined)
            int burstThreshold = oreBurstPerMinute;
            if (p.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER && oreBurstPerMinuteNether != null) burstThreshold = oreBurstPerMinuteNether;
            if (p.getWorld().getEnvironment() != org.bukkit.World.Environment.NETHER && oreBurstPerMinuteOverworld != null) burstThreshold = oreBurstPerMinuteOverworld;
            if (burstThreshold > 0) {
                int burst = countRecent(st.diamondTimes, now, 60_000L) + countRecent(st.debrisTimes, now, 60_000L);
                if (burst >= burstThreshold) {
                    String msg = ChatColor.YELLOW + "Teilnehmer: " + ChatColor.WHITE + p.getName() + ChatColor.GRAY
                            + " baut " + ChatColor.GOLD + burst + ChatColor.GRAY + " Erze in unter 1 Minute ab";
                    warnGMs(p, ChatColor.RED + "XRAY? " + msg);
                    sendGmQuickActions(p, burst, 60_000L, ms, now);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            lastFallDamageAt.put(((Player) e.getEntity()).getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (exempt(p)) return;
        long now = System.currentTimeMillis();
        Deque<Long> pt = placeTimes.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        pt.addLast(now);
        while (!pt.isEmpty() && now - pt.peekFirst() > 3000L) pt.removeFirst();
        // Scaffold-ish: many placements quickly while moving fast and placing near feet level
        org.bukkit.Location pl = e.getBlockPlaced().getLocation();
        double dy = p.getLocation().getY() - pl.getY();
        org.bukkit.Location prev = lastLoc.get(p.getUniqueId());
        Long prevAt = lastLocAt.get(p.getUniqueId());
        if (prev != null && prevAt != null) {
            double dist = p.getLocation().distance(prev);
            double dt = Math.max(1, now - prevAt) / 1000.0;
            double bps = dist / dt;
            if (bps > 3.0 && dy <= 1.5 && pt.size() >= 6) {
                flag(p, "Scaffold Heuristik");
            }
        }
    }

    private boolean isBuriedOre(org.bukkit.block.Block b) {
        org.bukkit.block.BlockFace[] faces = new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN,
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST
        };
        for (org.bukkit.block.BlockFace f : faces) {
            if (b.getRelative(f).getType().isAir()) return false;
        }
        return true;
    }

    private void flag(Player p, String reason) {
        int v = violations.merge(p.getUniqueId(), 1, Integer::sum);
        // store recent flag
        Deque<FlagEntry> dq = recentFlags.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
    dq.addLast(new FlagEntry(System.currentTimeMillis(), "AC", reason, p.getLocation()));
        while (dq.size() > 20) dq.removeFirst();
    // Only admins/GMs get alerts; suspects do not see AC warnings
        if (log != null) log.accept("AntiCheat Flag: " + p.getName() + " - " + reason + " (V=" + v + ")");
        sendGmAlert(p, reason, v);
        // Save short clip automatically
        saveClip(p.getUniqueId());
    auditLog(p, "AC", reason);
    }

    private void warnGMs(Player p, String msg) {
        if (log != null) log.accept("OreWarn: " + p.getName() + " - " + msg);
    // record XRAY-category flag for review highlighting
    Deque<FlagEntry> dq = recentFlags.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
    dq.addLast(new FlagEntry(System.currentTimeMillis(), "XRAY", msg, p.getLocation()));
    while (dq.size() > 20) dq.removeFirst();
    sendGmAlert(p, msg, violations.getOrDefault(p.getUniqueId(), 0));
    auditLog(p, "XRAY", msg);
    }

    private void sendGmAlert(Player suspect, String msg, int v) {
        long now = System.currentTimeMillis();
        UUID id = suspect.getUniqueId();
        long last = lastGmAlertAt.getOrDefault(id, 0L);
        if (now - last < 5000L) {
            suppressedAlerts.merge(id, 1, Integer::sum);
            return;
        }
        int sup = suppressedAlerts.getOrDefault(id, 0);
        suppressedAlerts.remove(id);
        lastGmAlertAt.put(id, now);
        String extra = sup > 0 ? ChatColor.DARK_GRAY + " (" + sup + " weitere)" : "";
        for (Player gm : Bukkit.getOnlinePlayers()) {
            if (gameMasters.contains(gm.getUniqueId()) || gm.isOp()) {
                gm.sendMessage(ChatColor.RED + "AC: " + ChatColor.YELLOW + suspect.getName() + ChatColor.GRAY + " → " + ChatColor.WHITE + msg + ChatColor.GRAY + " (V=" + v + ")" + extra);
            }
        }
    }

    private int countRecent(Deque<Long> dq, long now, long spanMs) {
        if (dq == null || dq.isEmpty()) return 0;
        int c = 0;
        for (Long t : dq) if (now - t <= spanMs) c++;
        return c;
    }

    private boolean isUnderground(Player p) {
        try {
            org.bukkit.World w = p.getWorld();
            int highest = w.getHighestBlockYAt(p.getLocation());
            return p.getLocation().getBlockY() < highest;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void sendGmQuickActions(Player suspect, int burst, long windowMs, MiningStats ms, long now) {
        boolean nether = suspect.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER;
        int stone1m = nether ? countRecent(ms.netherrackTimes, now, 60_000L) : countRecent(ms.stoneTimes, now, 60_000L);
        boolean underground = isUnderground(suspect);
        int y = suspect.getLocation().getBlockY();
        // Spielzeit
        String playedStr = "-";
        try {
            int ticks = suspect.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
            long secs = Math.max(0, ticks / 20);
            long h = secs / 3600, m = (secs % 3600) / 60;
            playedStr = String.format(java.util.Locale.ROOT, "%dh %02dm", h, m);
        } catch (Throwable ignored) {}

        String info = ChatColor.DARK_GRAY + "[Y=" + y + ", Untergrund=" + (underground?"ja":"nein") + ", "
                + (nether?"Netherrack":"Stein") + " in 1m=" + stone1m + ", Spielzeit=" + playedStr + "]";

        for (Player gm : Bukkit.getOnlinePlayers()) {
            if (!(gameMasters.contains(gm.getUniqueId()) || gm.isOp())) continue;
            gm.sendMessage(info);
            try {
                TextComponent root = new TextComponent(ChatColor.GOLD + "Aktionen: ");

                TextComponent tp = new TextComponent(ChatColor.GREEN + "[TP]");
                tp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpl " + suspect.getName()));
                // Hover text is optional and version-dependent; omit for compatibility

                TextComponent review = new TextComponent(ChatColor.AQUA + " [Review]");
                review.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/review " + suspect.getName()));
                // Hover text is optional and version-dependent; omit for compatibility

                TextComponent inv = new TextComponent(ChatColor.YELLOW + " [Inv]");
                inv.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/inspect " + suspect.getName()));
                // Hover text is optional and version-dependent; omit for compatibility

                root.addExtra(tp);
                root.addExtra(review);
                root.addExtra(inv);

                try { gm.spigot().sendMessage(root); }
                catch (Throwable t) { gm.sendMessage(ChatColor.GOLD + "Aktionen: " + ChatColor.GREEN + "/tpl " + suspect.getName() + ChatColor.AQUA + ", /review " + suspect.getName() + ChatColor.YELLOW + ", /inspect " + suspect.getName()); }
            } catch (Throwable ignored) {
                gm.sendMessage(ChatColor.GOLD + "Aktionen: " + ChatColor.GREEN + "/tpl " + suspect.getName() + ChatColor.AQUA + ", /review " + suspect.getName() + ChatColor.YELLOW + ", /inspect " + suspect.getName());
            }
        }
    }

    // Data classes
    @SuppressWarnings("unused")
    private static class FlagEntry {
        final long ts; final String category; final String reason; final String world; final int x, y, z;
        FlagEntry(long ts, String category, String reason, Location loc) {
            this.ts = ts; this.category = category; this.reason = reason;
            if (loc != null && loc.getWorld() != null) {
                this.world = loc.getWorld().getName(); this.x = loc.getBlockX(); this.y = loc.getBlockY(); this.z = loc.getBlockZ();
            } else { this.world = "-"; this.x = this.y = this.z = 0; }
        }
    }
    private static class LocationSample {
        final long ts; final double x,y,z; final String world;
        static LocationSample of(long ts, Location l) { return new LocationSample(ts, l.getWorld().getName(), l.getX(), l.getY(), l.getZ()); }
        LocationSample(long ts, String world, double x, double y, double z) { this.ts=ts; this.world=world; this.x=x; this.y=y; this.z=z; }
        Map<String,Object> toMap() {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("ts", ts); m.put("world", world); m.put("x", x); m.put("y", y); m.put("z", z);
            return m;
        }
    }

    // CSV audit for flags
    private void auditLog(Player p, String category, String message) {
    if (!auditEnabled) return;
        try {
            File data = dataFolderSupplier.get();
            File dir = new File(data, "audit");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "flags.csv");
            boolean newFile = !f.exists();
            try (FileWriter fw = new FileWriter(f, true)) {
                if (newFile) fw.write("ts,player,uuid,category,world,x,y,z,reason\n");
                Location loc = p.getLocation();
                String world = (loc.getWorld() != null ? loc.getWorld().getName() : "-");
                String reasonEsc = message == null ? "" : message.replace('\n', ' ').replace(',', ';');
                fw.write(String.format(java.util.Locale.ROOT, "%d,%s,%s,%s,%s,%d,%d,%d,%s\n",
                        System.currentTimeMillis(), p.getName(), p.getUniqueId().toString(), category,
                        world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), reasonEsc));
            }
        } catch (Throwable ignored) {}
    }

    // Utilities
    private boolean isInstantBreak(org.bukkit.Material m) {
        // Simplified list of near-instant blocks (even with bare hand)
        try {
            switch (m) {
                case DEAD_BUSH:
                case DANDELION: case POPPY: case TORCH:
                case LEVER:
                case WHEAT: case CARROTS: case POTATOES: case BEETROOTS: case NETHER_WART:
                case RAIL: case POWERED_RAIL: case DETECTOR_RAIL: case ACTIVATOR_RAIL:
                case STRING: case TRIPWIRE: case VINE: case LADDER: case SNOW:
                case FLOWER_POT: case BROWN_MUSHROOM: case RED_MUSHROOM:
                case SUGAR_CANE: case CACTUS: case BAMBOO:
                    return true;
                default:
                    return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void resetSuspectedFly(Player p, UUID id) {
        long now = System.currentTimeMillis();
        long last = lastFlyResetAt.getOrDefault(id, 0L);
        if (now - last < 1500L) return; // cooldown to avoid spam
        org.bukkit.Location back = lastGroundLocation.get(id);
        if (back == null) return;
        if (!back.getWorld().equals(p.getWorld())) return;
        // don't teleport if very far (could be legit vertical travel / void save)
        if (back.distanceSquared(p.getLocation()) > 400) return; // >20 blocks
        try {
            p.teleport(back.clone().add(0, 0.2, 0));
            p.setFallDistance(0f);
            lastFlyResetAt.put(id, now);
        } catch (Throwable ignored) {}
    }
}
