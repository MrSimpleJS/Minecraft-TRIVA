




package de.varo.features.hud;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.io.File;

/**
 * Handles periodic HUD, border shrink, warnings/damage, Nether time, and scoreboard updates.
 * Owns its own 1-second scheduler and reuses state passed by reference from the plugin.
 */
public class HudBorderFeature implements Listener {
    private final JavaPlugin plugin;
    private final Map<String, List<java.util.UUID>> teams;
    private final Map<String, ChatColor> teamColors;
    private final Map<String, Boolean> teamReady;
    private final Set<java.util.UUID> gameMasters;
    private final Set<java.util.UUID> streamers;
    private final Map<java.util.UUID, Long> lastActive;
    private final Set<java.util.UUID> warnedAfk;
    private final Set<java.util.UUID> hudHidden;
    private final Map<java.util.UUID, Integer> borderOutsideSeconds;
    private final Map<java.util.UUID, Long> netherUsedMs;
    private final Map<java.util.UUID, Long> netherSessionStart;
    private final Set<java.util.UUID> warned30, warned10, warned5, warned1, netherOvertime;
    // Removed top-killer tracking from HUD (UI cleanup)
    private final Map<java.util.UUID, String> playerTeam;
    private final Map<java.util.UUID, String> lastScoreboardHash;
    private final Map<java.util.UUID, Long> lastScoreboardAt;

    private final BooleanSupplier gameRunning;
    private final BooleanSupplier paused;
    private final LongSupplier projectRemainingMs;
    private final LongSupplier nextShrinkAtGetter;
    private final Runnable scheduleNextShrink;

    private final double borderStartSize, borderEndSize;
    private final int borderShrinkEveryMinutes, borderShrinkLerpSeconds, projectDurationMinutes;
    private final long afkKickMs;

    private BukkitTask task;
    // HUD burst fields removed in favor of 2-minute pre-shrink window
    private long nextScoreboardUpdateAt = 0L;
    private long SCOREBOARD_UPDATE_INTERVAL_MS; // throttle scoreboard updates (configurable)
    // GM overlay rotation index per GM
    private final Map<java.util.UUID, Integer> gmOverlayIndex = new HashMap<>();
    // Shrink warning tracking
    private long lastShrinkTargetAt = -1L;
    private final java.util.Set<Integer> shrinkWarnedMin = new java.util.HashSet<>();

    // Nether lockdown helpers (bossbar removed)
    private final Map<java.util.UUID, Location> lastOverworldPos = new HashMap<>();
    private final Map<java.util.UUID, BukkitTask> netherEvictTasks = new HashMap<>();
    private final Map<java.util.UUID, Long> netherEvictDeadlineMs = new HashMap<>();
    private boolean netherLockdownActive = false; // becomes true when projectRemainingMs <= 1h
    private static final long ONE_HOUR_MS = 3_600_000L;
    // Performance: throttle border preview particle rendering and tab header updates
    private final Map<java.util.UUID, Long> nextBorderParticlesAt = new HashMap<>();
    private long BORDER_PARTICLE_INTERVAL_MS; // per-player (configurable)
    private double BORDER_PREVIEW_NEAR_DIST; // only show when close to edge (configurable)
    private double BORDER_PARTICLE_RADIUS_SQ; // within view radius (configurable)
    private double BORDER_PARTICLE_STEP; // coarser sampling grid (configurable)
    private long nextTabHeaderAt = 0L;
    private String lastTabHeader = null;
    private final Map<java.util.UUID, String> lastTabFooterByPlayer = new HashMap<>();

    // Persistence for last overworld positions
    private File lastOverworldFile;
    private YamlConfiguration lastOverworldCfg;
    private long nextOverworldSaveAt = 0L;
    private boolean overworldDirty = false;

    public HudBorderFeature(
            JavaPlugin plugin,
            Map<String, List<java.util.UUID>> teams,
            Map<String, ChatColor> teamColors,
            Map<String, Boolean> teamReady,
            Map<java.util.UUID, String> playerTeam,
            Set<java.util.UUID> gameMasters,
            Set<java.util.UUID> streamers,
            Map<java.util.UUID, Long> lastActive,
            Set<java.util.UUID> warnedAfk,
            Set<java.util.UUID> hudHidden,
            Map<java.util.UUID, Integer> borderOutsideSeconds,
            Map<java.util.UUID, Long> netherUsedMs,
            Map<java.util.UUID, Long> netherSessionStart,
            Set<java.util.UUID> warned30,
            Set<java.util.UUID> warned10,
            Set<java.util.UUID> warned5,
            Set<java.util.UUID> warned1,
            Set<java.util.UUID> netherOvertime,
            Map<java.util.UUID, String> lastScoreboardHash,
            Map<java.util.UUID, Long> lastScoreboardAt,
            BooleanSupplier gameRunning,
            BooleanSupplier paused,
            LongSupplier projectRemainingMs,
            LongSupplier nextShrinkAtGetter,
            Runnable scheduleNextShrink,
            double borderStartSize,
            double borderEndSize,
            int borderShrinkEveryMinutes,
            int borderShrinkLerpSeconds,
            int projectDurationMinutes,
            long afkKickMs,
            long scoreboardUpdateIntervalMs,
            long borderParticleIntervalMs,
            double borderNearDist,
            double borderParticleRadius,
            double borderParticleStep
    ) {
        this.plugin = plugin;
    this.teams = teams;
    this.teamColors = teamColors;
        this.teamReady = teamReady;
        this.playerTeam = playerTeam;
        this.gameMasters = gameMasters;
        this.streamers = streamers;
        this.lastActive = lastActive;
        this.warnedAfk = warnedAfk;
        this.hudHidden = hudHidden;
        this.borderOutsideSeconds = borderOutsideSeconds;
        this.netherUsedMs = netherUsedMs;
        this.netherSessionStart = netherSessionStart;
        this.warned30 = warned30; this.warned10 = warned10; this.warned5 = warned5; this.warned1 = warned1; this.netherOvertime = netherOvertime;
        this.lastScoreboardHash = lastScoreboardHash;
        this.lastScoreboardAt = lastScoreboardAt;
        this.gameRunning = gameRunning;
        this.paused = paused;
        this.projectRemainingMs = projectRemainingMs;
        this.nextShrinkAtGetter = nextShrinkAtGetter;
        this.scheduleNextShrink = scheduleNextShrink;
        this.borderStartSize = borderStartSize;
        this.borderEndSize = borderEndSize;
        this.borderShrinkEveryMinutes = borderShrinkEveryMinutes;
        this.borderShrinkLerpSeconds = borderShrinkLerpSeconds;
    this.projectDurationMinutes = projectDurationMinutes;
    this.afkKickMs = afkKickMs;
    this.SCOREBOARD_UPDATE_INTERVAL_MS = Math.max(500L, scoreboardUpdateIntervalMs);
    this.BORDER_PARTICLE_INTERVAL_MS = Math.max(250L, borderParticleIntervalMs);
    this.BORDER_PREVIEW_NEAR_DIST = Math.max(4.0, borderNearDist);
    double r = Math.max(4.0, borderParticleRadius);
    this.BORDER_PARTICLE_RADIUS_SQ = r * r;
    this.BORDER_PARTICLE_STEP = Math.max(1.0, borderParticleStep);
    }

