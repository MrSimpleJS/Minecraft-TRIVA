package de.varo;

import de.varo.features.SupplyDropService;
import de.varo.features.chat.ChatFeature;
import de.varo.features.guard.GuardFeature;
import de.varo.features.gm.GmFeature;
import de.varo.features.spectate.SpectateFeature;
import de.varo.features.anticheat.AntiCheatFeature;
import de.varo.features.specteam.SpecteamFeature;
import de.varo.features.hud.HudBorderFeature;
import de.varo.features.rules.RulesFeature;
import de.varo.features.protection.ProtectionFeature;
import de.varo.gui.GuiHelper;
import de.varo.services.GameService;
import de.varo.services.WhitelistService;
import de.varo.services.StreamerService;
import de.varo.services.TeamService;
import de.varo.services.TrackerService;
import de.varo.features.tracker.TrackerFeature;
import de.varo.services.JoinFlowService;
import de.varo.services.NetherService;
import de.varo.services.DeathService;
import de.varo.features.moderation.ModerationFeature;
import de.varo.features.moderation.ReportsFeature;
import de.varo.features.admin.AdminToolsFeature;
import de.varo.features.schedule.SessionSchedulerFeature;
import de.varo.features.combat.CombatTagFeature;
import de.varo.features.kills.KillstreakFeature;
import de.varo.features.review.ReviewFeature;
import de.varo.services.InfoService;
import de.varo.services.StatsService;
import de.varo.services.GmUiService;
import de.varo.services.CombatLoggerService;


import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
// Scoreboard handled in HudBorderFeature
import org.bukkit.NamespacedKey;

import java.io.File;
import java.util.*;
import de.varo.util.Messages;
import de.varo.util.TabNameFormatter;

@SuppressWarnings("deprecation")
public class VaroPlugin extends JavaPlugin implements Listener {

    // Services
    private SupplyDropService supplyDropService;
    private TeamService teamService;
    private StreamerService streamerService;
    private TrackerService trackerService;
    private GameService gameService;

    // Core collections
    private final Map<String, List<UUID>> teams = new LinkedHashMap<>();
    private final Map<UUID, String> playerTeam = new HashMap<>();
    private final Map<String, Boolean> teamReady = new HashMap<>();
    private final Map<UUID, String> pendingInvite = new HashMap<>();
    @SuppressWarnings("deprecation")
    private final Map<String, ChatColor> teamColors = new HashMap<>();
    @SuppressWarnings("deprecation")
    private final ChatColor[] availableColors = new ChatColor[]{
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW, ChatColor.AQUA,
            ChatColor.LIGHT_PURPLE, ChatColor.GOLD, ChatColor.DARK_AQUA, ChatColor.DARK_GREEN,
            ChatColor.DARK_PURPLE, ChatColor.DARK_RED, ChatColor.WHITE
    };
    private final Set<UUID> streamers = new HashSet<>();
    private final Set<UUID> gameMasters = new HashSet<>();
    private final Map<UUID, String> gmOldTabName = new HashMap<>();
    private final Deque<String> actionLog = new ArrayDeque<>(); // simple in-memory log

    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private final Map<UUID, Integer> playerDeaths = new HashMap<>();

    private final Set<UUID> frozen = new HashSet<>();
    private final Map<UUID, Long> spawnProtectionUntil = new HashMap<>();
    private final Map<UUID, Long> rejoinProtectionUntil = new HashMap<>();
    private final Map<UUID, Long> lastPvP = new HashMap<>();
    private final Map<UUID, Long> trackerCooldown = new HashMap<>();
    private final Map<UUID, Long> lastActive = new HashMap<>();
    private final Set<UUID> warnedAfk = new HashSet<>();
    // Moderation: staff chat and mutes/warns
    private final Set<UUID> staffChatEnabled = new HashSet<>();
    // Per-player streamer privacy (coords masking + click-to-reveal)
    private final Set<UUID> streamerPrivacy = new HashSet<>();
    private final Map<UUID, Long> mutedUntil = new HashMap<>();
    private final Map<UUID, Integer> warnCount = new HashMap<>();

    // Nether
    private final Map<UUID, Long> netherUsedMs = new HashMap<>();
    private final Map<UUID, Long> netherSessionStart = new HashMap<>();
    private final Map<UUID, Location> lastNetherPortal = new HashMap<>();
    private final Set<UUID> warned30 = new HashSet<>();
    private final Set<UUID> warned10 = new HashSet<>();
    private final Set<UUID> warned5 = new HashSet<>();
    private final Set<UUID> warned1 = new HashSet<>();
    private final Set<UUID> netherOvertime = new HashSet<>();

    // Config values
    private double borderStartSize, borderEndSize, borderCenterX, borderCenterZ;
    private int borderShrinkEveryMinutes, borderShrinkLerpSeconds;
    private int projectDurationMinutes;
    private long projectDurationMs;
    private int teamSpreadRadius;
    private long netherLimitMs;
    private boolean supplyDropEnabled;
    private int supplyDropInterval;
    private List<String> supplyDropLoot = new ArrayList<>();
    private long trackerCooldownMs;
    private long combatTagMs;
    private long rejoinProtectMs;
    private int loggerNpcSeconds;
    // spectatorMinDistance no longer used
    private long afkKickMs;
    private long shrinkIntervalMs;
    // HUD tuning
    private long hudScoreboardUpdateMs;
    private long hudBorderParticleIntervalMs;
    private double hudBorderNearDist;
    private double hudBorderParticleRadius;
    private double hudBorderParticleStep;
    // Privacy/Streamer
    private boolean privacyMaskCoords;
    private int spectateDelaySeconds;
    // AntiCheat mining thresholds
    private int acWindowMinutes;
    private int acDiamondPerBlocks;
    private double acDiamondMaxRatio;
    private int acDiamondFastFind;
    private int acDebrisPerBlocks;
    private double acDebrisMaxRatio;
    private int acDebrisFastFind;
    private int acBranchWithinSteps;
    private int acBranchWarnCount;

    // State
    private boolean gameRunning = false;
    private boolean blitzTriggered = false;
    private boolean paused = false;
    private boolean centerSet = false;
    private long gameStartTime = 0L;
    private long nextShrinkAt = 0L;
    // Finalphase (langsamer End-Shrink)
    private boolean finalPhaseActive = false;
    private long finalPhaseStartTime = 0L;
    private double finalPhaseStartSize = 0.0;
    private final long finalPhaseDurationMs = 60L * 60 * 1000L; // 1 Stunde
    private double finalPhaseMinSize = 25.0; // minimale Border
    private double finalPhaseDeathShrinkPer = 5.0; // zusätzlicher Shrink pro Tod in Finalphase
    private int finalPhaseDeaths = 0;
    private int finalPhaseTaskId = -1;
    private boolean finalPhaseDoneAnnounced = false;
    // Pause-Handling (Projektzeit & Shrink einfrieren)
    private long pauseStartAt = 0L;          // Zeitpunkt, ab dem pausiert wurde
    private long totalPausedMs = 0L;         // aufsummierte Pausenzeit
    private long shrinkPauseRemainingMs = 0L; // Restzeit bis zum nächsten Shrink beim Eintritt in Pause
    private long finalPhasePauseStartedAt = 0L; // Finalphase Fortschritt einfrieren

    // Persistence
    private de.varo.services.StateService stateService;
    // Rules acceptance tracking
    private final java.util.Set<java.util.UUID> rulesAccepted = new java.util.HashSet<>();

    // Keys
    private NamespacedKey trackerKey;
    private NamespacedKey specteamTeamKey;
    private NamespacedKey specteamPlayerKey;
    private NamespacedKey specteamSelectorKey;
    private NamespacedKey combatNpcKey;
    // GM toolkit keys
    private NamespacedKey gmTpMenuKey;
    private NamespacedKey gmReviewKey;
    private NamespacedKey gmMiningKey;
    private NamespacedKey gmReportsKey;

    // Runtime
    private int scoreboardTaskId = -1;
    // Border tracking
    private final Map<UUID, Integer> borderOutsideSeconds = new HashMap<>();
    // HUD toggle for action bar arrow
    private final Set<UUID> hudHidden = new HashSet<>();
    // HUD bursts (every 5 minutes show for 30 seconds)
    // No lobby: pre-game spawn is world center (0,0)

    // Spectate overlay tracking
    private final Map<UUID, UUID> forcedSpectateTargets = new HashMap<>(); // viewer -> target
    // Externalized features
    private SpectateFeature spectateFeature;
    private SpecteamFeature specteamFeature;
    private GuardFeature guardFeature;
    private de.varo.features.portal.PortalFeature portalFeature;
    private GmFeature gmFeature;
    private ChatFeature chatFeature;
    private AntiCheatFeature antiCheatFeature;
    private HudBorderFeature hudBorderFeature;
    private ModerationFeature moderationFeature;
    private ReportsFeature reportsFeature;
    private AdminToolsFeature adminToolsFeature;
    private SessionSchedulerFeature sessionScheduler;
    private CombatTagFeature combatTagFeature;
    private KillstreakFeature killstreakFeature;
    private WhitelistService whitelistService;
    private ReviewFeature reviewFeature;
    private InfoService infoService;
    private StatsService statsService;
    private GmUiService gmUiService;
    private de.varo.services.AutoCamService autoCamService;
    private CombatLoggerService combatLoggerService;
    private RulesFeature rulesFeature;
    private ProtectionFeature protectionFeature;
    private TrackerFeature trackerFeature;
    private JoinFlowService joinFlowService;
    private NetherService netherService;
    private DeathService deathService;
    private de.varo.services.FightRadarService fightRadarService;
    private de.varo.services.LagWatchService lagWatchService;
    private de.varo.services.LootLamaService lootLamaService;
    private de.varo.services.DummyPlayerService dummyPlayerService; // bewegt Debug-Dummys
    private de.varo.services.NpcDummyService npcDummyService; // echte Packet-NPCs (optional)

    // Recent interesting spots for spectators (simple cache): last drop, recent combat spots
    private final java.util.Deque<org.bukkit.Location> recentCombatSpots = new java.util.ArrayDeque<>();

    // GM overlay handled via HUD feature (no BossBar)

    // Performance: cache last scoreboard content per player and throttle updates
    private final Map<UUID, String> lastScoreboardHash = new HashMap<>();
    private final Map<UUID, Long> lastScoreboardAt = new HashMap<>();
    // Scoreboard throttling moved to HudBorderFeature

    // Whitelist TTL (configured) handled by WhitelistService
    private long wlCodeTtlMs;
    // Performance auto-throttle
    private boolean lagAutoThrottle;
    private double lagTpsLow;
    private double lagTpsRecover;
    private double lagScoreboardScale;
    private double lagBorderIntervalScale;
    private double lagBorderStepScale;

    // Debug toggles (configurable)
    private boolean debugAllowRuntimeShrinkAdjust = false;
    private boolean debugAllowRuntimeProjectTimeAdjust = false;

    // Combat-Logger NPC handled by CombatLoggerService

    // ==== Enable/Disable ====
    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            // Modularized config/state loading
        de.varo.services.ConfigService cfgSvc = new de.varo.services.ConfigService();
        de.varo.services.ConfigService.ConfigValues cfgVals = cfgSvc.load(this);
            applyConfig(cfgVals);

        stateService = new de.varo.services.StateService();
        stateService.init(this);
        de.varo.services.StateService.RuntimeState rs = stateService.load(borderCenterX, borderCenterZ, borderStartSize, playerTeam);
            applyState(rs);

            getServer().getPluginManager().registerEvents(this, this);
            // Core features
            // Initialize AntiCheat early so dependent features (e.g., moderation) can reference it
        antiCheatFeature = new AntiCheatFeature(
            gameMasters,
            streamers,
            this::logAction,
            this::getDataFolder,
            acWindowMinutes,
            acDiamondPerBlocks,
            acDiamondMaxRatio,
            acDiamondFastFind,
            acDebrisPerBlocks,
            acDebrisMaxRatio,
            acDebrisFastFind,
            acBranchWithinSteps,
            acBranchWarnCount
        );
        getServer().getPluginManager().registerEvents(antiCheatFeature, this);

