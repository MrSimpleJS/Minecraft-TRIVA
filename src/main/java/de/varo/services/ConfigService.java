package de.varo.services;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Centralized loader for config values and derived settings.
 */
public class ConfigService {

    public static class ConfigValues {
        // Border
        public double borderStartSize;
        public double borderEndSize;
        public int borderShrinkEveryMinutes;
        public int borderShrinkLerpSeconds;
        public double borderCenterX;
        public double borderCenterZ;

        // Project
        public int projectDurationMinutes;
        public long projectDurationMs;
        public int teamSpreadRadius;

        // Nether
        public long netherLimitMs;

        // Supplydrop
        public boolean supplyDropEnabled;
        public int supplyDropInterval;
        public List<String> supplyDropLoot = new ArrayList<>();

        // Tracker
        public long trackerCooldownMs;

        // PvP
        public long combatTagMs;
        public long rejoinProtectMs;
        public int loggerNpcSeconds;

        // AFK
        public long afkKickMs;

        // Derived
        public long shrinkIntervalMs;

    // HUD tuning
    public long hudScoreboardUpdateMs;
    public long hudBorderParticleIntervalMs;
    public double hudBorderNearDist;
    public double hudBorderParticleRadius;
    public double hudBorderParticleStep;

        // Privacy/Streamer
        public boolean privacyMaskCoords;
        public int spectateDelaySeconds;

        // AntiCheat mining thresholds
        public int acWindowMinutes;
        public int acDiamondPerBlocks;
        public double acDiamondMaxRatio;
        public int acDiamondFastFind;
        public int acDebrisPerBlocks;
        public double acDebrisMaxRatio;
        public int acDebrisFastFind;
        public int acBranchWithinSteps;
        public int acBranchWarnCount;

        // Whitelist
        public long wlCodeTtlMs;

        // Language
        public String language;

        // Keys
        public NamespacedKey trackerKey;
        public NamespacedKey specteamTeamKey;
        public NamespacedKey specteamPlayerKey;
        public NamespacedKey specteamSelectorKey;
        public NamespacedKey combatNpcKey;
        public NamespacedKey gmTpMenuKey;
        public NamespacedKey gmReviewKey;
        public NamespacedKey gmMiningKey;
        public NamespacedKey gmReportsKey;

    // Fight Radar
    public int fightRadarLookbackSeconds;
    public int fightRadarPulseEverySeconds;
    public int fightRadarMaxDistance;
    public boolean fightRadarShowTeamName;

    // Performance auto-throttle
    public boolean lagAutoThrottle;
    public double lagTpsLow;
    public double lagTpsRecover;
    public double lagScoreboardScale;
    public double lagBorderIntervalScale;
    public double lagBorderStepScale;
    // Debug toggles
    public boolean debugAllowRuntimeShrinkAdjust;
    public boolean debugAllowRuntimeProjectTimeAdjust;
    }