    // Einheitliche Domain (Branding) – bei Bedarf an einer Stelle ändern
    private static final String BRAND_DOMAIN = ChatColor.DARK_RED + "play.triva.net";

    // Apply updated HUD settings at runtime
    public void applyHudSettings(long scoreboardUpdateIntervalMs,
                                 long borderParticleIntervalMs,
                                 double borderNearDist,
                                 double borderParticleRadius,
                                 double borderParticleStep) {
        this.SCOREBOARD_UPDATE_INTERVAL_MS = Math.max(500L, scoreboardUpdateIntervalMs);
        this.BORDER_PARTICLE_INTERVAL_MS = Math.max(250L, borderParticleIntervalMs);
        this.BORDER_PREVIEW_NEAR_DIST = Math.max(4.0, borderNearDist);
        double r = Math.max(4.0, borderParticleRadius);
        this.BORDER_PARTICLE_RADIUS_SQ = r * r;
        this.BORDER_PARTICLE_STEP = Math.max(1.0, borderParticleStep);
        // allow immediate scoreboard refresh sooner
        this.nextScoreboardUpdateAt = 0L;
    }


    public void start() {
        if (task != null) return;
    // Initialize timers and event listeners
    // register events
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
    // initialize lockdown flag
    netherLockdownActive = projectRemainingMs.getAsLong() <= ONE_HOUR_MS;
    // init last-overworld storage
    initLastOverworldStorage();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickSecond, 20L, 20L);
    }

    public void stop() {
    if (task != null) { task.cancel(); task = null; }
    // unregister events
    HandlerList.unregisterAll(this);
    // cleanup bossbars and tasks
    // no bossbars to clean up
    for (BukkitTask t : netherEvictTasks.values()) { try { t.cancel(); } catch (Throwable ignored) {} }
    netherEvictTasks.clear();
    netherEvictDeadlineMs.clear();
    // persist last overworld positions
    try { saveAllLastOverworld(); } catch (Throwable ignored) {}
    }

    private void tickSecond() {
        try { doTick(); } catch (Throwable t) { plugin.getLogger().warning("HudBorderFeature tick error: " + t.getMessage()); }
    }

    private void doTick() {
    long now = System.currentTimeMillis();

    List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        if (now >= nextTabHeaderAt) { updateTabHeaderFooter(); nextTabHeaderAt = now + 2000L; }

        // AFK-Kick
        for (Player p : players) {
            if (gameMasters.contains(p.getUniqueId()) || streamers.contains(p.getUniqueId())) continue;
            if (!gameRunning.getAsBoolean() || paused.getAsBoolean()) continue;
            long last = lastActive.getOrDefault(p.getUniqueId(), now);
            long idle = now - last;
            if (idle > afkKickMs - 30_000 && idle < afkKickMs && warnedAfk.add(p.getUniqueId())) {
                p.sendMessage(ChatColor.YELLOW + "AFK-Warnung: du wirst in 30s gekickt, wenn du dich nicht bewegst.");
            }
            if (idle >= afkKickMs) {
                p.kickPlayer(ChatColor.RED + "AFK-Kick.");
                warnedAfk.remove(p.getUniqueId());
            }
        }

    if (!gameRunning.getAsBoolean() || paused.getAsBoolean()) {
            updateScoreboard(players, now);
            return;
        }

    // HUD bursts disabled: only show border guidance in the last 2 minutes before shrink

        // Pre-shrink warnings: 10/5/2/1 min sound
        long nextShrink = nextShrinkAtGetter.getAsLong();
        // Falls nextShrinkAt noch nicht gesetzt (0), einmal planen um sofortigen Shrink-Spam zu verhindern
        if (nextShrink <= 0L) {
            try { scheduleNextShrink.run(); } catch (Throwable ignored) {}
            nextShrink = nextShrinkAtGetter.getAsLong();
        }
        if (lastShrinkTargetAt != nextShrink) { shrinkWarnedMin.clear(); lastShrinkTargetAt = nextShrink; }
        long untilShrink = Math.max(0L, nextShrink - now);
        int[] marks = new int[]{10,5,2,1};
        for (int m : marks) {
            if (untilShrink <= m*60_000L && !shrinkWarnedMin.contains(m)) {
                shrinkWarnedMin.add(m);
                for (Player p : players) {
                    try { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f); } catch (Throwable ignored) {}
                    if (m == 2) {
                        p.sendMessage(ChatColor.YELLOW + "Border schrumpft in " + ChatColor.WHITE + "2 Minuten" + ChatColor.YELLOW + ".");
                    } else {
                        p.sendMessage(ChatColor.YELLOW + "Border schrumpft in " + ChatColor.WHITE + m + "m" + ChatColor.YELLOW + ".");
                    }
                }
            }
        }

    // Border Shrink (unterdrückt im Finale – dort übernimmt langsamer Shrink ohne Spam)
    // Vorheriger Bug: ternary-Operator band now>=nextShrink mit instanceof falsch, so dass bei VaroPlugin immer (!finalPhase) alleine geprüft wurde -> jede Sekunde Shrink.
    if (now >= nextShrink && (!(plugin instanceof de.varo.VaroPlugin) || !((de.varo.VaroPlugin)plugin).isFinalPhaseActive())) {
            World w = plugin.getServer().getWorlds().get(0);
            WorldBorder wb = w.getWorldBorder();
            int totalShrinks = Math.max(1, projectDurationMinutes / Math.max(1, borderShrinkEveryMinutes));
            double step = (borderStartSize - borderEndSize) / totalShrinks;
            double newSize = Math.max(borderEndSize, wb.getSize() - step);
            wb.setSize(newSize, borderShrinkLerpSeconds);
            Bukkit.broadcastMessage(ChatColor.RED + "Border schrumpft! Neue Größe: " + (int) newSize + " Blöcke");
            Location center = wb.getCenter();
            for (Player p : players) {
                if (!p.getWorld().equals(w)) continue;
                String arrow = arrowToCenter(p.getLocation(), center);
                String dirText = directionToCenterText(p.getLocation(), center);
                String msg = ChatColor.RED + "⚠ Border schrumpft! " + ChatColor.YELLOW + "lauf nach "
                        + (ChatColor.WHITE + dirText + " " + arrow);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
                try { p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 0.8f); } catch (Throwable ignored) {}
            }
            scheduleNextShrink.run();
        }

    // Blitz-Finale placeholder when project ends (invoke if needed outside)
    if (projectRemainingMs.getAsLong() == 0L) {
            // optional hook
        }

        // Update last overworld positions for safe return
    for (Player p : players) {
            if (p.getWorld().getEnvironment() == World.Environment.NORMAL) {
        lastOverworldPos.put(p.getUniqueId(), p.getLocation().clone());
        overworldDirty = true;
            }
        }
    // throttle-save overworld positions
    if (overworldDirty && now >= nextOverworldSaveAt) {
        try { saveAllLastOverworld(); } catch (Throwable ignored) {}
        nextOverworldSaveAt = now + 10_000L; // every 10s
        overworldDirty = false;
    }

    // Nether time (1 hour cap per player) and overtime
        Map<java.util.UUID, Long> netherLeftThisTick = new HashMap<>();
        for (Player p : players) {
            if (p.getWorld().getEnvironment() == World.Environment.NETHER) {
                long start = netherSessionStart.getOrDefault(p.getUniqueId(), now);
                long used = netherUsedMs.getOrDefault(p.getUniqueId(), 0L);
                long diff = now - start;
                used += diff;
                netherUsedMs.put(p.getUniqueId(), used);
                netherSessionStart.put(p.getUniqueId(), now);

        // Personal remaining time: starts at 1 hour and persists across sessions
        long effectiveLeft = Math.max(0L, ONE_HOUR_MS - used);

                if (effectiveLeft <= 30L * 60_000L && warned30.add(p.getUniqueId()))
                    p.sendMessage(ChatColor.YELLOW + "Nether: 30 Minuten übrig.");
                if (effectiveLeft <= 10L * 60_000L && warned10.add(p.getUniqueId()))
                    p.sendMessage(ChatColor.YELLOW + "Nether: 10 Minuten übrig.");
                if (effectiveLeft <= 5L * 60_000L && warned5.add(p.getUniqueId()))
                    p.sendMessage(ChatColor.GOLD + "Nether: 5 Minuten übrig.");
                if (effectiveLeft <= 60_000L && warned1.add(p.getUniqueId()))
                    p.sendMessage(ChatColor.RED + "Nether: 1 Minute übrig!");

                netherLeftThisTick.put(p.getUniqueId(), effectiveLeft);

                if (effectiveLeft <= 0L) netherOvertime.add(p.getUniqueId());
                if (netherOvertime.contains(p.getUniqueId())) {
                    double hp = p.getHealth();
                    if (hp > 1.0D) {
                        double newHp = Math.max(1.0D, hp - 1.0D);
                        p.setHealth(newHp);
                        p.sendMessage(ChatColor.DARK_RED + "⚠ Deine Nether-Zeit ist abgelaufen! Verlasse den Nether!");
                    } else {
                        World overworld = plugin.getServer().getWorlds().get(0);
                        WorldBorder wb = overworld.getWorldBorder();
                        double cx = wb.getCenter().getX();
                        double cz = wb.getCenter().getZ();
                        int y = overworld.getHighestBlockYAt((int) Math.round(cx), (int) Math.round(cz)) + 1;
                        Location mid = new Location(overworld, cx + 0.5, y, cz + 0.5);
                        p.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Letzte Warnung! Ab in die Overworld (Mitte).");
                        p.teleport(mid);
                        netherSessionStart.remove(p.getUniqueId());
                        netherOvertime.remove(p.getUniqueId());
                    }
                }
            } else {
                // Not in Nether: clear eviction countdown state
                // Ausstehenden Rauswurf abbrechen, wenn Spieler Nether verlässt
                BukkitTask ev = netherEvictTasks.remove(p.getUniqueId());
                if (ev != null) { try { ev.cancel(); } catch (Throwable ignored) {} }
                netherEvictDeadlineMs.remove(p.getUniqueId());
            }
        }

    // Nether-Lockdown aktivieren, wenn <= 1h übrig; plane Rauswurf für aktuelle Nether-Spieler
        long remaining = projectRemainingMs.getAsLong();
        if (!netherLockdownActive && remaining <= ONE_HOUR_MS) {
            netherLockdownActive = true;
            for (Player p : players) {
                if (p.getWorld().getEnvironment() == World.Environment.NETHER) {
                    scheduleNetherEviction(p, 2 * 60L); // 2 minutes
                    p.sendMessage(ChatColor.RED + "Nether wird gesperrt. Du hast 2 Minuten, die Overworld zu betreten.");
                }
            }
        }

        // Outside world border warning + ramping damage + border preview particles along edge near players
        World overworld = plugin.getServer().getWorlds().get(0);
        WorldBorder owb = overworld.getWorldBorder();
        Location c = owb.getCenter();
        double half = owb.getSize() / 2.0;
    Set<java.util.UUID> warnedThisTick = new HashSet<>();
    // Track players near border for GM overlay
    int playersNearBorder = 0;
        for (Player p : players) {
            if (!p.getWorld().equals(overworld)) continue;
            if (gameMasters.contains(p.getUniqueId())) continue;
            if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) continue;
            double dx = Math.abs(p.getLocation().getX() - c.getX());
            double dz = Math.abs(p.getLocation().getZ() - c.getZ());
            boolean outside = (dx > half) || (dz > half);
            if (outside) {
                int secs = borderOutsideSeconds.getOrDefault(p.getUniqueId(), 0) + 1;
                borderOutsideSeconds.put(p.getUniqueId(), secs);
                String arrow = arrowToCenter(p.getLocation(), c);
                String warn = ChatColor.DARK_RED + "⚠ Du bist in der Border — geh schnell raus! " + ChatColor.YELLOW + arrow;
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(warn));
                warnedThisTick.add(p.getUniqueId());
                double dmg = secs <= 5 ? 1.0 : (secs <= 15 ? 2.0 : 4.0);
                try { p.damage(dmg); } catch (Throwable ignored) {}
            } else {
                borderOutsideSeconds.remove(p.getUniqueId());
            }
            // Count near-border players (<= 20 Blöcke zur Kante)
            double distToEdgeNow = half - Math.max(Math.abs(p.getLocation().getX() - c.getX()), Math.abs(p.getLocation().getZ() - c.getZ()));
            if (distToEdgeNow <= 20.0) playersNearBorder++;
            // preview particles at border edge near player (throttled and gated)
            try {
                long gate = nextBorderParticlesAt.getOrDefault(p.getUniqueId(), 0L);
                if (now >= gate) {
                    Location pl = p.getLocation();
                    double minX = c.getX() - half, maxX = c.getX() + half;
                    double minZ = c.getZ() - half, maxZ = c.getZ() + half;
                    // distance to nearest edge line
                    double distEdge = Math.min(
                        Math.min(Math.abs(minX - pl.getX()), Math.abs(maxX - pl.getX())),
                        Math.min(Math.abs(minZ - pl.getZ()), Math.abs(maxZ - pl.getZ()))
                    );
                    if (distEdge <= BORDER_PREVIEW_NEAR_DIST) {
                        double y = pl.getY() + 1.0;
                        for (double x = Math.max(minX, pl.getX()-16); x <= Math.min(maxX, pl.getX()+16); x += BORDER_PARTICLE_STEP) {
                            Location a = new Location(overworld, x, y, minZ);
                            Location b = new Location(overworld, x, y, maxZ);
                            if (a.distanceSquared(pl) <= BORDER_PARTICLE_RADIUS_SQ)
                                overworld.spawnParticle(org.bukkit.Particle.DUST, a, 1, 0,0,0, 0,
                                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255,64,64), 1.3f));
                            if (b.distanceSquared(pl) <= BORDER_PARTICLE_RADIUS_SQ)
                                overworld.spawnParticle(org.bukkit.Particle.DUST, b, 1, 0,0,0, 0,
                                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255,64,64), 1.3f));
                        }
                        for (double z = Math.max(minZ, pl.getZ()-16); z <= Math.min(maxZ, pl.getZ()+16); z += BORDER_PARTICLE_STEP) {
                            Location a = new Location(overworld, minX, y, z);
                            Location b = new Location(overworld, maxX, y, z);
                            if (a.distanceSquared(pl) <= BORDER_PARTICLE_RADIUS_SQ)
                                overworld.spawnParticle(org.bukkit.Particle.DUST, a, 1, 0,0,0, 0,
                                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255,64,64), 1.3f));
                            if (b.distanceSquared(pl) <= BORDER_PARTICLE_RADIUS_SQ)
                                overworld.spawnParticle(org.bukkit.Particle.DUST, b, 1, 0,0,0, 0,
                                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255,64,64), 1.3f));
                        }
                    }
                    nextBorderParticlesAt.put(p.getUniqueId(), now + BORDER_PARTICLE_INTERVAL_MS);
                }
            } catch (Throwable ignored) {}
        }

    // HUD (Nether via action bar), border (2-min window), and GM overlay
        for (Player p : players) {
            if (p.isDead()) continue;
            if (p.getWorld().getEnvironment() == World.Environment.NETHER) {
                Long nleft = netherLeftThisTick.get(p.getUniqueId());
                if (nleft == null) continue;
        String msg = ChatColor.AQUA + "Zeit im Nether: " + ChatColor.WHITE + formatTime(nleft);
                Long deadline = netherEvictDeadlineMs.get(p.getUniqueId());
                if (netherLockdownActive && deadline != null) {
                    long leftEvict = Math.max(0L, deadline - now);
            msg += ChatColor.GRAY + " • " + ChatColor.RED + "Rauswurf in: " + ChatColor.WHITE + formatTime(leftEvict);
                }
                // Append GM overlay if GM, include shrink countdown and near-border count
                if (gameMasters.contains(p.getUniqueId())) {
                    String overlay = buildGmOverlay(p, players);
                    long leftMsGm = Math.max(0L, nextShrinkAtGetter.getAsLong() - now);
                    int leftS = (int) ((leftMsGm + 999) / 1000);
                    String gmExtra = buildGmBorderViewInfo(p, playersNearBorder, leftS, now);
                    msg += ChatColor.DARK_GRAY + "  •  " + gmExtra;
                    if (!overlay.isEmpty()) msg += ChatColor.DARK_GRAY + "  •  " + overlay;
                }
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
                continue;
            }
            // Show border guidance only in the last 2 minutes before shrink
            if (untilShrink > 0 && untilShrink <= 120_000L) {
                if (hudHidden.contains(p.getUniqueId())) continue;
                if (warnedThisTick.contains(p.getUniqueId())) continue;
                WorldBorder pwb = p.getWorld().getWorldBorder();
                Location pc = pwb.getCenter();
                                // Calculate guidance
                                String arrow = arrowToCenter(p.getLocation(), pc);
                                String dirText = directionToCenterText(p.getLocation(), pc);
                                StringBuilder sb = new StringBuilder();
                                // Short headline: direction only
                                sb.append(ChatColor.GOLD).append("Border: ")
                                    .append(ChatColor.YELLOW).append(dirText).append(" ").append(arrow);

                                // Distance to current border and after next shrink (like a storm/zone helper)
                                                try {
                                        Location ploc = p.getLocation();
                                        double cx = pc.getX(), cz = pc.getZ();
                                        double dx = Math.abs(ploc.getX() - cx);
                                        double dz = Math.abs(ploc.getZ() - cz);
                                        double halfNow = pwb.getSize() / 2.0;
                                        double offsetNow = halfNow - Math.max(dx, dz); // >=0 inside, <0 outside
                                        int distNow = (int) Math.floor(Math.abs(offsetNow));

                                        int totalShrinks = Math.max(1, projectDurationMinutes / Math.max(1, borderShrinkEveryMinutes));
                                        double step = (borderStartSize - borderEndSize) / totalShrinks;
                                        double nextSize = Math.max(borderEndSize, pwb.getSize() - step);
                                        double halfNext = nextSize / 2.0;
                                        double offsetNext = halfNext - Math.max(dx, dz);
                                        int distNext = (int) Math.floor(Math.abs(offsetNext));

                                                                                                                ChatColor distColor = (offsetNow < 0 || distNow <= 10) ? ChatColor.RED : (distNow <= 30 ? ChatColor.YELLOW : ChatColor.WHITE);
                                                                                                                sb.append(ChatColor.GRAY).append(" • ")
                                                                                                                    .append(ChatColor.GOLD).append("B: ")
                                                                                                                    .append(distColor).append(distNow)
                                                                                                                    .append(ChatColor.GRAY).append(" → ")
                                                                                                                    .append(ChatColor.WHITE).append(distNext);

                                                        // Time estimate until border reaches this position (approx.)
                                                        double distToEdge = Math.max(dx, dz) * 2.0; // diameter at player's radius
                                                        double sizeNow = pwb.getSize();
                            if (step > 0.0 && sizeNow > distToEdge) {
                                // ETA omitted intentionally to keep HUD short
                            }
                                } catch (Throwable ignored) {}
                long leftSec = Math.max(0L, (untilShrink + 999) / 1000);
                sb.append(ChatColor.GRAY).append(" [").append(ChatColor.WHITE).append(leftSec).append("s").append(ChatColor.GRAY).append("]");
                Long nleft = netherLeftThisTick.get(p.getUniqueId());
                if (nleft != null) {
                    sb.append(ChatColor.GRAY).append(" • ").append(ChatColor.AQUA)
                      .append("Nether: ").append(ChatColor.WHITE).append(formatTime(nleft));
                }
                // Teilnehmer (kein GM): ersetze HUD durch Live-Border-View Info
                if (!gameMasters.contains(p.getUniqueId())) {
                    String info = buildPlayerBorderViewInfo(p, (int)leftSec, now);
                    if (info != null && !info.isEmpty()) {
                        sb = new StringBuilder(info);
                    }
                }
                // Append GM overlay if GM, include shrink countdown and near-border count
                if (gameMasters.contains(p.getUniqueId())) {
                    String overlay = buildGmOverlay(p, players);
                    long leftMsGm = Math.max(0L, nextShrinkAtGetter.getAsLong() - now);
                    int leftS = (int) ((leftMsGm + 999) / 1000);
                    String gmExtra = buildGmBorderViewInfo(p, playersNearBorder, leftS, now);
                    sb.append(ChatColor.DARK_GRAY).append("  •  ").append(gmExtra);
                    if (!overlay.isEmpty()) sb.append(ChatColor.DARK_GRAY).append("  •  ").append(overlay);
                }
                sb.append(ChatColor.DARK_GRAY).append(" (/hud)");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(sb.toString()));
            } else if (gameMasters.contains(p.getUniqueId())) {
                // No regular HUD shown: still show GM overlay so it appears consistently
                String overlay = buildGmOverlay(p, players);
                if (!overlay.isEmpty()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(overlay));
                }
            }
    }
    // Update scoreboard periodically also during active gameplay
    updateScoreboard(players, now);
    }

    // Build GM overlay: rotates over all other players, shows name, coords, dimension, and distance
    private String buildGmOverlay(Player gm, List<Player> snapshotPlayers) {
        try {
            // Only for GMs
            if (!gameMasters.contains(gm.getUniqueId())) return "";
            // Collect targets: online non-spectator players (including streamers), excluding the GM
            List<Player> targets = new ArrayList<>();
            for (Player o : snapshotPlayers) {
                if (o.getUniqueId().equals(gm.getUniqueId())) continue;
                if (o.getGameMode() == org.bukkit.GameMode.SPECTATOR || o.isDead()) continue;
                targets.add(o);
            }
            if (targets.isEmpty()) return ChatColor.GRAY + "Keine Spieler";
            int n = targets.size();
            int idx = gmOverlayIndex.getOrDefault(gm.getUniqueId(), 0);
            if (idx >= n) idx = 0;
            Player t = targets.get(idx);
            gmOverlayIndex.put(gm.getUniqueId(), (idx + 1) % n);
            Location loc = t.getLocation();
            int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
            World.Environment env = (loc.getWorld() != null ? loc.getWorld().getEnvironment() : World.Environment.NORMAL);
            String dim = (env == World.Environment.NETHER ? "NET" : (env == World.Environment.THE_END ? "END" : "OW"));
            double dist = 0.0;
            try { if (gm.getWorld().equals(loc.getWorld())) dist = gm.getLocation().distance(loc); } catch (Throwable ignored) {}
            String idxPart = ChatColor.DARK_PURPLE + "[" + (idx+1) + "/" + n + "]" + ChatColor.RESET + " ";
            String namePart = ChatColor.GOLD + t.getName();
            String coordsPart = ChatColor.WHITE + "(" + x + ", " + y + ", " + z + ")";
            String distPart = ChatColor.AQUA + " ~" + (int)Math.round(dist) + ChatColor.GRAY + " Blöcke";
            String dimPart = ChatColor.DARK_GRAY + " (" + dim + ")";
            return idxPart + namePart + ChatColor.GRAY + " " + coordsPart + ChatColor.GRAY + "  " + distPart + dimPart;
        } catch (Throwable t) {
            return "";
        }
    }

    // Zusätzliche Info für GMs: zeigt anhand Blickrichtung an, ob Blickrichtung sicherer wird oder zur Border führt
    private String buildGmBorderViewInfo(Player p, int playersNearBorder, int shrinkLeftSeconds, long nowMs) {
        try {
            WorldBorder wb = p.getWorld().getWorldBorder();
            Location c = wb.getCenter();
            Location loc = p.getLocation();
            double half = wb.getSize() / 2.0;
            double dx = loc.getX() - c.getX();
            double dz = loc.getZ() - c.getZ();
            double maxAbs = Math.max(Math.abs(dx), Math.abs(dz));
            double distToEdge = half - maxAbs; // positiv = innen, negativ = draußen
            org.bukkit.util.Vector dir = loc.getDirection().clone().setY(0);
            if (dir.lengthSquared() < 1e-4) dir = new org.bukkit.util.Vector(0,0,1);
            dir.normalize();
            // Punkt wenige Blöcke in Blickrichtung
            double sampleAhead = 8.0; // 8 Blöcke vorn prüfen
            double ax = dx + dir.getX()*sampleAhead;
            double az = dz + dir.getZ()*sampleAhead;
            double aheadMaxAbs = Math.max(Math.abs(ax), Math.abs(az));
            double distAhead = half - aheadMaxAbs;
            // Trend bestimmen
            String trend;
            if (distAhead > distToEdge + 1.0) { // weiter weg von Border
                trend = "sicherer";
            } else if (distAhead < distToEdge - 1.0) { // näher an Border
                trend = "näher";
            } else {
                trend = "gleich";
            }
            // Zeit bis Border diesen Radius erreicht: approximativ
            String eta = "-";
            try {
                double sizeNow = wb.getSize();
                int totalShrinks = Math.max(1, projectDurationMinutes / Math.max(1, borderShrinkEveryMinutes));
                double step = (borderStartSize - borderEndSize) / totalShrinks;
                if (step > 0.0 && distToEdge > 0) {
                    // Anzahl Shrinks bis Kante erreicht (Radius *2)
                    double diameterAtPlayer = maxAbs * 2.0;
                    if (sizeNow > diameterAtPlayer) {
                        int kNeeded = (int) Math.ceil((sizeNow - diameterAtPlayer) / step);
                        long perStepMs = Math.max(60_000L, borderShrinkEveryMinutes * 60_000L);
                        long leftMsFirst = Math.max(0L, nextShrinkAtGetter.getAsLong() - nowMs);
                        long tMs = leftMsFirst + Math.max(0, kNeeded - 1) * perStepMs;
                        int sec = (int) ((tMs + 999) / 1000);
                        eta = sec + "s";
                    }
                }
            } catch (Throwable ignored) {}
            String dirWord = directionToCenterText(loc, c.toLocation(p.getWorld()));
            String base = ChatColor.GRAY + "[Shrink:" + ChatColor.WHITE + shrinkLeftSeconds + "s" + ChatColor.GRAY + "]";
            base += ChatColor.GRAY + " [Rand:" + ChatColor.WHITE + (int)Math.max(0, distToEdge) + ChatColor.GRAY + "]";
            base += ChatColor.GRAY + " [Blick:" + ChatColor.WHITE + trend + ChatColor.GRAY + "]";
            base += ChatColor.GRAY + " [Rtg Mitte:" + ChatColor.WHITE + dirWord + ChatColor.GRAY + "]";
            base += ChatColor.GRAY + " [ETA:" + ChatColor.WHITE + eta + ChatColor.GRAY + "]";
            base += ChatColor.GRAY + " [Nahe:" + ChatColor.WHITE + playersNearBorder + ChatColor.GRAY + "]";
            return base;
        } catch (Throwable ignored) {
            return ChatColor.GRAY + "[GM]";
        }
    }

    // Spieler HUD: Live Sicht + Richtung weg von Border + ETA bis Border erreicht
    private String buildPlayerBorderViewInfo(Player p, int shrinkLeftSeconds, long nowMs) {
        try {
            WorldBorder wb = p.getWorld().getWorldBorder();
            Location c = wb.getCenter();
            Location loc = p.getLocation();
            double half = wb.getSize() / 2.0;
            double dx = loc.getX() - c.getX();
            double dz = loc.getZ() - c.getZ();
            double maxAbs = Math.max(Math.abs(dx), Math.abs(dz));
            double distToEdge = half - maxAbs; // >=0 innerhalb
            if (distToEdge < 0) return null; // außerhalb handled elsewhere
            org.bukkit.util.Vector dir = loc.getDirection().clone().setY(0);
            if (dir.lengthSquared() < 1e-4) dir = new org.bukkit.util.Vector(0,0,1);
            dir.normalize();
            double ax = dx + dir.getX()*8.0;
            double az = dz + dir.getZ()*8.0;
            double aheadMaxAbs = Math.max(Math.abs(ax), Math.abs(az));
            double distAhead = half - aheadMaxAbs;
            String trend;
            if (distAhead > distToEdge + 1.0) { trend = "sicherer"; }
            else if (distAhead < distToEdge - 1.0) { trend = "näher"; }
            else { trend = "gleich"; }
            // ETA berechnen (numerisch + String)
            String eta = "-";
            int etaSecondsValue = -1; // numerische ETA bis Border deinen Radius erreicht
            try {
                double sizeNow = wb.getSize();
                int totalShrinks = Math.max(1, projectDurationMinutes / Math.max(1, borderShrinkEveryMinutes));
                double step = (borderStartSize - borderEndSize) / totalShrinks;
                if (step > 0.0) {
                    double diameterAtPlayer = maxAbs * 2.0;
                    if (sizeNow > diameterAtPlayer) {
                        int kNeeded = (int) Math.ceil((sizeNow - diameterAtPlayer) / step);
                        long perStepMs = Math.max(60_000L, borderShrinkEveryMinutes * 60_000L);
                        long leftMsFirst = Math.max(0L, nextShrinkAtGetter.getAsLong() - nowMs);
                        long tMs = leftMsFirst + Math.max(0, kNeeded - 1) * perStepMs;
                        int sec = (int) ((tMs + 999) / 1000);
                        etaSecondsValue = sec;
                        if (sec >= 60) eta = (sec/60) + "m" + (sec%60==0? "" : (" " + (sec%60) + "s")); else eta = sec + "s";
                    }
                }
            } catch (Throwable ignored) {}
            String centerDir = directionToCenterText(loc, c.toLocation(p.getWorld()));
            String centerArrow = arrowToCenter(loc, c.toLocation(p.getWorld()));
            String lookDir = lookDirectionText(loc.getYaw());
            int distEdgeInt = (int)Math.max(0, distToEdge);
            // Risiko / Safety Level bestimmen
            String safetySuffix;
            if (etaSecondsValue >= 300 && distEdgeInt > 50 && !"näher".equalsIgnoreCase(trend)) {
                safetySuffix = ChatColor.GRAY + " • " + ChatColor.GREEN + "Sicher!";
            } else if (distEdgeInt <= 10 || (etaSecondsValue > 0 && etaSecondsValue <= 60)) {
                safetySuffix = ChatColor.GRAY + " • " + ChatColor.RED + "GEFAHR";
            } else if (distEdgeInt <= 30 || (etaSecondsValue > 0 && etaSecondsValue <= 120)) {
                safetySuffix = ChatColor.GRAY + " • " + ChatColor.YELLOW + "Achtung";
            } else if (etaSecondsValue >= 180) {
                safetySuffix = ChatColor.GRAY + " • " + ChatColor.GREEN + "du bist sicher!";
            } else {
                safetySuffix = ""; // neutral
            }

            return ChatColor.GRAY + "D: " + ChatColor.WHITE + distEdgeInt
                + ChatColor.GRAY + " • Lauf: " + ChatColor.GOLD + centerDir + centerArrow
                + ChatColor.GRAY + " • Blick: " + ChatColor.WHITE + lookDir
            //    + ChatColor.GRAY + " • " + trendColor + trend
                + ChatColor.GRAY + " • ETA: " + ChatColor.WHITE + eta
                + safetySuffix;
        } catch (Throwable ignored) { return ""; }
    }

    private String lookDirectionText(float yaw) {
        // Normalize yaw to 0..360 (Minecraft yaw: 0=-Z(South), 90=-X(West))
        float y = yaw;
        while (y < 0) y += 360f; while (y >= 360f) y -= 360f;
        // Map to 8 directions (rotate so 0=Süden)
        if (y >= 337.5 || y < 22.5) return "Süden";
        if (y < 67.5) return "Südwest";
        if (y < 112.5) return "Westen";
        if (y < 157.5) return "Nordwest";
        if (y < 202.5) return "Norden";
        if (y < 247.5) return "Nordost";
        if (y < 292.5) return "Osten";
        return "Südost";
    }

    // Removed facingToCenterText — kept HUD short

    

    private void updateTabHeaderFooter() {
        long now = System.currentTimeMillis();
        boolean isPaused = paused.getAsBoolean();
        boolean running = gameRunning.getAsBoolean();

        WorldBorder wb = plugin.getServer().getWorlds().get(0).getWorldBorder();
        int borderSize = (int) wb.getSize();

    String title = ChatColor.DARK_RED.toString() + ChatColor.BOLD + "✦ " + ChatColor.WHITE + "TRIVA" + ChatColor.DARK_RED + " ✦";
        String status = isPaused
                ? ChatColor.YELLOW + "⏸ Pause aktiv"
                : ChatColor.GRAY + "⏳ Projekt: " + ChatColor.WHITE + (running ? formatTime(projectRemainingMs.getAsLong()) : "wartet");

        String line2 = ChatColor.GRAY + "❖ Border: " + ChatColor.WHITE + borderSize;
        if (running) {
            long left = Math.max(0L, nextShrinkAtGetter.getAsLong() - now);
            line2 += ChatColor.GRAY + "  |  " + ChatColor.GRAY + "⏱ Shrink: " + ChatColor.WHITE + formatTime(left);
        }

    String line3 = ChatColor.GRAY + "👥 Teams: " + ChatColor.WHITE + teams.size();

        String header = String.join("\n", java.util.Arrays.asList(title, status, line2, line3));
        boolean headerChanged = !header.equals(lastTabHeader);
        lastTabHeader = header;

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            // Build per-player footer with Shrink + personal ETA
            String footer;
            if (running) {
                long leftMs = Math.max(0L, nextShrinkAtGetter.getAsLong() - now);
                int leftMM = (int) (leftMs / 60_000L);
                int leftSS = (int) ((leftMs % 60_000L) / 1000L);

                // Compute ETA for this player (approx) using same logic as action bar
                String etaStr = "-";
                try {
                    WorldBorder pwb2 = p.getWorld().getWorldBorder();
                    Location pc = pwb2.getCenter().toLocation(p.getWorld());
                    Location ploc = p.getLocation();
                    double cx = pc.getX(), cz = pc.getZ();
                    double dx = Math.abs(ploc.getX() - cx);
                    double dz = Math.abs(ploc.getZ() - cz);
                    double sizeNow = pwb2.getSize();
                    int totalShrinks = Math.max(1, projectDurationMinutes / Math.max(1, borderShrinkEveryMinutes));
                    double step = (borderStartSize - borderEndSize) / totalShrinks;
                    if (step > 0.0) {
                        double distToEdgeDiameter = Math.max(dx, dz) * 2.0;
                        if (sizeNow > distToEdgeDiameter) {
                            int kNeeded = (int) Math.ceil((sizeNow - distToEdgeDiameter) / step);
                            long perStepMs = Math.max(60_000L, borderShrinkEveryMinutes * 60_000L);
                            long tMs = leftMs + Math.max(0, kNeeded - 1) * perStepMs;
                            double deltaBeforeFinal = (kNeeded - 1) * step;
                            double remainingInFinal = Math.max(0.0, (sizeNow - distToEdgeDiameter) - deltaBeforeFinal);
                            double frac = Math.min(1.0, Math.max(0.0, remainingInFinal / step));
                            tMs += (long) (frac * (borderShrinkLerpSeconds * 1000L));
                            int emm = (int) (tMs / 60_000L);
                            int ess = (int) ((tMs % 60_000L) / 1000L);
                            etaStr = String.format(java.util.Locale.ROOT, "~%dm %02ds", emm, ess);
                        }
                    }
                } catch (Throwable ignored) {}

                footer = BRAND_DOMAIN + "\n"
                        + ChatColor.GRAY + "⏱ Shrink in: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%d:%02d", leftMM, leftSS)
                        + ChatColor.GRAY + "  |  " + ChatColor.GRAY + "Deine ETA: " + ChatColor.WHITE + etaStr;
            } else {
                footer = BRAND_DOMAIN + "\n" + ChatColor.DARK_GRAY + "/help" + ChatColor.GRAY + " • " + ChatColor.DARK_GRAY + "/regeln";
            }

            String prev = lastTabFooterByPlayer.get(p.getUniqueId());
            if (!headerChanged && footer.equals(prev)) continue;
            lastTabFooterByPlayer.put(p.getUniqueId(), footer);
            try { p.setPlayerListHeaderFooter(header, footer); } catch (Throwable ignored) {}
        }
    }

    public void updateScoreboardFor(Player p) { updateScoreboard(Collections.singletonList(p), System.currentTimeMillis(), false); }
    // Force=true bypasses global throttle for immediate refresh (e.g., on join/world change)
    public void updateScoreboardFor(Player p, boolean force) { updateScoreboard(Collections.singletonList(p), System.currentTimeMillis(), force); }
    public void refreshAllScoreboards(boolean force) {
        updateScoreboard(new java.util.ArrayList<>(plugin.getServer().getOnlinePlayers()), System.currentTimeMillis(), force);
    }

    private void updateScoreboard(List<Player> players, long now) { updateScoreboard(players, now, false); }

    private void updateScoreboard(List<Player> players, long now, boolean force) {
        if (!force && now < nextScoreboardUpdateAt) return;
        nextScoreboardUpdateAt = now + SCOREBOARD_UPDATE_INTERVAL_MS;
        ScoreboardManager m = Bukkit.getScoreboardManager(); if (m == null) return;

        for (Player p : players) {
            String proj = (gameRunning.getAsBoolean() ? formatTime(projectRemainingMs.getAsLong()) : "wartet");
            WorldBorder wb = p.getWorld().getWorldBorder();
            String shrink = "";
            if (gameRunning.getAsBoolean()) {
                long left = Math.max(0L, nextShrinkAtGetter.getAsLong() - now);
                shrink = formatTime(left);
            }
            String nether = ""; // Keeping simple; detailed per-player remaining shown in HUD already
            int teamCount = (teams != null ? teams.size() : 0);

            String killTop = ""; // removed display
            String tn = playerTeam.get(p.getUniqueId());
            String readyFlag = "";
            if (tn != null) {
                boolean r = Boolean.TRUE.equals(teamReady != null ? teamReady.get(tn) : Boolean.FALSE);
                readyFlag = r ? "R1" : "R0";
            }
            String pausedFlag = paused.getAsBoolean() ? "1" : "0";
            String hash = proj + '|' + (int)wb.getSize() + '|' + shrink + '|' + nether + '|' + killTop + '|' + (tn==null?"":tn) + '|' + readyFlag + '|' + pausedFlag + '|' + teamCount;

            String lastHash = lastScoreboardHash.get(p.getUniqueId());
            Long lastAt = lastScoreboardAt.get(p.getUniqueId());
            if (hash.equals(lastHash) && lastAt != null && now - lastAt < 10_000L) continue;

            Scoreboard board = m.getNewScoreboard();
            Objective obj = board.registerNewObjective("triva", "dummy",
            ChatColor.DARK_RED.toString() + ChatColor.BOLD + "✦ " + ChatColor.WHITE + "TRIVA" + ChatColor.DARK_RED + " ✦");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            // Tab sorting/groups: create scoreboard Teams per LARO team (A→Z)
            if (!teams.isEmpty()) {
                java.util.List<String> teamNames = new java.util.ArrayList<>(teams.keySet());
                teamNames.sort(String.CASE_INSENSITIVE_ORDER);
                int idx = 0;
                for (String tname : teamNames) {
                    String teamKey = String.format(java.util.Locale.ROOT, "t%03d", idx++);
                    Team t = board.registerNewTeam(teamKey);
                    ChatColor tcolor = (teamColors != null ? teamColors.getOrDefault(tname, ChatColor.WHITE) : ChatColor.WHITE);
                    try { t.setColor(tcolor); } catch (Throwable ignored) {}
                    // Do not show team name as prefix in tab; rely on team color only
                    try { t.setPrefix(""); } catch (Throwable ignored) {}
                    java.util.List<java.util.UUID> members = teams.getOrDefault(tname, java.util.Collections.emptyList());
                    for (java.util.UUID mid : members) {
                        Player online = Bukkit.getPlayer(mid);
                        if (online != null) t.addEntry(online.getName());
                    }
                }
            }

            String projLine = ChatColor.GRAY + "⏳ Projekt: " + ChatColor.WHITE + proj;
            String bszLine = ChatColor.GRAY + "❖ Border: " + ChatColor.WHITE + (int) wb.getSize() + " Blöcke";
            String teamsLine = ChatColor.GRAY + "👥 Teams: " + ChatColor.WHITE + teamCount;
            String shrinkLine = gameRunning.getAsBoolean() ? ChatColor.GRAY + "⏱ Shrink: " + ChatColor.WHITE + shrink : "";
            // Top-Killer Anzeige vollständig entfernt
            String teamLine = "";
            if (tn != null) {
                boolean r = Boolean.TRUE.equals(teamReady != null ? teamReady.get(tn) : Boolean.FALSE);
                String lamp = r ? ChatColor.GREEN + " ✓" : ChatColor.RED + " ✗";
                teamLine = ChatColor.GOLD + "⚑ Team: " + ChatColor.WHITE + tn + lamp;
            }

            // Personal ETA line: when will the border reach the player's current radius (approx)
            String etaLine = "";
            if (gameRunning.getAsBoolean()) {
                try {
                    double sizeNow = wb.getSize();
                    int totalShrinks = Math.max(1, projectDurationMinutes / Math.max(1, borderShrinkEveryMinutes));
                    double step = (borderStartSize - borderEndSize) / totalShrinks;
                    if (step > 0.0) {
                        Location pc = wb.getCenter();
                        double dx = Math.abs(p.getLocation().getX() - pc.getX());
                        double dz = Math.abs(p.getLocation().getZ() - pc.getZ());
                        double distToEdgeDiameter = Math.max(dx, dz) * 2.0;
                        if (sizeNow > distToEdgeDiameter) {
                            long leftMs = Math.max(0L, nextShrinkAtGetter.getAsLong() - now);
                            int kNeeded = (int) Math.ceil((sizeNow - distToEdgeDiameter) / step);
                            long perStepMs = Math.max(60_000L, borderShrinkEveryMinutes * 60_000L);
                            long tMs = leftMs + Math.max(0, kNeeded - 1) * perStepMs;
                            double deltaBeforeFinal = (kNeeded - 1) * step;
                            double remainingInFinal = Math.max(0.0, (sizeNow - distToEdgeDiameter) - deltaBeforeFinal);
                            double frac = Math.min(1.0, Math.max(0.0, remainingInFinal / step));
                            tMs += (long) (frac * (borderShrinkLerpSeconds * 1000L));
                            int mm = (int) (tMs / 60_000L);
                            int ss = (int) ((tMs % 60_000L) / 1000L);
                            ChatColor etaColor = (tMs <= 30_000L) ? ChatColor.RED : (tMs <= 120_000L ? ChatColor.YELLOW : ChatColor.WHITE);
                            etaLine = ChatColor.GRAY + "⏱ Border @ dir: " + etaColor + "~" + mm + "m " + ss + "s";
                        }
                    }
                } catch (Throwable ignored) {}
            }

            int score = 15;
            if (paused.getAsBoolean()) obj.getScore(ChatColor.YELLOW + "⏸ PAUSE AKTIV").setScore(score--);
            obj.getScore(projLine).setScore(score--);
            obj.getScore(bszLine).setScore(score--);
            obj.getScore(teamsLine).setScore(score--);
            if (!shrinkLine.isEmpty()) obj.getScore(shrinkLine).setScore(score--);
            if (!etaLine.isEmpty()) obj.getScore(etaLine).setScore(score--);
            // Top-Killer Zeile bewusst entfernt (Anforderung)
            if (!teamLine.isEmpty()) obj.getScore(teamLine).setScore(score--);
            obj.getScore(BRAND_DOMAIN).setScore(score--);

            p.setScoreboard(board);
            lastScoreboardHash.put(p.getUniqueId(), hash);
            lastScoreboardAt.put(p.getUniqueId(), now);
        }
    }

    private String formatTime(long ms) {
        long totalSec = Math.max(0L, ms) / 1000L;
        long h = totalSec / 3600L, m = (totalSec % 3600L) / 60L, s = totalSec % 60L;
    if (h > 0) return String.format("%dh %02dmin %02ds", h, m, s);
    return String.format("%02dmin %02ds", m, s);
    }

    private String arrowToCenter(Location from, Location center) {
        double dx = center.getX() - from.getX();
        double dz = center.getZ() - from.getZ();
        double adx = Math.abs(dx), adz = Math.abs(dz);
        if (adx > adz * 1.7) {
            return dx > 0 ? "→" : "←";
        } else if (adz > adx * 1.7) {
            return dz > 0 ? "↓" : "↑";
        } else {
            if (dx > 0 && dz < 0) return "↗";
            if (dx > 0 && dz > 0) return "↘";
            if (dx < 0 && dz < 0) return "↖";
            return "↙";
        }
    }

    private String directionToCenterText(Location from, Location center) {
        double dx = center.getX() - from.getX();
        double dz = center.getZ() - from.getZ();
        double adx = Math.abs(dx), adz = Math.abs(dz);
        if (adx > adz * 1.7) {
            return dx > 0 ? "Osten" : "Westen";
        } else if (adz > adx * 1.7) {
            return dz > 0 ? "Süden" : "Norden";
        } else {
            if (dx > 0 && dz < 0) return "Nordost";
            if (dx > 0 && dz > 0) return "Südost";
            if (dx < 0 && dz < 0) return "Nordwest";
            return "Südwest";
        }
    }

    private void scheduleNetherEviction(Player p, long delaySeconds) {
        // Cancel existing eviction if any
        BukkitTask existing = netherEvictTasks.remove(p.getUniqueId());
        if (existing != null) { try { existing.cancel(); } catch (Throwable ignored) {} }
        // Schedule new eviction
        long deadline = System.currentTimeMillis() + delaySeconds * 1000L;
        netherEvictDeadlineMs.put(p.getUniqueId(), deadline);
        BukkitTask t = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && p.getWorld().getEnvironment() == World.Environment.NETHER) {
                Location back = lastOverworldPos.get(p.getUniqueId());
                if (back == null) {
                    World overworld = plugin.getServer().getWorlds().get(0);
                    WorldBorder wb = overworld.getWorldBorder();
                    double cx = wb.getCenter().getX();
                    double cz = wb.getCenter().getZ();
                    int y = overworld.getHighestBlockYAt((int) Math.round(cx), (int) Math.round(cz)) + 1;
                    back = new Location(overworld, cx + 0.5, y, cz + 0.5);
                }
                p.sendMessage(ChatColor.RED + "Nether gesperrt: Du wirst zurück in die Overworld teleportiert.");
                try { p.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN); } catch (Throwable ignored) {}
            }
            netherEvictTasks.remove(p.getUniqueId());
            netherEvictDeadlineMs.remove(p.getUniqueId());
        }, delaySeconds * 20L);
        netherEvictTasks.put(p.getUniqueId(), t);
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (!gameRunning.getAsBoolean() || paused.getAsBoolean()) return;
        if (!netherLockdownActive) return;
        if (e.getTo() == null) return;
        World.Environment toEnv = e.getTo().getWorld().getEnvironment();
        if (toEnv == World.Environment.NETHER) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Nether ist gesperrt. Du kannst ihn nicht mehr betreten.");
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null) return;
        Player p = e.getPlayer();
        World.Environment fromEnv = e.getFrom().getWorld().getEnvironment();
        World.Environment toEnv = e.getTo().getWorld().getEnvironment();

        // Track last overworld location
        if (toEnv == World.Environment.NORMAL) {
            lastOverworldPos.put(p.getUniqueId(), e.getTo().clone());
            overworldDirty = true;
        }

        if (!gameRunning.getAsBoolean() || paused.getAsBoolean()) return;
        if (!netherLockdownActive) return;

        // Prevent entering Nether via any teleport when lockdown
        if (toEnv == World.Environment.NETHER && fromEnv != World.Environment.NETHER) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Nether ist gesperrt. Du kannst ihn nicht mehr betreten.");
        }
    }
    // ===== Persistence helpers =====
    private void initLastOverworldStorage() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            lastOverworldFile = new File(plugin.getDataFolder(), "last_overworld.yml");
            if (!lastOverworldFile.exists()) {
                lastOverworldFile.createNewFile();
            }
            lastOverworldCfg = YamlConfiguration.loadConfiguration(lastOverworldFile);
            // load into map
            for (String key : lastOverworldCfg.getKeys(false)) {
                try {
                    java.util.UUID id = java.util.UUID.fromString(key);
                    String world = lastOverworldCfg.getString(key + ".world");
                    double x = lastOverworldCfg.getDouble(key + ".x");
                    double y = lastOverworldCfg.getDouble(key + ".y");
                    double z = lastOverworldCfg.getDouble(key + ".z");
                    float yaw = (float) lastOverworldCfg.getDouble(key + ".yaw", 0.0);
                    float pitch = (float) lastOverworldCfg.getDouble(key + ".pitch", 0.0);
                    World w = (world != null) ? Bukkit.getWorld(world) : null;
                    if (w != null) {
                        Location loc = new Location(w, x, y, z, yaw, pitch);
                        lastOverworldPos.put(id, loc);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private void saveAllLastOverworld() {
        if (lastOverworldCfg == null || lastOverworldFile == null) return;
        for (Map.Entry<java.util.UUID, Location> en : lastOverworldPos.entrySet()) {
            String key = en.getKey().toString();
            Location loc = en.getValue();
            if (loc.getWorld() == null) continue;
            lastOverworldCfg.set(key + ".world", loc.getWorld().getName());
            lastOverworldCfg.set(key + ".x", loc.getX());
            lastOverworldCfg.set(key + ".y", loc.getY());
            lastOverworldCfg.set(key + ".z", loc.getZ());
            lastOverworldCfg.set(key + ".yaw", loc.getYaw());
            lastOverworldCfg.set(key + ".pitch", loc.getPitch());
        }
        try { lastOverworldCfg.save(lastOverworldFile); } catch (Throwable ignored) {}
    }
}