            // Feature instances
            spectateFeature = new SpectateFeature(this, forcedSpectateTargets, () -> spectateDelaySeconds, (uuid) -> streamers.contains(uuid));
        specteamFeature = new de.varo.features.specteam.SpecteamFeature(
                    teams,
                    teamColors,
                    playerTeam,
                    gameMasters,
                    streamers,
                    forcedSpectateTargets.keySet(),
                    specteamTeamKey,
                    specteamPlayerKey,
                    specteamSelectorKey,
                    (viewer, target) -> {
                        if (streamers.contains(viewer.getUniqueId())) {
                // Streamer sollen frei zuschauen können -> Spectator statt Adventure+Freeze
                try { unfreezePlayer(viewer); } catch (Throwable ignored) {}
                viewer.setGameMode(org.bukkit.GameMode.SPECTATOR);
                try { viewer.setSpectatorTarget(target); } catch (Throwable ignored) {}
                            forcedSpectateTargets.put(viewer.getUniqueId(), target.getUniqueId());
                            spectateFeature.startOverlay(viewer, target, true);
                            viewer.sendMessage(net.md_5.bungee.api.ChatColor.LIGHT_PURPLE + "Streamer: " + net.md_5.bungee.api.ChatColor.GREEN + "du beobachtest jetzt " + target.getName() + ".");
                        } else {
                            if (viewer.getGameMode() != org.bukkit.GameMode.SPECTATOR) viewer.setGameMode(org.bukkit.GameMode.SPECTATOR);
                            viewer.setSpectatorTarget(target);
                            viewer.sendMessage(net.md_5.bungee.api.ChatColor.GREEN + "Du beobachtest jetzt " + target.getName() + ".");
                        }
                    }
            );
        moderationFeature = new ModerationFeature(gameMasters, mutedUntil, warnCount, actionLog, antiCheatFeature);
        guardFeature = new GuardFeature(frozen, forcedSpectateTargets, specteamSelectorKey, gameMasters, gmTpMenuKey, gmReviewKey, gmMiningKey, gmReportsKey);
            gmFeature = new GmFeature(gameMasters);
            chatFeature = new ChatFeature(playerTeam, teamColors, gameMasters, streamers);
            // Register listeners
        getServer().getPluginManager().registerEvents(spectateFeature, this);
        getServer().getPluginManager().registerEvents(specteamFeature, this);
        getServer().getPluginManager().registerEvents(guardFeature, this);
        // End/Nether portal rules
        portalFeature = new de.varo.features.portal.PortalFeature(this::getProjectRemainingMs, () -> netherLimitMs, netherUsedMs, netherSessionStart, lastNetherPortal);
        getServer().getPluginManager().registerEvents(portalFeature, this);
        getServer().getPluginManager().registerEvents(chatFeature, this);
            getServer().getPluginManager().registerEvents(gmFeature, this);
        // Core rules (enchanted golden apple ban)
        rulesFeature = new RulesFeature();
        getServer().getPluginManager().registerEvents(rulesFeature, this);
        // PvP and protection rules
        protectionFeature = new ProtectionFeature(
            () -> paused,
            frozen,
            gameMasters,
            spawnProtectionUntil,
            rejoinProtectionUntil,
            playerTeam,
            lastPvP
        );
        getServer().getPluginManager().registerEvents(protectionFeature, this);
        // Moderation/Admin extra features
        reportsFeature = new ReportsFeature(this, antiCheatFeature);
        getServer().getPluginManager().registerEvents(reportsFeature, this);
        adminToolsFeature = new AdminToolsFeature(this, this::freezePlayer, this::unfreezePlayer, (uuid) -> frozen.contains(uuid));
        getServer().getPluginManager().registerEvents(adminToolsFeature, this);

        // Combat tag and killstreaks
        combatTagFeature = new CombatTagFeature(this, combatTagMs,
            (uuid) -> de.varo.util.Permissions.isPrivileged(uuid, gameMasters, streamers));
        combatTagFeature.start();
        getServer().getPluginManager().registerEvents(combatTagFeature, this);
        killstreakFeature = new KillstreakFeature(this);
        getServer().getPluginManager().registerEvents(killstreakFeature, this);

        // Review & GM UI services
        reviewFeature = new ReviewFeature(this, antiCheatFeature, gameMasters);
        getServer().getPluginManager().registerEvents(reviewFeature, this);
        gmUiService = new GmUiService(this, gmTpMenuKey, gmReviewKey, gmMiningKey, gmReportsKey, specteamSelectorKey, reviewFeature);
        // AutoCam service
        autoCamService = new de.varo.services.AutoCamService(this, lastPvP, gameMasters);
        getServer().getPluginManager().registerEvents(gmUiService, this);

        // Combat-logger service
        combatLoggerService = new CombatLoggerService(this, combatNpcKey, combatTagMs, loggerNpcSeconds, lastPvP);
        getServer().getPluginManager().registerEvents(combatLoggerService, this);