    public ConfigValues load(JavaPlugin plugin) {
        FileConfiguration cfg = plugin.getConfig();

        ConfigValues v = new ConfigValues();

        // Border
        v.borderStartSize = cfg.getDouble("border.startSize", 4000);
        v.borderEndSize = cfg.getDouble("border.endSize", 100);
        v.borderShrinkEveryMinutes = cfg.getInt("border.shrinkEveryMinutes", 20);
        v.borderShrinkLerpSeconds = cfg.getInt("border.shrinkLerpSeconds", 30);
        v.borderCenterX = cfg.getDouble("border.centerX", 0);
        v.borderCenterZ = cfg.getDouble("border.centerZ", 0);

        // Project
        v.projectDurationMinutes = cfg.getInt("project.totalMinutes", 180);
        v.projectDurationMs = v.projectDurationMinutes * 60_000L;
        v.teamSpreadRadius = cfg.getInt("project.teamSpreadRadius", 700);

        // Nether
        v.netherLimitMs = cfg.getInt("nether.limitMinutes", 60) * 60_000L;

        // Supplydrop
        v.supplyDropEnabled = cfg.getBoolean("supplydrop.enabled", true);
        v.supplyDropInterval = cfg.getInt("supplydrop.intervalMinutes", 30);
        v.supplyDropLoot = new ArrayList<>(cfg.getStringList("supplydrop.loot"));

        // Tracker
        v.trackerCooldownMs = cfg.getInt("tracker.cooldownSeconds", 10) * 1000L;

        // PvP
        v.combatTagMs = cfg.getInt("pvp.combatTagSeconds", 20) * 1000L;
        v.rejoinProtectMs = cfg.getInt("pvp.rejoinProtectSeconds", 5) * 1000L;
        v.loggerNpcSeconds = Math.max(5, cfg.getInt("pvp.loggerNpcSeconds", 30));

        // AFK
        v.afkKickMs = cfg.getInt("afk.kickMinutes", 5) * 60_000L;

        // Derived
        v.shrinkIntervalMs = v.borderShrinkEveryMinutes * 60_000L;

    // HUD tuning
    v.hudScoreboardUpdateMs = cfg.getLong("hud.scoreboardUpdateMs", 4000L);
    v.hudBorderParticleIntervalMs = cfg.getLong("hud.borderParticles.intervalMs", 1500L);
    v.hudBorderNearDist = cfg.getDouble("hud.borderParticles.nearDist", 28.0);
    v.hudBorderParticleRadius = cfg.getDouble("hud.borderParticles.radius", 18.0);
    v.hudBorderParticleStep = cfg.getDouble("hud.borderParticles.step", 4.0);

        // Privacy
        v.privacyMaskCoords = cfg.getBoolean("privacy.maskStreamerCoords", true);
        v.spectateDelaySeconds = Math.max(0, cfg.getInt("privacy.spectateDelaySeconds", 0));

        // AntiCheat mining thresholds
        v.acWindowMinutes     = cfg.getInt("ac.mining.windowMinutes", 10);
        v.acDiamondPerBlocks  = cfg.getInt("ac.mining.diamond.perBlocks", 200);
        v.acDiamondMaxRatio   = cfg.getDouble("ac.mining.diamond.maxPerWindowRatio", 1.2);
        v.acDiamondFastFind   = cfg.getInt("ac.mining.diamond.fastFindPerWindow", 8);
        v.acDebrisPerBlocks   = cfg.getInt("ac.mining.debris.perBlocks", 100);
        v.acDebrisMaxRatio    = cfg.getDouble("ac.mining.debris.maxPerWindowRatio", 1.5);
        v.acDebrisFastFind    = cfg.getInt("ac.mining.debris.fastFindPerWindow", 3);
        v.acBranchWithinSteps = cfg.getInt("ac.mining.branch.withinSteps", 3);
        v.acBranchWarnCount   = cfg.getInt("ac.mining.branch.warnCountInWindow", 4);

        // Whitelist
        v.wlCodeTtlMs = cfg.getInt("whitelist.codeTtlMinutes", 60) * 60_000L;

        // Language
        try {
            v.language = cfg.getString("language", "de");
        } catch (Throwable ignored) {
            v.language = "de";
        }

        // Keys
        v.trackerKey = new NamespacedKey(plugin, "player_tracker");
        v.specteamTeamKey = new NamespacedKey(plugin, "specteam_team");
        v.specteamPlayerKey = new NamespacedKey(plugin, "specteam_player");
        v.specteamSelectorKey = new NamespacedKey(plugin, "specteam_selector");
        v.combatNpcKey = new NamespacedKey(plugin, "combat_npc");
        v.gmTpMenuKey = new NamespacedKey(plugin, "gm_tp_menu");
        v.gmReviewKey = new NamespacedKey(plugin, "gm_review");
        v.gmMiningKey = new NamespacedKey(plugin, "gm_mining");
        v.gmReportsKey = new NamespacedKey(plugin, "gm_reports");

    // Fight Radar
    v.fightRadarLookbackSeconds = cfg.getInt("fightradar.lookbackSeconds", 15);
    v.fightRadarPulseEverySeconds = cfg.getInt("fightradar.pulseEverySeconds", 3);
    v.fightRadarMaxDistance = cfg.getInt("fightradar.maxDistance", 600);
    v.fightRadarShowTeamName = cfg.getBoolean("fightradar.showTeamName", true);

    // Performance auto-throttle
    v.lagAutoThrottle = cfg.getBoolean("performance.autoThrottle.enabled", true);
    v.lagTpsLow = cfg.getDouble("performance.autoThrottle.tpsLow", 17.5);
    v.lagTpsRecover = cfg.getDouble("performance.autoThrottle.tpsRecover", 19.5);
    v.lagScoreboardScale = cfg.getDouble("performance.autoThrottle.scale.scoreboardMs", 2.0);
    v.lagBorderIntervalScale = cfg.getDouble("performance.autoThrottle.scale.borderIntervalMs", 2.0);
    v.lagBorderStepScale = cfg.getDouble("performance.autoThrottle.scale.borderStep", 1.5);

    // Debug
    v.debugAllowRuntimeShrinkAdjust = cfg.getBoolean("debug.allowRuntimeShrinkAdjust", false);
    v.debugAllowRuntimeProjectTimeAdjust = cfg.getBoolean("debug.allowRuntimeProjectTimeAdjust", false);

        return v;
    }
}
