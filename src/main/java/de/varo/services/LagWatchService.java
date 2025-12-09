package de.varo.services;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.function.Supplier;

/**
 * Watches server TPS and auto-throttles HUD/particles to reduce load.
 */
public class LagWatchService {
    private final JavaPlugin plugin;
    private final Supplier<Boolean> autoOn;
    private final Supplier<Double> tpsLow;
    private final Supplier<Double> tpsRecover;
    private final Supplier<Double> scoreboardScale;
    private final Supplier<Double> borderIntervalScale;
    private final Supplier<Double> borderStepScale;

    private boolean throttled = false;
    // Keep originals to restore
    private long origScoreboardMs;
    private long origBorderIntervalMs;
    private double origNearDist;
    private double origRadius;
    private double origStep;
    private boolean capturedOrig = false;

    public LagWatchService(JavaPlugin plugin,
                           Supplier<Boolean> autoOn,
                           Supplier<Double> tpsLow,
                           Supplier<Double> tpsRecover,
                           Supplier<Double> scoreboardScale,
                           Supplier<Double> borderIntervalScale,
                           Supplier<Double> borderStepScale) {
        this.plugin = plugin; this.autoOn = autoOn; this.tpsLow = tpsLow; this.tpsRecover = tpsRecover;
        this.scoreboardScale = scoreboardScale; this.borderIntervalScale = borderIntervalScale; this.borderStepScale = borderStepScale;
        new BukkitRunnable(){ @Override public void run(){ tick(); } }.runTaskTimer(plugin, 100L, 100L);
    }

    public boolean isThrottled() { return throttled; }

    private void tick() {
        if (!(plugin instanceof de.varo.VaroPlugin)) return;
        de.varo.VaroPlugin vp = (de.varo.VaroPlugin) plugin;
        if (!autoOn.get()) { if (throttled) restore(vp); return; }
        double currTps;
        try { currTps = Bukkit.getServer().getTPS()[0]; } catch (Throwable ex) { currTps = 20.0; }
        if (!throttled && currTps <= tpsLow.get()) {
            throttle(vp);
        } else if (throttled && currTps >= tpsRecover.get()) {
            restore(vp);
        }
    }

    private void throttle(de.varo.VaroPlugin vp) {
        // Capture originals once
        if (!capturedOrig) {
            origScoreboardMs = vp.getHudScoreboardUpdateMs();
            origBorderIntervalMs = vp.getHudBorderParticleIntervalMs();
            origNearDist = vp.getHudBorderNearDist();
            origRadius = vp.getHudBorderParticleRadius();
            origStep = vp.getHudBorderParticleStep();
            capturedOrig = true;
        }
        long sMs = (long) Math.max(1000L, Math.round(origScoreboardMs * safe(scoreboardScale.get(), 1.0, 10.0)));
        long bInt = (long) Math.max(500L, Math.round(origBorderIntervalMs * safe(borderIntervalScale.get(), 1.0, 10.0)));
        double step = Math.max(1.0, origStep * safe(borderStepScale.get(), 1.0, 10.0));
        // Keep geometry; only slow down and reduce samples
        vp.applyHudSettingsPublic(sMs, bInt, origNearDist, origRadius, step);
        throttled = true;
    }

    private void restore(de.varo.VaroPlugin vp) {
        if (!capturedOrig) return;
        vp.applyHudSettingsPublic(origScoreboardMs, origBorderIntervalMs, origNearDist, origRadius, origStep);
        throttled = false;
    }

    private double safe(Double v, double min, double max) { if (v == null) return 1.0; return Math.max(min, Math.min(max, v)); }
}