        // Whitelist service (listener + commands)
        whitelistService = new WhitelistService(
            this,
            (uuid) -> gameMasters.contains(uuid) || Bukkit.getOfflinePlayer(uuid).isOp(),
            this::saveState,
            wlCodeTtlMs
        );
        getServer().getPluginManager().registerEvents(whitelistService, this);
        // Load existing whitelist state from file
        try {
            org.bukkit.configuration.file.YamlConfiguration y = stateService != null ? stateService.getYaml() : org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(getDataFolder(), "state.yml"));
            whitelistService.loadFromState(y);
        } catch (Throwable ignored) {}

        // Tracker feature registered below

        // Register command executors dynamically from plugin.yml
        de.varo.features.commands.CommandsFeature cmdExec = new de.varo.features.commands.CommandsFeature(this);
        try {
            java.util.Map<String, java.util.Map<String, Object>> cmds = getDescription().getCommands();
            if (cmds != null) {
                for (String name : cmds.keySet()) {
                    try {
                        if (getCommand(name) != null) {
                            getCommand(name).setExecutor(cmdExec);
                            getCommand(name).setTabCompleter(cmdExec); // enable auto-fill
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // Services
        supplyDropService = new SupplyDropService(this, () -> supplyDropLoot, (uuid) -> streamers.contains(uuid) || streamerPrivacy.contains(uuid), () -> privacyMaskCoords);
        if (supplyDropEnabled) { supplyDropService.startScheduled(supplyDropInterval); }

        teamService = new TeamService(teams, playerTeam, teamReady, pendingInvite, teamColors, availableColors,
            () -> gameRunning, this::updateTabName, this::saveState, this::logAction,
            (uuid) -> gameMasters.contains(uuid) || Bukkit.getOfflinePlayer(uuid).isOp());
        streamerService = new StreamerService(streamers, this::updateTabName);
        trackerService = new TrackerService(trackerCooldown, trackerCooldownMs, trackerKey);
        // Feature owning tracker recipe + usage
        trackerFeature = new de.varo.features.tracker.TrackerFeature(this, trackerService);
        getServer().getPluginManager().registerEvents(trackerFeature, this);

        // Info & stats services
        infoService = new InfoService(teams, playerTeam, teamColors, streamers, playerKills, () -> gameRunning, this::getProjectRemainingMs, () -> privacyMaskCoords);
        statsService = new StatsService(this, teams, playerKills, playerDeaths);

        // Rules service (book + acceptance)
        rulesService = new de.varo.services.RulesService(this, rulesAccepted, this::saveState) {
            // bridge: also push human-readable log line into actionLog for /logs filtering
            @Override
            public void markAccepted(org.bukkit.entity.Player p) {
                super.markAccepted(p);
                try { logAction("[rules] " + p.getName() + " hat die Regeln akzeptiert"); } catch (Throwable ignored) {}
            }
        };

        // Game service
        gameService = new GameService(
            this,
            teams,
            teamReady,
            gameMasters,
            spawnProtectionUntil,
            () -> gameRunning,
            (val) -> gameRunning = val,
            () -> paused,
            (val) -> paused = val,
            () -> centerSet,
            (val) -> centerSet = val,
            (ts) -> { gameStartTime = ts; saveState(); },
            () -> borderCenterX,
            (val) -> borderCenterX = val,
            () -> borderCenterZ,
            (val) -> borderCenterZ = val,
            () -> borderStartSize,
            () -> teamSpreadRadius,
            this::scheduleNextShrink,
            this::saveState,
            this::freezePlayer,
            this::unfreezePlayer,
            netherLimitMs,
            netherUsedMs,
            netherSessionStart,
            warned30, warned10, warned5, warned1, netherOvertime
        );

        // Hud/Border feature with its own scheduler
    hudBorderFeature = new HudBorderFeature(
            this,
            teams,
            teamColors,
            teamReady,
            playerTeam,
            gameMasters,
            streamers,
            lastActive,
            warnedAfk,
            hudHidden,
            borderOutsideSeconds,
            netherUsedMs,
            netherSessionStart,
            warned30, warned10, warned5, warned1, netherOvertime,
            lastScoreboardHash,
            lastScoreboardAt,
            () -> gameRunning,
            () -> paused,
            this::getProjectRemainingMs,
            () -> nextShrinkAt,
            this::scheduleNextShrink,
            borderStartSize,
            borderEndSize,
            borderShrinkEveryMinutes,
            borderShrinkLerpSeconds,
            projectDurationMinutes,
        afkKickMs,
        hudScoreboardUpdateMs,
        hudBorderParticleIntervalMs,
        hudBorderNearDist,
        hudBorderParticleRadius,
        hudBorderParticleStep
        );
        hudBorderFeature.start();

        // Fight Radar (GM actionbar pulse about fresh PvP)
        fightRadarService = new de.varo.services.FightRadarService(this, lastPvP, teams, playerTeam, gameMasters);

        // Lag Watch: auto-throttle HUD/particles on low TPS and provide /lagreport
        lagWatchService = new de.varo.services.LagWatchService(this,
            () -> lagAutoThrottle,
            () -> lagTpsLow,
            () -> lagTpsRecover,
            () -> lagScoreboardScale,
            () -> lagBorderIntervalScale,
            () -> lagBorderStepScale
        );
        // Loot-Lama
        lootLamaService = new de.varo.services.LootLamaService(this);
    dummyPlayerService = new de.varo.services.DummyPlayerService(this);
    try { npcDummyService = new de.varo.services.NpcDummyService(this); } catch (Throwable ignored) { npcDummyService = null; }

        // Lifecycle flows and nether tracking services
        joinFlowService = new JoinFlowService(
            gameMasters,
            streamers,
            lastPvP,
            spawnProtectionUntil,
            rejoinProtectionUntil,
            () -> gameRunning,
            this::freezePlayer,
            (pl, force) -> { if (hudBorderFeature != null) hudBorderFeature.updateScoreboardFor(pl, force); },
            this::updateTabName,
            this::giveSpecteamSelector,
            this::scheduleOpenSpecteamMenu,
            (uuid) -> playerTeam.get(uuid),
            this::scheduleOpenRulesBook,
            (uuid) -> hasAcceptedRules(uuid),
            combatTagMs,
            rejoinProtectMs
        );
        getServer().getPluginManager().registerEvents(joinFlowService, this);

        netherService = new NetherService(
            () -> netherLimitMs,
            netherUsedMs,
            netherSessionStart,
            warned30, warned10, warned5, warned1, netherOvertime
        );
        getServer().getPluginManager().registerEvents(netherService, this);

        deathService = new DeathService(
            this,
            teams,
            playerTeam,
            streamers,
            gameMasters,
            playerKills,
            playerDeaths,
            forcedSpectateTargets,
            (viewer, target) -> spectateFeature.startOverlay(viewer, target, true),
            this::applyObservationState,
            this::giveSpecteamSelector
        );
        getServer().getPluginManager().registerEvents(deathService, this);

        // Hook: collect recent combat spots from death service via Bukkit listener
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener(){
            @org.bukkit.event.EventHandler
            public void onEntityDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
                try {
                    org.bukkit.entity.Player p = e.getEntity();
                    if (p != null && p.getLocation() != null) {
                        if (recentCombatSpots.size() >= 10) recentCombatSpots.removeFirst();
                        recentCombatSpots.addLast(p.getLocation().clone());
                    }
                } catch (Throwable ignored) {}
            }
        }, this);

        // GM overlay is appended to HUD (no BossBar)

        // Session scheduler (deaktiviert)
        // sessionScheduler = new SessionSchedulerFeature(this);
        // sessionScheduler.start();

        getLogger().info("TRIVA geladen.");
            // Finalphase Periodic Task (alle 5s prüfen)
            try {
                if (finalPhaseTaskId != -1) Bukkit.getScheduler().cancelTask(finalPhaseTaskId);
                finalPhaseTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
                    try {
                        if (gameRunning && !finalPhaseActive) {
                            long remaining = getProjectRemainingMs();
                            if (remaining <= 0L) {
                                startFinalPhase();
                            }
                        }
                        if (finalPhaseActive) {
                            updateFinalPhaseBorder(false);
                        }
                    } catch (Throwable ignored) {}
                }, 20L, 100L); // start after 1s, repeat every 5s
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            getLogger().severe("[TRIVA] Schwerer Initialisierungsfehler: " + t.getMessage());
            t.printStackTrace();
            // Minimal fallback: register only this listener so commands like /help still work
            try { getServer().getPluginManager().registerEvents(this, this); } catch (Throwable ignored) {}
            try {
                String msg = net.md_5.bungee.api.ChatColor.RED + "TRIVA Start-Fehler" + net.md_5.bungee.api.ChatColor.GRAY + ": Teile deaktiviert. Check Konsole.";
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    p.sendMessage(msg);
                }
            } catch (Throwable ignored) {}
        }
    }

    // Stop spectate overlay on manual close to be safe
    // Overlay close handled by SpectateFeature

    // ==== Small helpers (readability) ====
    private void applyObservationState(Player p) {
        freezePlayer(p);
        try { p.setCollidable(false); } catch (Throwable ignored) {}
        try { p.setCanPickupItems(false); } catch (Throwable ignored) {}
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20*60*60*24, 0, true, false, false));
    }

    private void scheduleOpenSpecteamMenu(Player p) {
        new BukkitRunnable(){
            @Override public void run(){ if (p.isOnline()) openSpecteamTeamsMenu(p); }
        }.runTaskLater(this, 2L);
    }

    // === Rules (modularized) ===
    private de.varo.services.RulesService rulesService;
    public boolean hasAcceptedRules(java.util.UUID id) { return rulesService != null && rulesService.hasAccepted(id); }
    public void markAcceptedRules(Player p) { if (rulesService != null) rulesService.markAccepted(p); }
    private void scheduleOpenRulesBook(Player p) { if (rulesService != null) rulesService.scheduleOpenBook(p, 20L*5); }
    public void openRulesBook(Player p) { if (rulesService != null) rulesService.openBook(p); }


    @Override
    public void onDisable() {
    if (scoreboardTaskId != -1) Bukkit.getScheduler().cancelTask(scoreboardTaskId);
    if (hudBorderFeature != null) hudBorderFeature.stop();
    if (combatTagFeature != null) combatTagFeature.stop();
    if (sessionScheduler != null) sessionScheduler.stop();
    if (dummyPlayerService != null) dummyPlayerService.clear();
    if (npcDummyService != null) npcDummyService.clear();
    // no GM BossBar to clean up
        saveState();
    }
    
    // ==== Config laden ====
    private void applyConfig(de.varo.services.ConfigService.ConfigValues v) {
        // Border
        borderStartSize = v.borderStartSize;
        borderEndSize = v.borderEndSize;
        borderShrinkEveryMinutes = v.borderShrinkEveryMinutes;
        borderShrinkLerpSeconds = v.borderShrinkLerpSeconds;
        borderCenterX = v.borderCenterX;
        borderCenterZ = v.borderCenterZ;

        // Project
        projectDurationMinutes = v.projectDurationMinutes;
        projectDurationMs = v.projectDurationMs;
        teamSpreadRadius = v.teamSpreadRadius;

        // Nether
        netherLimitMs = v.netherLimitMs;

        // Supplydrop
        supplyDropEnabled = v.supplyDropEnabled;
        supplyDropInterval = v.supplyDropInterval;
        supplyDropLoot = new ArrayList<>(v.supplyDropLoot);

        // Tracker
        trackerCooldownMs = v.trackerCooldownMs;

        // PvP
        combatTagMs = v.combatTagMs;
        rejoinProtectMs = v.rejoinProtectMs;
        loggerNpcSeconds = v.loggerNpcSeconds;

        // AFK
        afkKickMs = v.afkKickMs;

        // Derived
        shrinkIntervalMs = v.shrinkIntervalMs;

    // HUD tuning
    hudScoreboardUpdateMs = v.hudScoreboardUpdateMs;
    hudBorderParticleIntervalMs = v.hudBorderParticleIntervalMs;
    hudBorderNearDist = v.hudBorderNearDist;
    hudBorderParticleRadius = v.hudBorderParticleRadius;
    hudBorderParticleStep = v.hudBorderParticleStep;

        // Privacy/Streamer
        privacyMaskCoords = v.privacyMaskCoords;
        spectateDelaySeconds = v.spectateDelaySeconds;

        // AntiCheat mining thresholds
        acWindowMinutes = v.acWindowMinutes;
        acDiamondPerBlocks = v.acDiamondPerBlocks;
        acDiamondMaxRatio = v.acDiamondMaxRatio;
        acDiamondFastFind = v.acDiamondFastFind;
        acDebrisPerBlocks = v.acDebrisPerBlocks;
        acDebrisMaxRatio = v.acDebrisMaxRatio;
        acDebrisFastFind = v.acDebrisFastFind;
        acBranchWithinSteps = v.acBranchWithinSteps;
        acBranchWarnCount = v.acBranchWarnCount;

        // Whitelist
        wlCodeTtlMs = v.wlCodeTtlMs;

    // Performance auto-throttle
    lagAutoThrottle = v.lagAutoThrottle;
    lagTpsLow = v.lagTpsLow;
    lagTpsRecover = v.lagTpsRecover;
    lagScoreboardScale = v.lagScoreboardScale;
    lagBorderIntervalScale = v.lagBorderIntervalScale;
    lagBorderStepScale = v.lagBorderStepScale;

    // Debug toggles
    debugAllowRuntimeShrinkAdjust = v.debugAllowRuntimeShrinkAdjust;
    debugAllowRuntimeProjectTimeAdjust = v.debugAllowRuntimeProjectTimeAdjust;

        // Keys
        trackerKey = v.trackerKey;
        specteamTeamKey = v.specteamTeamKey;
        specteamPlayerKey = v.specteamPlayerKey;
        specteamSelectorKey = v.specteamSelectorKey;
        combatNpcKey = v.combatNpcKey;
        gmTpMenuKey = v.gmTpMenuKey;
        gmReviewKey = v.gmReviewKey;
        gmMiningKey = v.gmMiningKey;
        gmReportsKey = v.gmReportsKey;
    }

    // ==== State laden ====
    private void applyState(de.varo.services.StateService.RuntimeState rs) {
        gameRunning = rs.gameRunning;
        blitzTriggered = rs.blitzTriggered;
        paused = rs.paused;
        gameStartTime = rs.gameStartTime;
        nextShrinkAt = rs.nextShrinkAt;
        centerSet = rs.centerSet;

        // Border center/size were already applied by StateService to the live world
        borderCenterX = rs.borderCenterX;
        borderCenterZ = rs.borderCenterZ;

        teams.clear();
        teams.putAll(rs.teams);
        teamReady.clear();
        teamReady.putAll(rs.teamReady);
        teamColors.clear();
        teamColors.putAll(rs.teamColors);

    streamers.clear();
        streamers.addAll(rs.streamers);

        playerKills.clear();
        playerKills.putAll(rs.playerKills);

    netherUsedMs.clear();
        netherUsedMs.putAll(rs.netherUsedMs);

    // Rules accepted
    rulesAccepted.clear();
    rulesAccepted.addAll(rs.rulesAccepted);

    // YAML access centralized in StateService; keep no direct reference here
    }

    @SuppressWarnings("deprecation")
    private void saveState() {
        // Build a snapshot for StateService
    de.varo.services.StateService.RuntimeState rs = new de.varo.services.StateService.RuntimeState();
        rs.gameRunning = gameRunning;
        rs.blitzTriggered = blitzTriggered;
        rs.paused = paused;
        rs.centerSet = centerSet;
        rs.gameStartTime = gameStartTime;
        rs.nextShrinkAt = nextShrinkAt;

        rs.borderCenterX = borderCenterX;
        rs.borderCenterZ = borderCenterZ;
        try { rs.borderCurrentSize = org.bukkit.Bukkit.getWorlds().get(0).getWorldBorder().getSize(); } catch (Throwable ignored) {}

        rs.teams.putAll(teams);
        rs.teamReady.putAll(teamReady);
        rs.teamColors.putAll(teamColors);
        rs.streamers.addAll(streamers);
        rs.playerKills.putAll(playerKills);
    rs.netherUsedMs.putAll(netherUsedMs);
    rs.rulesAccepted.addAll(rulesAccepted);

    if (stateService == null) { stateService = new de.varo.services.StateService(); stateService.init(this); }
    stateService.save(this, rs);

        // Keep legacy state yaml in sync for services that directly write to it (e.g., whitelist)
    stateService.saveLegacy(this, rs, (y) -> { if (whitelistService != null) whitelistService.saveToState(y); });
    }

    // Tracker item helpers moved to TrackerService and TrackerFeature

    // Join handled by JoinFlowService

    // Whitelist login enforcement is handled by WhitelistService

    // Quit handled by JoinFlowService/NetherService

    // World change handled by NetherService

    // Despawn NPC and cancel task when player rejoins
    @EventHandler
    public void onJoinRestoreNpc(PlayerJoinEvent e) {
        // Handled by CombatLoggerService
    }

    // Craft ban is handled by RulesFeature

    // Enforce forced spectate state in Adventure: block teleports out of observation
    @EventHandler(ignoreCancelled = true)
    public void onTeleportWhileForcedSpectate(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!forcedSpectateTargets.containsKey(id)) return;
        if (gameMasters.contains(id) || streamers.contains(id)) return;
        // Allow plugin-driven teleports (e.g., our own repositioning), block others
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.GRAY + "Du beobachtest deinen Teampartner. Teleport ist gesperrt.");
        }
    }

    // If a forced spectator closes the overlay, reopen it to keep observation active
    @EventHandler
    public void onOverlayClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player viewer = (Player) e.getPlayer();
        UUID id = viewer.getUniqueId();
        if (!forcedSpectateTargets.containsKey(id)) return;
        if (gameMasters.contains(id) || streamers.contains(id)) return;
        UUID targetId = forcedSpectateTargets.get(id);
        if (targetId == null) return;
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline() || target.getGameMode() == GameMode.SPECTATOR || target.isDead()) return;
        // Reopen overlay shortly after close
        new BukkitRunnable(){
            @Override public void run(){
                if (!viewer.isOnline()) return;
                if (spectateFeature != null) spectateFeature.startOverlay(viewer, target, true);
            }
        }.runTaskLater(this, 2L);
    }

    // Consume ban is handled by RulesFeature

    // Pickup ban is handled by RulesFeature

    // Portal rules moved to PortalFeature

    // ==== Interaktionen (Freeze / Bed) ====
    // Frozen interaction guard moved to GuardFeature


    // Movement / Freeze / AFK
    // Movement freeze moved to GuardFeature; AFK tracking remains via HudBorderFeature

    // Block break/place freeze moved to GuardFeature; lastActive updates handled elsewhere

    // Damage / Schutzs
    // Damage/PvP protections moved to ProtectionFeature

    @EventHandler
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        // Combat-logger NPC death handled by CombatLoggerService
    }

    // Respawn handled by JoinFlowService

    // Tote dürfen nur ihren Mate beobachten (SPECTATE-Teleports blocken)
    @SuppressWarnings("deprecation")
    @EventHandler(ignoreCancelled = true)
    public void onSpectateTeleport(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) return;
        Player viewer = e.getPlayer();
        if (viewer.getGameMode() != GameMode.SPECTATOR) return;

        // Erlaubt: GM/Streamer
        if (gameMasters.contains(viewer.getUniqueId()) || streamers.contains(viewer.getUniqueId())) return;

        // Nur eigenen Mate beobachten
        if (e.getTo() == null) return;
        Location to = e.getTo();
        Player target = null;
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (!pl.getWorld().equals(to.getWorld())) continue;
            if (pl.getLocation().distanceSquared(to) < 1.0) { target = pl; break; }
        }
        if (target == null) return;

        String tv = playerTeam.get(viewer.getUniqueId());
        String tt = playerTeam.get(target.getUniqueId());
        if (tv == null || tt == null || !tv.equals(tt)) {
            e.setCancelled(true);
            viewer.sendMessage(ChatColor.RED + "Du darfst nur deinen Teampartner beobachten.");
            return;
        }
        // Start spectate overlay: open inventory view and schedule updates
    if (target != null) spectateFeature.startOverlay(viewer, target, false);
    }

    // PlayerDeath handled by DeathService

    // /summary [filename] — show endgame summary and export to file
    @SuppressWarnings("deprecation")
    public void cmdSummary(Player sender, String[] args) {
        java.util.List<String> lines = de.varo.util.SummaryUtil.buildSummaryLines(teams, playerKills, playerDeaths);

        // Broadcast compact summary to sender (first 15 lines)
    sender.sendMessage(Messages.title("TRIVA SUMMARY"));
        int shown = 0;
        for (String l : lines) { sender.sendMessage(ChatColor.GRAY + l); if (++shown >= 15) break; }
        if (lines.size() > shown) sender.sendMessage(ChatColor.DARK_GRAY + "+" + (lines.size()-shown) + " weitere Zeilen in Datei");

        // Export to file
        String desired = (args != null && args.length >= 1 && !args[0].isEmpty()) ? args[0] : null;
        java.io.File out;
        try {
            out = de.varo.util.SummaryUtil.writeSummary(this, lines, desired);
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Export fehlgeschlagen: " + ex.getMessage());
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Summary exportiert: " + ChatColor.WHITE + out.getName());
    }

    // ==== Help/Regeln for participants ====
    @SuppressWarnings("deprecation")
    public void cmdHelp(Player p) {
    p.sendMessage(Messages.title("Hilfe"));
        p.sendMessage(ChatColor.YELLOW + "/team create <Name>" + ChatColor.GRAY + " — Team erstellen");
        p.sendMessage(ChatColor.YELLOW + "/team invite <Spieler>" + ChatColor.GRAY + " — Spieler einladen");
        p.sendMessage(ChatColor.YELLOW + "/team accept <Team|Spieler>" + ChatColor.GRAY + " — Einladung annehmen");
        p.sendMessage(ChatColor.YELLOW + "/team decline [Team]" + ChatColor.GRAY + " — Einladung ablehnen");
        p.sendMessage(ChatColor.YELLOW + "/team delete" + ChatColor.GRAY + " — Team-Löschung beantragen (Admin bestätigt)");
        p.sendMessage(ChatColor.YELLOW + "/hud" + ChatColor.GRAY + " — Border-HUD (Actionbar) ein-/ausblenden");
        p.sendMessage(ChatColor.YELLOW + "/varo" + ChatColor.GRAY + " — Projektinfo (Border, Zeit, Teams)");
        p.sendMessage(ChatColor.YELLOW + "/who" + ChatColor.GRAY + " — Lebende Teams/Spieler");
        p.sendMessage(ChatColor.YELLOW + "/report <Spieler> [Grund]" + ChatColor.GRAY + " — Admins informieren");
        p.sendMessage(ChatColor.DARK_GRAY + "Mehr: " + ChatColor.WHITE + "/regeln" + ChatColor.DARK_GRAY + " für die Projektregeln.");
    }

    @SuppressWarnings("deprecation")
    public void cmdRegeln(Player p) {
        // Derive values from config already loaded
        String projTime = (projectDurationMinutes >= 60
                ? (projectDurationMinutes / 60) + "h " + (projectDurationMinutes % 60 == 0 ? "" : (projectDurationMinutes % 60) + "m")
                : projectDurationMinutes + "m");
        String borderInfo = ChatColor.YELLOW + "Border: " + ChatColor.WHITE + (int)borderStartSize + ChatColor.GRAY + " → " + ChatColor.WHITE + (int)borderEndSize
                + ChatColor.GRAY + ", schrumpft alle " + ChatColor.WHITE + borderShrinkEveryMinutes + ChatColor.GRAY + "; Dauer Shrink: " + ChatColor.WHITE + borderShrinkLerpSeconds + "s";
    p.sendMessage(Messages.title("Regeln"));
        p.sendMessage(ChatColor.YELLOW + "Projektzeit: " + ChatColor.WHITE + projTime + ChatColor.GRAY + " (danach Ende/Finale)");
        p.sendMessage(ChatColor.YELLOW + "Netherzeit: " + ChatColor.WHITE + "1 Stunde" + ChatColor.GRAY + " pro Spieler — danach Ticks/Druck/Teleport in die Overworld");
        p.sendMessage(borderInfo);
        p.sendMessage(ChatColor.YELLOW + "Kein End: " + ChatColor.GRAY + "Das Ende ist deaktiviert (End-Portale/Enderaugen blockiert)");
        p.sendMessage(ChatColor.YELLOW + "Verboten: " + ChatColor.GRAY + "Verzauberte Goldäpfel (Craft/Consume gesperrt)");
        p.sendMessage(ChatColor.YELLOW + "Kampf: " + ChatColor.GRAY + "PvP erlaubt; Spawn-/Rejoin-Schutz aktiv; Combat-Log wird bestraft");
        p.sendMessage(ChatColor.YELLOW + "AFK: " + ChatColor.GRAY + "Länger AFK → Kick");
        p.sendMessage(ChatColor.DARK_GRAY + "Hinweis: Streamer haben Privatsphäre-Optionen; GMs haben Tools/Review.");
    }

    // Drop/swap/pickup/held guards moved to GuardFeature

    private void giveSpecteamSelector(Player p) { if (gmUiService != null) gmUiService.giveSpecteamSelector(p); }

    // Chat formatting and staff chat are handled by ChatFeature

    // GM / Specteam Inventar-Clicks
    // Spectate inventory blocking is handled by SpectateFeature/GuardFeature; GM/Specteam clicks are in GmFeature/SpecteamFeature

    // Commands are handled by de.varo.features.commands.CommandsFeature

    private void logAction(String msg) {
        String ts = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        String line = ChatColor.GRAY + "[" + ts + "] " + ChatColor.WHITE + msg;
        actionLog.addLast(line);
        while (actionLog.size() > 200) actionLog.removeFirst();
        getLogger().info("LOGS: " + ChatColor.stripColor(line));
    }

    // Show per-player mining stats (diamonds/debris per blocks, counts, branch hits)
    @SuppressWarnings("deprecation")
    public void cmdMiningStats(Player gm, String[] args) {
        Player target = gm;
        if (args != null && args.length >= 1) {
            Player t = Bukkit.getPlayerExact(args[0]);
            if (t != null) target = t;
        }
        if (antiCheatFeature == null) { gm.sendMessage(ChatColor.RED + "Keine Daten."); return; }
    de.varo.features.anticheat.AntiCheatFeature.MiningSnapshot snap = antiCheatFeature.snapshotMining(target.getUniqueId());
        if (snap == null) { gm.sendMessage(ChatColor.YELLOW + "Keine Mining-Daten für " + target.getName()); return; }
    gm.sendMessage(Messages.title("Mining-Stats von " + ChatColor.YELLOW + target.getName() + ChatColor.GOLD));
        gm.sendMessage(ChatColor.GRAY + "Fenster: " + acWindowMinutes + "min");
        gm.sendMessage(ChatColor.AQUA + "Overworld: " + ChatColor.WHITE + snap.stone + ChatColor.GRAY + " Blöcke"
                + ChatColor.GRAY + ", Diamanten: " + ChatColor.WHITE + snap.diamonds
                + ChatColor.GRAY + ", Ratio: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.2f/"+acDiamondPerBlocks, snap.diamondPerNorm));
        gm.sendMessage(ChatColor.LIGHT_PURPLE + "Nether: " + ChatColor.WHITE + snap.netherrack + ChatColor.GRAY + " Blöcke"
                + ChatColor.GRAY + ", Netherit: " + ChatColor.WHITE + snap.debris
                + ChatColor.GRAY + ", Ratio: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.2f/"+acDebrisPerBlocks, snap.debrisPerNorm));
        gm.sendMessage(ChatColor.YELLOW + "Branch→Erz (≤" + acBranchWithinSteps + "): " + ChatColor.WHITE + snap.branchHits);
    }

    @SuppressWarnings("deprecation")
    public void cmdLogs(Player p, String[] args) { if (moderationFeature != null) moderationFeature.printLogs(p, args); }

    // /wlcode <CODE> — whitelist a pending player by code (Admin/GM)
    @SuppressWarnings("deprecation")
    public void cmdWlCode(org.bukkit.command.CommandSender sender, String[] args) { if (whitelistService != null) whitelistService.cmdWlCode(sender, args); }

    // /wllist — list pending codes and age
    @SuppressWarnings("deprecation")
    public void cmdWlList(Player admin) { if (whitelistService != null) whitelistService.cmdWlList(admin); }

    // /wldel <code|Spieler> — delete a pending code
    @SuppressWarnings("deprecation")
    public void cmdWlDel(Player admin, String[] args) { if (whitelistService != null) whitelistService.cmdWlDel(admin, args); }

    // Review a player: show mining snapshot, violations, recent flags, and save a short clip
    @SuppressWarnings("deprecation")
    public void cmdReview(Player gm, String[] args) {
    if (reviewFeature != null) reviewFeature.cmdReview(gm, args, acWindowMinutes, acDiamondPerBlocks, acDebrisPerBlocks, acBranchWithinSteps);
    }

    // /report <Spieler> [Grund] — broadcast to GMs
    @SuppressWarnings("deprecation")
    public void cmdReport(Player sender, String[] args) {
        if (args == null || args.length == 0) { sender.sendMessage(ChatColor.YELLOW + "/report <Spieler> [Grund]"); return; }
        String name = args[0];
        String reason = (args.length > 1) ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "-";
        if (reportsFeature != null) reportsFeature.submitReport(sender, name, reason);
        else {
            for (Player gm : Bukkit.getOnlinePlayers()) {
                if (gameMasters.contains(gm.getUniqueId())) {
                    gm.sendMessage(ChatColor.DARK_RED + "REPORT " + ChatColor.GRAY + "von " + ChatColor.WHITE + sender.getName() + ChatColor.GRAY + ": "
                            + ChatColor.YELLOW + name + ChatColor.GRAY + " — " + ChatColor.WHITE + reason);
                }
            }
        }
        sender.sendMessage(ChatColor.GREEN + "Report gesendet.");
        logAction("Report: " + sender.getName() + " -> " + name + " (" + reason + ")");
    }

    // Spectator POIs: clickable TP to last drop, recent fights, nearest border
    @SuppressWarnings("deprecation")
    public void cmdPoi(Player viewer) {
        if (!(gameMasters.contains(viewer.getUniqueId()) || streamers.contains(viewer.getUniqueId()) || viewer.getGameMode()==GameMode.SPECTATOR)) {
            viewer.sendMessage(ChatColor.RED + "Nur Zuschauer/GM/Streamer."); return;
        }
        // Last Drop
        try {
            de.varo.features.SupplyDropService sd = getSupplyDropService();
            org.bukkit.Location d = (sd != null ? sd.getLastDrop() : null);
            if (d != null) {
                net.md_5.bungee.api.chat.TextComponent t = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.ChatColor.LIGHT_PURPLE + "[TP: Letzter Drop]");
                t.setUnderlined(true);
                t.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/poitp drop"));
                viewer.spigot().sendMessage(new net.md_5.bungee.api.chat.TextComponent[]{
                    new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.ChatColor.GOLD + "POIs: ")
                });
                viewer.spigot().sendMessage(new net.md_5.bungee.api.chat.TextComponent[]{ t });
            }
        } catch (Throwable ignored) {}
        // Fights list (up to 3 latest)
        int added = 0;
        java.util.List<org.bukkit.Location> fights = new java.util.ArrayList<>(recentCombatSpots);
        java.util.Collections.reverse(fights);
        for (org.bukkit.Location loc : fights) {
            if (added >= 3) break;
            if (loc == null || loc.getWorld()==null) continue;
            net.md_5.bungee.api.chat.TextComponent t = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.ChatColor.RED + "[TP: Kampf]");
            t.setUnderlined(true);
            // As above, we can teleport directly on click only via commands; instead, we show coords and do a quick TP command suggestion
            t.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                String.format(java.util.Locale.ROOT, "/poitp fight %d", added)));
            viewer.spigot().sendMessage(new net.md_5.bungee.api.chat.TextComponent[]{ t });
            added++;
        }
        // Nearest border edge from viewer
        try {
            org.bukkit.WorldBorder wb = viewer.getWorld().getWorldBorder();
            org.bukkit.Location c = wb.getCenter().toLocation(viewer.getWorld());
            org.bukkit.Location pl = viewer.getLocation();
            double half = wb.getSize()/2.0;
            double tx = Math.max(c.getX()-half, Math.min(c.getX()+half, pl.getX()));
            double tz = Math.max(c.getZ()-half, Math.min(c.getZ()+half, pl.getZ()));
            int y = viewer.getWorld().getHighestBlockYAt((int)Math.round(tx), (int)Math.round(tz)) + 1;
            net.md_5.bungee.api.chat.TextComponent t = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.ChatColor.AQUA + "[TP: Border]");
            t.setUnderlined(true);
            t.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                String.format(java.util.Locale.ROOT, "/poitp border %d %d %d", (int)Math.round(tx), y, (int)Math.round(tz))));
            viewer.spigot().sendMessage(new net.md_5.bungee.api.chat.TextComponent[]{ t });
        } catch (Throwable ignored) {}
    }

    public void cmdPoiTp(Player p, String kind, String[] args) {
        if (!(gameMasters.contains(p.getUniqueId()) || streamers.contains(p.getUniqueId()) || p.getGameMode()==GameMode.SPECTATOR)) {
            p.sendMessage(ChatColor.RED + "Nur Zuschauer/GM/Streamer."); return;
        }
        try {
            switch (kind) {
                case "atw": {
                    if (args.length < 5) { p.sendMessage(ChatColor.YELLOW + "/poitp atw <world> <x> <y> <z>"); return; }
                    String wn = args[1];
                    org.bukkit.World w = org.bukkit.Bukkit.getWorld(wn);
                    if (w == null) { p.sendMessage(ChatColor.RED + "Welt nicht gefunden: " + wn); return; }
                    int x = Integer.parseInt(args[2]);
                    int y = Integer.parseInt(args[3]);
                    int z = Integer.parseInt(args[4]);
                    org.bukkit.Location l = new org.bukkit.Location(w, x + 0.5, y, z + 0.5);
                    p.teleport(l, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    p.sendMessage(ChatColor.RED + "TP zu Kill-Ort (" + wn + ").");
                    return;
                }
                case "at": {
                    // direct coordinate TP (used by GM killfeed [TP])
                    if (args.length < 4) { p.sendMessage(ChatColor.YELLOW + "/poitp at <x> <y> <z>"); return; }
                    int x = Integer.parseInt(args[1]);
                    int y = Integer.parseInt(args[2]);
                    int z = Integer.parseInt(args[3]);
                    org.bukkit.Location l = new org.bukkit.Location(p.getWorld(), x + 0.5, y, z + 0.5);
                    p.teleport(l, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    p.sendMessage(ChatColor.RED + "TP zu Kill-Ort.");
                    return;
                }
                case "drop": {
                    de.varo.features.SupplyDropService sd = getSupplyDropService();
                    org.bukkit.Location d = (sd != null ? sd.getLastDrop() : null);
                    if (d == null) { p.sendMessage(ChatColor.RED + "Kein letzter Drop."); return; }
                    p.teleport(d, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    p.sendMessage(ChatColor.LIGHT_PURPLE + "TP zu letztem Drop.");
                    return;
                }
                case "fight": {
                    int idx = 0;
                    if (args.length >= 2) { try { idx = Math.max(0, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {} }
                    java.util.List<org.bukkit.Location> fights = new java.util.ArrayList<>(recentCombatSpots);
                    java.util.Collections.reverse(fights);
                    if (idx >= fights.size()) { p.sendMessage(ChatColor.RED + "Kein Kampf an Index."); return; }
                    org.bukkit.Location l = fights.get(idx);
                    if (l == null) { p.sendMessage(ChatColor.RED + "Ort ungültig."); return; }
                    p.teleport(l, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    p.sendMessage(ChatColor.RED + "TP zu Kampf-Ort.");
                    return;
                }
                case "border": {
                    if (args.length < 4) { p.sendMessage(ChatColor.YELLOW + "/poitp border <x> <y> <z>"); return; }
                    int x = Integer.parseInt(args[1]);
                    int y = Integer.parseInt(args[2]);
                    int z = Integer.parseInt(args[3]);
                    org.bukkit.Location l = new org.bukkit.Location(p.getWorld(), x + 0.5, y, z + 0.5);
                    p.teleport(l, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    p.sendMessage(ChatColor.AQUA + "TP zur Border-Kante.");
                    return;
                }
            }
        } catch (Throwable t) {
            p.sendMessage(ChatColor.RED + "TP fehlgeschlagen.");
        }
    }

    // /announce <Nachricht...> — Title an alle (BossBar optional später)
    @SuppressWarnings("deprecation")
    public void cmdAnnounce(Player sender, String[] args) {
        if (args == null || args.length == 0) { sender.sendMessage(ChatColor.YELLOW + "/announce <Nachricht>"); return; }
        String msg = String.join(" ", args);
        for (Player pl : Bukkit.getOnlinePlayers()) {
            try { pl.sendTitle(ChatColor.GOLD + "ANNOUNCE", ChatColor.WHITE + msg, 10, 60, 10); } catch (Throwable ignored) {}
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "[ANNOUNCE] " + ChatColor.WHITE + msg);
        logAction("Announce: " + msg);
    }

    // /pregen [radiusChunks] — pre-load chunks around center in Overworld (throttled)
    @SuppressWarnings("deprecation")
    public void cmdPregen(Player sender, String[] args) {
        int r = 32; // default
        if (args != null && args.length >= 1) {
            try { r = Math.max(4, Math.min(128, Integer.parseInt(args[0]))); } catch (NumberFormatException ignored) {}
        }
    final int radius = r;
    sender.sendMessage(ChatColor.YELLOW + "Pregen gestartet: " + (radius*2+1) + "×" + (radius*2+1) + " Chunks …");
    logAction("Pregen by " + sender.getName() + ": radiusChunks=" + radius);
    de.varo.util.WorldPregenUtil.pregenAroundBorder(this, sender, radius);
    }

    // /hudset <key> <value> | /hudset dump — live-change HUD timings/geometry
    @SuppressWarnings("deprecation")
    public void cmdHudSet(Player sender, String[] args) {
        if (hudBorderFeature == null) { sender.sendMessage(ChatColor.RED + "HUD nicht aktiv."); return; }
        if (args == null || args.length == 0 || args[0].equalsIgnoreCase("dump")) {
            sender.sendMessage(Messages.title("HUD Einstellungen"));
            sender.sendMessage(ChatColor.GRAY + "hud.scoreboardUpdateMs" + ChatColor.WHITE + ": " + hudScoreboardUpdateMs);
            sender.sendMessage(ChatColor.GRAY + "hud.borderParticles.intervalMs" + ChatColor.WHITE + ": " + hudBorderParticleIntervalMs);
            sender.sendMessage(ChatColor.GRAY + "hud.borderParticles.nearDist" + ChatColor.WHITE + ": " + hudBorderNearDist);
            sender.sendMessage(ChatColor.GRAY + "hud.borderParticles.radius" + ChatColor.WHITE + ": " + hudBorderParticleRadius);
            sender.sendMessage(ChatColor.GRAY + "hud.borderParticles.step" + ChatColor.WHITE + ": " + hudBorderParticleStep);
            return;
        }
        if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "/hudset <key> <value> | /hudset dump"); return; }
        String key = args[0].toLowerCase(java.util.Locale.ROOT);
        double val;
        try { val = Double.parseDouble(args[1]); } catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Wert ungültig."); return; }

        boolean applied = false;
        switch (key) {
            case "hud.scoreboardupdatems":
            case "scoreboardupdatems":
                hudScoreboardUpdateMs = Math.max(500L, (long) val);
                applied = true; break;
            case "hud.borderparticles.intervalms":
            case "border.intervalms":
            case "borderparticles.intervalms":
                hudBorderParticleIntervalMs = Math.max(250L, (long) val);
                applied = true; break;
            case "hud.borderparticles.neardist":
            case "border.neardist":
                hudBorderNearDist = Math.max(4.0, val);
                applied = true; break;
            case "hud.borderparticles.radius":
            case "border.radius":
                hudBorderParticleRadius = Math.max(4.0, val);
                applied = true; break;
            case "hud.borderparticles.step":
            case "border.step":
                hudBorderParticleStep = Math.max(1.0, val);
                applied = true; break;
        }
        if (!applied) { sender.sendMessage(ChatColor.RED + "Unbekannter Schlüssel."); return; }
        // push live to feature
        try {
            hudBorderFeature.applyHudSettings(
                hudScoreboardUpdateMs,
                hudBorderParticleIntervalMs,
                hudBorderNearDist,
                hudBorderParticleRadius,
                hudBorderParticleStep
            );
        } catch (Throwable t) {
            // best-effort; feature may still tick with new values if fields are read from plugin
        }
        sender.sendMessage(ChatColor.GREEN + "Gesetzt.");
    }

    // /debugshrink <minuten> — setzt Intervall bis nächster Shrink (nur Admin). 1 bedeutet jede Minute.
    public void cmdDebugShrink(Player sender, String[] args) {
        if (!gameRunning) { sender.sendMessage(ChatColor.RED + "Projekt läuft nicht."); return; }
    if (!debugAllowRuntimeShrinkAdjust) { sender.sendMessage(ChatColor.RED + "Debug-Änderung deaktiviert (config debug.allowRuntimeShrinkAdjust=false)"); return; }
        if (args == null || args.length < 1) { sender.sendMessage(ChatColor.YELLOW + "/debugshrink <minuten>"); return; }
        try {
            int m = Integer.parseInt(args[0]);
            if (m < 1) m = 1; // Minimum 1 Minute
            borderShrinkEveryMinutes = m; // live ändern
            shrinkIntervalMs = borderShrinkEveryMinutes * 60_000L;
            // reschedule next shrink relative to jetzt
            nextShrinkAt = System.currentTimeMillis() + shrinkIntervalMs;
            saveState();
            sender.sendMessage(ChatColor.GREEN + "Shrink-Intervall jetzt alle " + ChatColor.WHITE + m + ChatColor.GREEN + " Minuten.");
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Zahl ungültig.");
        }
    }

    // /debugprojzeit <minuten> — setzt verbleibende Projektzeit (verkürzt oder verlängert)
    public void cmdDebugProjzeit(Player sender, String[] args) {
        if (!gameRunning) { sender.sendMessage(ChatColor.RED + "Projekt läuft nicht."); return; }
    if (!debugAllowRuntimeProjectTimeAdjust) { sender.sendMessage(ChatColor.RED + "Debug-Änderung deaktiviert (config debug.allowRuntimeProjectTimeAdjust=false)"); return; }
        if (args == null || args.length < 1) { sender.sendMessage(ChatColor.YELLOW + "/debugprojzeit <minuten>"); return; }
        try {
            int mins = Integer.parseInt(args[0]);
            if (mins < 1) mins = 1;
            long already = System.currentTimeMillis() - gameStartTime;
            projectDurationMs = already + mins * 60_000L; // set total so remaining = mins
            saveState();
            sender.sendMessage(ChatColor.GREEN + "Projekt-Restzeit gesetzt auf " + ChatColor.WHITE + mins + ChatColor.GREEN + " Minuten.");
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Zahl ungültig.");
        }
    }

    // /acset <key> <value> | /acset dump — live-change thresholds
    @SuppressWarnings("deprecation")
    public void cmdAcSet(Player sender, String[] args) {
        if (antiCheatFeature == null) { sender.sendMessage(ChatColor.RED + "AntiCheat nicht aktiv."); return; }
        if (args == null || args.length == 0 || args[0].equalsIgnoreCase("dump")) {
            java.util.Map<String,Object> m = antiCheatFeature.dumpThresholds();
            sender.sendMessage(Messages.title("AC Schwellen"));
            for (java.util.Map.Entry<String,Object> e : m.entrySet()) sender.sendMessage(ChatColor.GRAY + e.getKey() + ChatColor.WHITE + ": " + e.getValue());
            return;
        }
        if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "/acset <key> <value> | /acset dump"); return; }
        String key = args[0];
        double val;
        try { val = Double.parseDouble(args[1]); } catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Wert ungültig."); return; }
        boolean ok = antiCheatFeature.setThreshold(key, val);
        sender.sendMessage(ok ? ChatColor.GREEN + "Gesetzt." : ChatColor.RED + "Unbekannter Schlüssel.");
    }

    // /acprofile <save|load|list> <name>
    @SuppressWarnings("deprecation")
    public void cmdAcProfile(Player sender, String[] args) {
        if (antiCheatFeature == null) { sender.sendMessage(ChatColor.RED + "AntiCheat nicht aktiv."); return; }
        if (args == null || args.length == 0) { sender.sendMessage(ChatColor.YELLOW + "/acprofile <save|load|list> <name>"); return; }
        String op = args[0].toLowerCase(java.util.Locale.ROOT);
        if (op.equals("list")) {
            java.util.List<String> names = de.varo.util.AcProfileUtil.list(this);
            if (names.isEmpty()) { sender.sendMessage(ChatColor.GRAY + "Keine Profile."); return; }
            sender.sendMessage(ChatColor.GOLD + "Profile:");
            for (String n : names) sender.sendMessage(ChatColor.WHITE + n);
            return;
        }
        if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "/acprofile <save|load|list> <name>"); return; }
        String name = args[1];
        if (op.equals("save")) {
            try {
                de.varo.util.AcProfileUtil.save(this, name, antiCheatFeature.dumpThresholds());
                sender.sendMessage(ChatColor.GREEN + "Profil gespeichert: " + name);
            } catch (Exception ex) {
                sender.sendMessage(ChatColor.RED + "Speichern fehlgeschlagen: " + ex.getMessage());
            }
        } else if (op.equals("load")) {
            java.util.Map<String,Object> m;
            try {
                m = de.varo.util.AcProfileUtil.load(this, name);
            } catch (Exception ex) {
                sender.sendMessage(ChatColor.RED + "Laden fehlgeschlagen: " + ex.getMessage());
                return;
            }
            if (m == null) { sender.sendMessage(ChatColor.RED + "Profil nicht gefunden."); return; }
            int applied = 0;
            for (java.util.Map.Entry<String,Object> en : m.entrySet()) {
                Object v = en.getValue();
                if (v instanceof Number) {
                    if (antiCheatFeature.setThreshold(en.getKey(), ((Number) v).doubleValue())) applied++;
                }
            }
            sender.sendMessage(ChatColor.GREEN + "Profil geladen: " + name + ChatColor.GRAY + " (" + applied + ")");
        } else {
            sender.sendMessage(ChatColor.RED + "Unbekannte Aktion.");
        }
    }

    // ==== Moderation ====
    public void staffChatToggle(Player p) { if (moderationFeature != null) moderationFeature.staffChatToggle(staffChatEnabled, p); }

    @SuppressWarnings("deprecation")
    public void cmdMute(Player gm, String[] args) { if (moderationFeature != null) moderationFeature.mute(gm, args); }

    @SuppressWarnings("deprecation")
    public void cmdUnmute(Player gm, String[] args) { if (moderationFeature != null) moderationFeature.unmute(gm, args); }

    @SuppressWarnings("deprecation")
    public void cmdWarn(Player gm, String[] args) { if (moderationFeature != null) moderationFeature.warn(gm, args); }

    @SuppressWarnings("deprecation")
    public void cmdVaroInfo(Player p) {
        WorldBorder wb = p.getWorld().getWorldBorder();
    p.sendMessage(Messages.title("TRIVA INFO"));
    boolean mask = privacyMaskCoords && streamers.contains(p.getUniqueId());
    String centerStr = mask ? (ChatColor.DARK_GRAY + "X:??? Z:???")
        : (ChatColor.WHITE + "" + (int)wb.getCenter().getX() + ChatColor.GRAY + "," + ChatColor.WHITE + (int)wb.getCenter().getZ());
    p.sendMessage(ChatColor.YELLOW + "Border: " + ChatColor.WHITE + (int)wb.getSize() + " Blöcke"
        + ChatColor.GRAY + "  Center: " + centerStr);
        p.sendMessage(ChatColor.YELLOW + "Projektzeit: " + ChatColor.WHITE + (gameRunning ? formatTime(getProjectRemainingMs()) : "wartet"));
        p.sendMessage(ChatColor.YELLOW + "Teams: " + ChatColor.WHITE + teams.size());
        if (!playerKills.isEmpty()) {
            UUID topId = null; int max = 0;
            for (Map.Entry<UUID,Integer> en : playerKills.entrySet()) if (en.getValue() > max) { max=en.getValue(); topId=en.getKey(); }
            String nm = topId!=null ? Objects.toString(Bukkit.getOfflinePlayer(topId).getName(),"Spieler") : "-";
            p.sendMessage(ChatColor.YELLOW + "Top-Killer: " + ChatColor.GOLD + nm + ChatColor.GRAY + " ("+max+")");
        }
    }

    // ==== Combat-Logger NPC ====
    // spawnCombatNpc now handled by CombatLoggerService

    @SuppressWarnings("deprecation")
    public void cmdSpecteam(Player p, String[] args) {
        if (args.length == 0) { openSpecteamTeamsMenu(p); return; }
        String team = args[0];
        if (!teams.containsKey(team)) {
            p.sendMessage(ChatColor.RED + "Team existiert nicht.");
            return;
        }
        openSpecteamPlayersMenu(p, team);
    }

    @SuppressWarnings("deprecation")
    public void cmdWho(Player sender) {
        @SuppressWarnings("deprecation")
        StringBuilder sb = new StringBuilder(ChatColor.GOLD + "Lebende Teams/Spieler:\n");
        for (Map.Entry<String, List<UUID>> e : teams.entrySet()) {
            String t = e.getKey();
            sb.append(ChatColor.YELLOW).append("  ").append(t).append(ChatColor.GRAY).append(": ");
            List<String> names = new ArrayList<>();
            for (UUID id : e.getValue()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                boolean online = op.isOnline();
                boolean active = false;
                if (online && op.getPlayer()!=null) active = (op.getPlayer().getGameMode()!=GameMode.SPECTATOR);
                names.add((online ? (active ? ChatColor.GREEN : ChatColor.GRAY) : ChatColor.DARK_GRAY)
                        + (op.getName() == null ? "Spieler" : op.getName()));
            }
            sb.append(String.join(ChatColor.GRAY + ", ", names)).append("\n");
        }
        sender.sendMessage(sb.toString());
    }

    // Streamer command moved to StreamerService
    // Team handling moved to TeamService


    public void toggleHud(Player p) {
        UUID id = p.getUniqueId();
        boolean hide = !hudHidden.contains(id);
        if (hide) {
            hudHidden.add(id);
            p.sendMessage(ChatColor.GRAY + "Border-HUD ausgeblendet. (/hud)");
        } else {
            hudHidden.remove(id);
            p.sendMessage(ChatColor.GREEN + "Border-HUD eingeblendet. (/hud)");
        }
    }


    private void scheduleNextShrink() {
    if (finalPhaseActive) return; // Finalphase übernimmt eigenes Shrinking
    // Wenn pausiert: nur Restzeit merken, nicht neu zählen lassen
    if (paused) {
        shrinkPauseRemainingMs = shrinkIntervalMs; // komplette Intervallzeit als Rest (oder könnte konfigurierbar sein)
        return;
    }
    nextShrinkAt = System.currentTimeMillis() + shrinkIntervalMs;
    saveState();
    }


    public void toggleGm(Player p) {
        UUID id = p.getUniqueId();
        if (gameMasters.contains(id)) {
            gameMasters.remove(id);
            p.setGameMode(GameMode.SURVIVAL);
            String old = gmOldTabName.getOrDefault(id, p.getName());
            p.setPlayerListName(old);
            p.sendMessage(ChatColor.YELLOW + "GM-Modus beendet.");
            removeGmToolkit(p);
            // Auto-vanish off when leaving GM
            if (adminToolsFeature != null && adminToolsFeature.isVanished(p)) {
                adminToolsFeature.toggleVanish(p);
            }
            try { p.setAllowFlight(false); p.setFlying(false); } catch (Throwable ignored) {}
            // no BossBar to remove
        } else {
            gameMasters.add(id);
            gmOldTabName.putIfAbsent(id, p.getPlayerListName());
            p.setGameMode(GameMode.ADVENTURE);
            p.setPlayerListName(ChatColor.RED + "GAMEMASTER: " + ChatColor.WHITE + p.getName());
            p.sendMessage(ChatColor.GREEN + "GM-Modus aktiv. /tpmenu für Teleports, Sneak = durch Blöcke (Spectator-Phasing).");
            giveGmToolkit(p);
            // Auto-vanish on when entering GM
            if (adminToolsFeature != null && !adminToolsFeature.isVanished(p)) {
                adminToolsFeature.toggleVanish(p);
            }
            try { p.setAllowFlight(true); p.setFlying(true); } catch (Throwable ignored) {}
            // HUD overlay for GM handled by HudBorderFeature
        }
    }

    // GM teleport handled by GmFeature

    // GM actions handled by GmFeature

    // ==== Spectate Overlay ====
    // Overlay start/stop delegated to SpectateFeature

    private void giveGmToolkit(Player p) { if (gmUiService != null) gmUiService.giveGmToolkit(p); }
    private void removeGmToolkit(Player p) { if (gmUiService != null) gmUiService.removeGmToolkit(p); }

    // Remove GM toolkit and Specteam selector from non-privileged players
    // Stripping special items is handled in JoinFlowService and GmUiService

    

    // handled by GmUiService
    public void onGmSelectClick(org.bukkit.event.inventory.InventoryClickEvent e) { }

    // ==== Specteam GUI ====
    private void openSpecteamTeamsMenu(Player viewer) {
        boolean isSpectator = viewer.getGameMode() == GameMode.SPECTATOR;
        boolean isGm = gameMasters.contains(viewer.getUniqueId());
        boolean isStreamer = streamers.contains(viewer.getUniqueId());
        boolean isEliminatedOverlay = forcedSpectateTargets.containsKey(viewer.getUniqueId());
        boolean allowed = isSpectator || isGm || isStreamer || isEliminatedOverlay;
        if (!allowed) {
            viewer.sendMessage(ChatColor.RED + "Nur für Zuschauer oder Admins/Streamer.");
            return;
        }
        // Use full list; filtering happens on player selection (non-spectators constrained to own team)
        viewer.openInventory(GuiHelper.createSpecteamTeamsMenu(teams, teamColors, specteamTeamKey));
    }
    private void openSpecteamPlayersMenu(Player viewer, String teamName) {
        List<UUID> members = teams.get(teamName);
        if (members == null || members.isEmpty()) {
            viewer.sendMessage(ChatColor.RED + "Team nicht gefunden oder leer.");
            return;
        }
        viewer.openInventory(GuiHelper.createSpecteamPlayersMenu(teamName, members, teamColors, specteamPlayerKey, specteamTeamKey));
    }

    // SupplyDrops are handled by SupplyDropService

    // Blitz-Finale placeholder removed (managed by HUD feature or future logic)

    // Hud/Border moved to HudBorderFeature

    // Spectator limiter removed

    // Tab header/footer moved to HudBorderFeature

    // ==== Scoreboard ==== (handled centrally by HudBorderFeature)

    // ==== Review GUI (Inventory) ====
    @SuppressWarnings("deprecation")
    public void openReviewGui(Player gm, Player target) { if (reviewFeature != null) reviewFeature.openReviewGui(gm, target); }

    // handled by ReviewFeature

    // no additional helpers

    // ==== Utils ====
    private long getProjectRemainingMs() {
    if (!gameRunning) return Long.MAX_VALUE;
    long now = System.currentTimeMillis();
    long elapsed = now - gameStartTime;
    // Abziehen: gesamte Pausenzeit + laufende Pause
    long pausedAccum = totalPausedMs + (pauseStartAt > 0L ? (now - pauseStartAt) : 0L);
    long activeElapsed = Math.max(0L, elapsed - pausedAccum);
    return Math.max(0L, projectDurationMs - activeElapsed);
    }

    private String formatTime(long ms) {
        long totalSec = Math.max(0L, ms) / 1000L;
        long h = totalSec / 3600L, m = (totalSec % 3600L) / 60L, s = totalSec % 60L;
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        return String.format("%02dm %02ds", m, s);
    }

    // Server MOTD with project remaining time
    @org.bukkit.event.EventHandler
    public void onPing(ServerListPingEvent e) {
        try {
            int online = Bukkit.getOnlinePlayers().size();
            String status;
            if (!gameRunning) {
                status = ChatColor.GRAY + "WARTET";
            } else if (paused) {
                status = ChatColor.GOLD + "PAUSE";
            } else if (finalPhaseActive) {
                status = ChatColor.RED + "FINALE";
            } else {
                status = ChatColor.GREEN + "LÄUFT";
            }

        String line1 = ChatColor.GOLD + "" + ChatColor.BOLD + "TRIVA" + ChatColor.RESET + ChatColor.DARK_GRAY + " | "
            + status + ChatColor.DARK_GRAY + " | " + ChatColor.AQUA + online + ChatColor.GRAY + " Spieler";

            String line2;
            if (gameRunning) {
                long rem = getProjectRemainingMs();
                // Fortschritt (0..1)
                double percentDone;
                if (projectDurationMs > 0L && projectDurationMs < Long.MAX_VALUE) {
                    long total = projectDurationMs;
                    long elapsedActive = Math.max(0L, total - rem);
                    percentDone = Math.min(1.0, Math.max(0.0, (double) elapsedActive / (double) total));
                } else {
                    percentDone = 0.0;
                }
                String bar = progressBar(percentDone, 16);
                double borderSize = 0.0;
                try { borderSize = Bukkit.getWorlds().get(0).getWorldBorder().getSize(); } catch (Throwable ignored2) {}
                line2 = bar + ChatColor.YELLOW + " Rest: " + ChatColor.WHITE + conciseTime(rem)
                        + ChatColor.GRAY + " | " + ChatColor.YELLOW + "Border: " + ChatColor.WHITE + (int) borderSize;
            } else {
                // Warte-Phase: Projektlänge + Teams
                String projLen;
                try {
                    long mins = projectDurationMinutes; // vorhandenes Feld
                    if (mins >= 60) {
                        long h = mins / 60; long m = mins % 60;
                        projLen = h + "h" + (m == 0 ? "" : (" " + m + "m"));
                    } else {
                        projLen = mins + "m";
                    }
                } catch (Throwable ex) { projLen = "?"; }
                line2 = ChatColor.GRAY + "Dauer: " + ChatColor.WHITE + projLen
                        + ChatColor.GRAY + " | Teams: " + ChatColor.WHITE + teams.size();
            }
            e.setMotd(line1 + "\n" + line2);
        } catch (Throwable ignored) {}
    }

    // Simple colored progress bar (percent 0..1) using Unicode blocks; fallback safe characters
    private String progressBar(double percent, int slots) {
        percent = Math.min(1.0, Math.max(0.0, percent));
        int done = (int) Math.round(percent * slots);
        if (done > slots) done = slots; if (done < 0) done = 0;
        ChatColor color = (percent < 0.5) ? ChatColor.GREEN : (percent < 0.8 ? ChatColor.GOLD : ChatColor.RED);
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.DARK_GRAY).append("[");
        for (int i = 0; i < slots; i++) {
            if (i < done) sb.append(color).append("█"); else sb.append(ChatColor.GRAY).append("·");
        }
        sb.append(ChatColor.DARK_GRAY).append("] ");
        return sb.toString();
    }

    private String conciseTime(long ms) {
        long totalSec = Math.max(0L, ms) / 1000L;
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
    if (h > 0) return String.format("%dh %02dmin", h, m);
    if (m > 0) return String.format("%dmin %02ds", m, s);
        return String.format("%ds", s);
    }

    // No GM BossBar functions; GM overlay is provided via HUD action bar

    // Whitelist helpers moved to WhitelistService

    // Arrow/direction helpers moved to HudBorderFeature

    // Textual compass direction towards center (German): used to instruct where to run away from border
    // Direction helper moved to HudBorderFeature


    // no-op: lobby helpers removed

    private void freezePlayer(Player p) {
        if (gameMasters.contains(p.getUniqueId())) { // GM are not frozen
            try { p.setAllowFlight(true); } catch (Throwable ignored) {}
            return;
        }
        frozen.add(p.getUniqueId());
        p.setWalkSpeed(0f);
        try { p.setAllowFlight(false); } catch (Throwable ignored) {}
    }
    private void unfreezePlayer(Player p) {
        frozen.remove(p.getUniqueId());
        p.setWalkSpeed(0.2f);
        try { if (gameMasters.contains(p.getUniqueId())) p.setAllowFlight(true); else p.setAllowFlight(false); } catch (Throwable ignored) {}
    }

    // Kills updated in DeathService

    private void updateTabName(Player p) {
        String tn = playerTeam.get(p.getUniqueId());
        if (tn != null) {
            ChatColor color = teamColors.getOrDefault(tn, ChatColor.WHITE);
            // Show team name (truncated) in tab; streamer names are pink even in a team
            String prefix = TabNameFormatter.teamPrefix(tn, color, 12);
            ChatColor nameColor = streamers.contains(p.getUniqueId()) ? ChatColor.LIGHT_PURPLE : ChatColor.WHITE;
            p.setPlayerListName(prefix + nameColor + p.getName());
        } else if (streamers.contains(p.getUniqueId())) {
            // Streamers: pink name
            p.setPlayerListName(ChatColor.LIGHT_PURPLE + "ST " + ChatColor.LIGHT_PURPLE + p.getName());
        } else if (gameMasters.contains(p.getUniqueId())) {
            p.setPlayerListName(ChatColor.RED + "GM " + ChatColor.WHITE + p.getName());
        } else {
            p.setPlayerListName(p.getName());
        }
    }

    // Truncate long team names for compact tab display, adding an ellipsis
    // shortenTeamName moved to TabNameFormatter

    public void resetVaro() {
        gameRunning = false; paused = false; blitzTriggered = false; gameStartTime = 0L;
        teams.clear(); playerTeam.clear(); teamReady.clear(); pendingInvite.clear();
        playerKills.clear(); frozen.clear(); spawnProtectionUntil.clear();
        netherUsedMs.clear(); netherSessionStart.clear(); lastNetherPortal.clear();
        warned30.clear(); warned10.clear(); warned5.clear(); warned1.clear(); netherOvertime.clear();
        lastPvP.clear(); trackerCooldown.clear();
    Bukkit.broadcastMessage(ChatColor.YELLOW + "TRIVA zurückgesetzt. Neue Runde kann gestartet werden!");
        WorldBorder wb = Bukkit.getWorlds().get(0).getWorldBorder();
        wb.setCenter(borderCenterX, borderCenterZ);
        wb.setSize(borderStartSize);
        saveState();
    }

    // === Public getters for features/services used by CommandsFeature ===
    public boolean isGameMaster(Player p) { return p != null && (de.varo.util.Permissions.isGm(p.getUniqueId(), gameMasters) || p.isOp()); }
    public GmFeature getGmFeature() { return gmFeature; }
    public TeamService getTeamService() { return teamService; }
    public GameService getGameService() { return gameService; }
    public StreamerService getStreamerService() { return streamerService; }
    public boolean isStreamer(Player p) { return p != null && streamers.contains(p.getUniqueId()); }
    public TrackerService getTrackerService() { return trackerService; }
    public SupplyDropService getSupplyDropService() { return supplyDropService; }
    public boolean isFinalPhaseActive() { return finalPhaseActive; }
    // === Pause Hooks ===
    public void handlePauseStart() {
        if (!gameRunning) return;
        if (pauseStartAt != 0L) return; // bereits pausiert
        pauseStartAt = System.currentTimeMillis();
        // Restzeit bis zum nächsten Shrink merken
        if (nextShrinkAt > 0L) shrinkPauseRemainingMs = Math.max(0L, nextShrinkAt - pauseStartAt);
        else shrinkPauseRemainingMs = shrinkIntervalMs;
        // Finalphase Fortschritt einfrieren durch Merken des Start-Zeitpunktes
        if (finalPhaseActive && finalPhasePauseStartedAt == 0L) finalPhasePauseStartedAt = pauseStartAt;
    }
    public void handlePauseEnd() {
        if (pauseStartAt == 0L) return; // nicht pausiert
        long now = System.currentTimeMillis();
        long pausedDur = now - pauseStartAt;
        totalPausedMs += Math.max(0L, pausedDur);
        pauseStartAt = 0L;
        // Shrink-Zeit wieder einplanen
        if (shrinkPauseRemainingMs > 0L) {
            nextShrinkAt = now + shrinkPauseRemainingMs;
        } else if (nextShrinkAt <= now) {
            nextShrinkAt = now + 5_000L; // kleiner Puffer falls bereits überfällig
        }
        shrinkPauseRemainingMs = 0L;
        // Finalphase Start verschieben, damit Fortschritt stehen blieb
        if (finalPhaseActive && finalPhasePauseStartedAt > 0L) {
            finalPhaseStartTime += pausedDur;
            finalPhasePauseStartedAt = 0L;
        }
        saveState();
    }

    // === Streamer privacy toggle ===
    public void toggleStreamerPrivacy(Player p) {
        UUID id = p.getUniqueId();
        boolean on;
        if (streamerPrivacy.contains(id)) { streamerPrivacy.remove(id); on = false; }
        else { streamerPrivacy.add(id); on = true; }
        p.sendMessage(on ? ChatColor.LIGHT_PURPLE + "Streamer-Schutz aktiv: Koordinaten werden verborgen." : ChatColor.GRAY + "Streamer-Schutz deaktiviert.");
        // Actionbar feedback
        try {
            String txt = on ? net.md_5.bungee.api.ChatColor.LIGHT_PURPLE + "Streamer-Schutz: AN" : net.md_5.bungee.api.ChatColor.GRAY + "Streamer-Schutz: AUS";
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(txt));
        } catch (Throwable ignored) {}
        // Optional: mark in tab name via update, if available
        try { updateTabName(p); } catch (Throwable ignored) {}
        // Also mute advancement announcements while active by using gamerule for the world if possible
        try {
            if (p.getWorld() != null) {
                p.getWorld().setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, !on);
                p.getWorld().setGameRule(org.bukkit.GameRule.REDUCED_DEBUG_INFO, on);
            }
        } catch (Throwable ignored) {}
    }
    public AdminToolsFeature getAdminTools() { return adminToolsFeature; }
    public ReportsFeature getReportsFeature() { return reportsFeature; }
    public WhitelistService getWhitelistService() { return whitelistService; }
    public InfoService getInfoService() { return infoService; }
    public StatsService getStatsService() { return statsService; }
    public de.varo.services.AutoCamService getAutoCamService() { return autoCamService; }
    public de.varo.services.FightRadarService getFightRadarService() { return fightRadarService; }
    public de.varo.services.LagWatchService getLagWatchService() { return lagWatchService; }
    public de.varo.services.LootLamaService getLootLamaService() { return lootLamaService; }
    public de.varo.services.DummyPlayerService getDummyPlayerService() { return dummyPlayerService; }
    public de.varo.services.NpcDummyService getNpcDummyService() { return npcDummyService; }

    // HUD settings accessors for LagWatchService
    public long getHudScoreboardUpdateMs() { return hudScoreboardUpdateMs; }
    public long getHudBorderParticleIntervalMs() { return hudBorderParticleIntervalMs; }
    public double getHudBorderNearDist() { return hudBorderNearDist; }
    public double getHudBorderParticleRadius() { return hudBorderParticleRadius; }
    public double getHudBorderParticleStep() { return hudBorderParticleStep; }
    public void applyHudSettingsPublic(long scoreboardMs, long borderIntervalMs, double nearDist, double radius, double step) {
        hudScoreboardUpdateMs = scoreboardMs;
        hudBorderParticleIntervalMs = borderIntervalMs;
        hudBorderNearDist = nearDist;
        hudBorderParticleRadius = radius;
        hudBorderParticleStep = step;
        try { if (hudBorderFeature != null) hudBorderFeature.applyHudSettings(scoreboardMs, borderIntervalMs, nearDist, radius, step); } catch (Throwable ignored) {}
    }

    // /lagreport — show TPS/MSPT and throttling status
    @SuppressWarnings("deprecation")
    public void cmdLagReport(Player p) {
        double[] tps;
        double mspt;
        try {
            tps = Bukkit.getServer().getTPS();
        } catch (Throwable ex) { tps = new double[]{20.0,20.0,20.0}; }
        try {
            mspt = Bukkit.getServer().getAverageTickTime();
        } catch (Throwable ex) { mspt = (tps[0] > 0 ? Math.min(50.0, 1000.0/Math.max(0.01,tps[0])) : 50.0); }
        p.sendMessage(Messages.title("Lag Report"));
        p.sendMessage(ChatColor.YELLOW + "TPS: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.1f", tps[0])
                + ChatColor.GRAY + ", " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.1f", tps[1])
                + ChatColor.GRAY + ", " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.1f", tps[2]));
        p.sendMessage(ChatColor.YELLOW + "MSPT: " + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.1f", mspt));
        // Entity count summary
        int ents = 0; int chunks = 0;
        try {
            for (org.bukkit.World w : Bukkit.getWorlds()) { ents += w.getEntities().size(); chunks += w.getLoadedChunks().length; }
        } catch (Throwable ignored) {}
        p.sendMessage(ChatColor.YELLOW + "Entities: " + ChatColor.WHITE + ents + ChatColor.GRAY + ", Chunks: " + ChatColor.WHITE + chunks);
        boolean throttled = (lagWatchService != null && lagWatchService.isThrottled());
        p.sendMessage(ChatColor.YELLOW + "Auto-Drossel: " + (lagAutoThrottle ? (throttled ? ChatColor.GOLD + "AKTIV" : ChatColor.GREEN + "bereit") : ChatColor.GRAY + "aus"));
    }

    // === Debug: Fake Teams & Spieler (nur zur UI/Test) ===
    @SuppressWarnings("deprecation")
    public void cmdDebugFakeTeams(Player sender, String[] args) {
        int teamCount = 5;
        int membersPer = 2;
        try {
            if (args != null && args.length >= 1) teamCount = Math.max(1, Math.min(20, Integer.parseInt(args[0])));
            if (args != null && args.length >= 2) membersPer = Math.max(1, Math.min(5, Integer.parseInt(args[1])));
        } catch (NumberFormatException ignored) {}

        // Clear existing (only if game not running to avoid corruption)
        if (gameRunning) { sender.sendMessage(ChatColor.RED + "Projekt läuft – keine Fake-Teams."); return; }
        teams.clear();
        playerTeam.clear();
        teamReady.clear();
        teamColors.clear();

        java.util.Random rnd = new java.util.Random();
        ChatColor[] palette = new ChatColor[]{ChatColor.BLUE,ChatColor.GREEN,ChatColor.GOLD,ChatColor.AQUA,ChatColor.LIGHT_PURPLE,ChatColor.YELLOW,ChatColor.DARK_AQUA,ChatColor.DARK_GREEN,ChatColor.DARK_PURPLE,ChatColor.DARK_RED,ChatColor.WHITE};
        for (int i=1;i<=teamCount;i++) {
            String tName = "Team" + i;
            java.util.List<java.util.UUID> list = new java.util.ArrayList<>();
            for (int m=1;m<=membersPer;m++) {
                // Virtual UUID, deterministic per team/member
                java.util.UUID vid = java.util.UUID.nameUUIDFromBytes((tName+":V"+m).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                list.add(vid);
                playerTeam.put(vid, tName);
            }
            teams.put(tName, list);
            teamReady.put(tName, rnd.nextBoolean());
            teamColors.put(tName, palette[i % palette.length]);
        }
        saveState();
        sender.sendMessage(ChatColor.GREEN + "Fake-Teams erstellt: " + ChatColor.WHITE + teamCount + ChatColor.GRAY + " × " + membersPer + " Spieler (virtuell)." );
        // Refresh HUD/Scoreboards for online players
        try { if (hudBorderFeature != null) hudBorderFeature.refreshAllScoreboards(true); } catch (Throwable ignored) {}
        // Spawn moving dummy ArmorStands to visualize players
        try { if (dummyPlayerService != null) dummyPlayerService.spawnForFakeTeams(teams, playerTeam, teamColors); } catch (Throwable ignored) {}
    }

    // /debugnpcteams <teams> <members> : nutzt ProtocolLib NPCs statt ArmorStands
    public void cmdDebugNpcTeams(Player sender, String[] args) {
        if (npcDummyService == null) { sender.sendMessage(ChatColor.RED + "ProtocolLib nicht geladen."); return; }
        int teamCount = 3, members = 2;
        try {
            if (args.length>0) teamCount = Math.max(1, Math.min(15, Integer.parseInt(args[0])));
            if (args.length>1) members = Math.max(1, Math.min(4, Integer.parseInt(args[1])));
        } catch (NumberFormatException ignored) {}
        if (gameRunning) { sender.sendMessage(ChatColor.RED + "Projekt läuft – keine NPC-Dummys."); return; }
        // Reuse fake team generator (no ArmorStand spawn here)
        teams.clear(); playerTeam.clear(); teamReady.clear(); teamColors.clear();
        java.util.Random rnd = new java.util.Random();
        ChatColor[] palette = new ChatColor[]{ChatColor.BLUE,ChatColor.GREEN,ChatColor.GOLD,ChatColor.AQUA,ChatColor.LIGHT_PURPLE,ChatColor.YELLOW,ChatColor.DARK_AQUA,ChatColor.DARK_GREEN,ChatColor.DARK_PURPLE,ChatColor.DARK_RED,ChatColor.WHITE};
        for (int i=1;i<=teamCount;i++) {
            String tName = "NTeam"+i;
            java.util.List<java.util.UUID> list = new java.util.ArrayList<>();
            for (int m=1;m<=members;m++) {
                java.util.UUID vid = java.util.UUID.nameUUIDFromBytes((tName+":VN"+m).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                list.add(vid); playerTeam.put(vid, tName);
            }
            teams.put(tName, list);
            teamReady.put(tName, rnd.nextBoolean());
            teamColors.put(tName, palette[i % palette.length]);
        }
        saveState();
        npcDummyService.spawnFromFakeTeams(teams, playerTeam, teamColors);
        sender.sendMessage(ChatColor.GREEN + "NPC-Fake-Teams erstellt ("+teamCount+"×"+members+").");
    }

    // /debugcleardummys — entfernt bewegte Debug-Dummys (ArmorStands)
    public void cmdDebugClearDummys(Player sender) {
        if (dummyPlayerService == null || !dummyPlayerService.hasDummys()) {
            sender.sendMessage(ChatColor.GRAY + "Keine Dummys aktiv.");
            return;
        }
        dummyPlayerService.clear();
        sender.sendMessage(ChatColor.GREEN + "Dummys entfernt.");
    }

    // ==== GM Zusatzfunktionen: Phasing (Sneak Spectator), Immunität, Angriffsblock ====
    @EventHandler(ignoreCancelled = true)
    public void onGmSneak(org.bukkit.event.player.PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!gameMasters.contains(p.getUniqueId())) return;
        try {
            if (e.isSneaking()) {
                if (p.getGameMode() == GameMode.ADVENTURE) p.setGameMode(GameMode.SPECTATOR);
            } else {
                if (p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.ADVENTURE);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(ignoreCancelled = true)
    public void onGmDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (!gameMasters.contains(p.getUniqueId())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGmAttack(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        Player p = (Player) e.getDamager();
        if (!gameMasters.contains(p.getUniqueId())) return;
        e.setCancelled(true);
        try { p.sendMessage(ChatColor.RED + "GM: Angriff blockiert."); } catch (Throwable ignored) {}
    }

    // ==== Finalphase Logik ====
    private void startFinalPhase() {
        if (finalPhaseActive) return;
        finalPhaseActive = true;
        finalPhaseStartTime = System.currentTimeMillis();
        WorldBorder wb = Bukkit.getWorlds().get(0).getWorldBorder();
        finalPhaseStartSize = wb.getSize();
        Bukkit.broadcastMessage(ChatColor.GOLD + "Finale Phase! Die Border schrumpft jetzt sehr langsam über 1 Stunde.");
        updateFinalPhaseBorder(true);
    }

    private void updateFinalPhaseBorder(boolean immediate) {
        if (!finalPhaseActive) return;
        WorldBorder wb = Bukkit.getWorlds().get(0).getWorldBorder();
    long nowTs = System.currentTimeMillis();
    long pausedAccum = totalPausedMs + (pauseStartAt > 0L ? (nowTs - pauseStartAt) : 0L);
    // Wenn Pause während Finalphase begonnen hat, wurde finalPhaseStartTime bei handlePauseEnd verschoben.
    long elapsed = nowTs - finalPhaseStartTime - pausedAccum;
    if (elapsed < 0L) elapsed = 0L;
        double extraShrink = finalPhaseDeaths * finalPhaseDeathShrinkPer;
        double targetMin = Math.max(5.0, finalPhaseMinSize - extraShrink); // nicht kleiner als 5
        double start = finalPhaseStartSize;
        if (start < targetMin) start = targetMin; // falls bereits kleiner
        double frac = Math.min(1.0, (double) elapsed / (double) finalPhaseDurationMs);
        double newSize = start - (start - targetMin) * frac;
        if (newSize < targetMin) newSize = targetMin;
        // Lerp Duration klein halten damit es smooth bleibt
        int lerp = immediate ? 1 : 10; // 0.5s-ish
        if (Math.abs(wb.getSize() - newSize) > 0.1) {
            try { wb.setSize(newSize, lerp); } catch (Throwable ignored) {}
        }
        if (!finalPhaseDoneAnnounced && elapsed >= finalPhaseDurationMs) {
            finalPhaseDoneAnnounced = true;
            Bukkit.broadcastMessage(ChatColor.RED + "Finale Phase abgeschlossen. Border minimal!");
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onPlayerDeathFinalPhase(org.bukkit.event.entity.PlayerDeathEvent e) {
        if (!finalPhaseActive) return;
        finalPhaseDeaths++;
        Bukkit.getScheduler().runTaskLater(this, () -> updateFinalPhaseBorder(true), 1L);
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "Border verkleinert sich zusätzlich durch den Tod! (" + finalPhaseDeaths + ")");
    }

    // ==== Helpers for death reason formatting ====
    // prettyEntityName/prettyCauseName moved into DeathService
}
