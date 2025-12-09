package de.varo.services;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GameService {
    private final JavaPlugin plugin;

    private final Map<String, List<UUID>> teams;
    private final Map<String, Boolean> teamReady;
    private final Set<UUID> gameMasters;
    private final Map<UUID, Long> spawnProtectionUntil;

    private final Supplier<Boolean> isGameRunning;
    private final Consumer<Boolean> setGameRunning;
    private final Supplier<Boolean> isPaused;
    private final Consumer<Boolean> setPaused;
    private final Supplier<Boolean> isCenterSet;
    private final Consumer<Boolean> setCenterSet;
    private final Consumer<Long> setGameStartTime;

    private final Supplier<Double> getBorderCenterX;
    private final Consumer<Double> setBorderCenterX;
    private final Supplier<Double> getBorderCenterZ;
    private final Consumer<Double> setBorderCenterZ;

    private final Supplier<Double> getBorderStartSize;
    private final Supplier<Integer> getTeamSpreadRadius;

    private final Runnable scheduleNextShrink;
    private final Runnable saveState;
    private final Consumer<Player> freezePlayer;
    private final Consumer<Player> unfreezePlayer;

    private final long netherLimitMs;
    private final Map<UUID, Long> netherUsedMs;
    private final Map<UUID, Long> netherSessionStart;
    private final Set<UUID> warned30, warned10, warned5, warned1, netherOvertime;

    public GameService(JavaPlugin plugin,
                       Map<String, List<UUID>> teams,
                       Map<String, Boolean> teamReady,
                       Set<UUID> gameMasters,
                       Map<UUID, Long> spawnProtectionUntil,
                       Supplier<Boolean> isGameRunning,
                       Consumer<Boolean> setGameRunning,
                       Supplier<Boolean> isPaused,
                       Consumer<Boolean> setPaused,
                       Supplier<Boolean> isCenterSet,
                       Consumer<Boolean> setCenterSet,
                       Consumer<Long> setGameStartTime,
                       Supplier<Double> getBorderCenterX,
                       Consumer<Double> setBorderCenterX,
                       Supplier<Double> getBorderCenterZ,
                       Consumer<Double> setBorderCenterZ,
                       Supplier<Double> getBorderStartSize,
                       Supplier<Integer> getTeamSpreadRadius,
                       Runnable scheduleNextShrink,
                       Runnable saveState,
                       Consumer<Player> freezePlayer,
                       Consumer<Player> unfreezePlayer,
                       long netherLimitMs,
                       Map<UUID, Long> netherUsedMs,
                       Map<UUID, Long> netherSessionStart,
                       Set<UUID> warned30,
                       Set<UUID> warned10,
                       Set<UUID> warned5,
                       Set<UUID> warned1,
                       Set<UUID> netherOvertime) {
        this.plugin = plugin;
        this.teams = teams;
        this.teamReady = teamReady;
        this.gameMasters = gameMasters;
        this.spawnProtectionUntil = spawnProtectionUntil;
        this.isGameRunning = isGameRunning;
        this.setGameRunning = setGameRunning;
        this.isPaused = isPaused;
        this.setPaused = setPaused;
        this.isCenterSet = isCenterSet;
        this.setCenterSet = setCenterSet;
    this.setGameStartTime = setGameStartTime;
        this.getBorderCenterX = getBorderCenterX;
        this.setBorderCenterX = setBorderCenterX;
        this.getBorderCenterZ = getBorderCenterZ;
        this.setBorderCenterZ = setBorderCenterZ;
        this.getBorderStartSize = getBorderStartSize;
        this.getTeamSpreadRadius = getTeamSpreadRadius;
        this.scheduleNextShrink = scheduleNextShrink;
        this.saveState = saveState;
        this.freezePlayer = freezePlayer;
        this.unfreezePlayer = unfreezePlayer;
        this.netherLimitMs = netherLimitMs;
        this.netherUsedMs = netherUsedMs;
        this.netherSessionStart = netherSessionStart;
        this.warned30 = warned30;
        this.warned10 = warned10;
        this.warned5 = warned5;
        this.warned1 = warned1;
        this.netherOvertime = netherOvertime;
    }

    // /pausevaro
    @SuppressWarnings("deprecation")
    public void togglePause() {
        boolean paused = isPaused.get();
        paused = !paused;
        setPaused.accept(paused);
        if (paused) {
            for (Player p : Bukkit.getOnlinePlayers()) if (!gameMasters.contains(p.getUniqueId())) freezePlayer.accept(p);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "TRIVA pausiert. Bitte warten …");
            try { if (plugin instanceof de.varo.VaroPlugin) ((de.varo.VaroPlugin)plugin).handlePauseStart(); } catch (Throwable ignored) {}
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) if (!gameMasters.contains(p.getUniqueId())) unfreezePlayer.accept(p);
            Bukkit.broadcastMessage(ChatColor.GREEN + "TRIVA fortgesetzt!");
            try { if (plugin instanceof de.varo.VaroPlugin) ((de.varo.VaroPlugin)plugin).handlePauseEnd(); } catch (Throwable ignored) {}
            scheduleNextShrink.run();
        }
        saveState.run();
    }

    // /setcenter [x] [z]
    @SuppressWarnings("deprecation")
    public void setCenter(Player p, String[] args) {
        double x = p.getLocation().getX();
        double z = p.getLocation().getZ();
        if (args.length >= 2) {
            try { x = Double.parseDouble(args[0]); z = Double.parseDouble(args[1]); }
            catch (Exception ex) { p.sendMessage(ChatColor.RED + "Usage: /setcenter [x] [z] (oder ohne: aktuelle Position)"); return; }
        }
        WorldBorder wb = p.getWorld().getWorldBorder();
        wb.setCenter(x, z);
        setBorderCenterX.accept(x);
        setBorderCenterZ.accept(z);
        setCenterSet.accept(true);

        Location c = new Location(p.getWorld(), x, p.getWorld().getHighestBlockYAt((int)x, (int)z), z);
        for (int i=0;i<3;i++) c.clone().add(0,i,0).getBlock().setType(Material.GLASS);
        c.clone().add(0,3,0).getBlock().setType(Material.TORCH);

        Bukkit.broadcastMessage(ChatColor.AQUA + "Border-Mitte gesetzt: " + (int)x + ", " + (int)z
                + ChatColor.GRAY + "  (Start erst möglich, nachdem das Center gesetzt wurde)");
        saveState.run();
    }

    // /fertig (admin start)
    @SuppressWarnings("deprecation")
    public void startIfAllTeamsReady() {
        if (isGameRunning.get()) { Bukkit.broadcastMessage(ChatColor.RED + "Das Spiel läuft bereits!"); return; }
        if (!isCenterSet.get()) { Bukkit.broadcastMessage(ChatColor.RED + "Start gesperrt: Bitte zuerst /setcenter ausführen!"); return; }
        if (teams.isEmpty()) { Bukkit.broadcastMessage(ChatColor.RED + "Keine Teams vorhanden."); return; }
        for (Map.Entry<String, List<UUID>> e : teams.entrySet()) {
            if (e.getValue().size() < 2 || !teamReady.getOrDefault(e.getKey(), false)) {
                Bukkit.broadcastMessage(ChatColor.RED + "Nicht alle Teams sind fertig.");
                return;
            }
        }
        new BukkitRunnable() {
            int c = 10;
            @Override public void run() {
                try {
                    if (c == 0) { cancel(); performStartTeleportAndRun(); return; }
                    if (c == 10 || c == 5 || c == 1)
                        Bukkit.broadcastMessage(ChatColor.GREEN + "TRIVA beginnt in " + c + " Sekunden!");
                    c--;
                } catch (Throwable t) {
                    Bukkit.getLogger().warning("Start countdown task error: " + t.getMessage());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @SuppressWarnings("deprecation")
    private void performStartTeleportAndRun() {
        World world = Bukkit.getWorlds().get(0);
        WorldBorder wb = world.getWorldBorder();

        double cx = getBorderCenterX.get();
        double cz = getBorderCenterZ.get();
        wb.setCenter(cx, cz);
        wb.setSize(getBorderStartSize.get());
        wb.setWarningDistance(12);
        wb.setWarningTime(10);
        wb.setDamageBuffer(0);
        wb.setDamageAmount(0.1);

        buildGlassSpawnRing(world, teams.size(), cx, cz, getTeamSpreadRadius.get());

        int teamCount = teams.size();
        double angleStep = (teamCount <= 1) ? 0 : 360.0 / teamCount;
        double half = wb.getSize() / 2.0;
        double margin = 16;
        double maxRadiusInsideBorder = Math.max(32, half - margin);
        double radius = Math.min(Math.max(32, getTeamSpreadRadius.get()), maxRadiusInsideBorder);

        int idx = 0;
        for (Map.Entry<String, List<UUID>> e : teams.entrySet()) {
            double angle = Math.toRadians(idx * angleStep);
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            int y = world.getHighestBlockYAt((int) Math.round(x), (int) Math.round(z)) + 3;
            Location spawn = new Location(world, x + 0.5, y, z + 0.5);

            org.bukkit.util.Vector side = new org.bukkit.util.Vector(-Math.sin(angle), 0, Math.cos(angle)).normalize().multiply(1.5);

            int pi = 0;
            for (UUID u : e.getValue()) {
                Player pl = Bukkit.getPlayer(u);
                if (pl != null && pl.isOnline()) {
                    Location spot = spawn.clone().add(side.clone().multiply(pi==0? -1 : 1));
                    pl.teleport(spot);
                    unfreezePlayer.accept(pl);
                    spawnProtectionUntil.put(pl.getUniqueId(), System.currentTimeMillis() + 60_000L);
                    pl.sendMessage(ChatColor.GREEN + "Spawn-Schutz aktiv (60 Sekunden).");
                }
                pi++;
            }
            idx++;
        }

        setGameRunning.accept(true);
        setPaused.accept(false);
    if (setGameStartTime != null) setGameStartTime.accept(System.currentTimeMillis());
        try {
            world.setTime(1000L); // Daytime
            world.setStorm(false);
            world.setThundering(false);
        } catch (Throwable ignored) {}
    Bukkit.broadcastMessage(ChatColor.GOLD + "Alle Teams teleportiert. TRIVA beginnt!");
        scheduleNextShrink.run();
        saveState.run();
    // Scoreboard rendering is handled by HUD feature; no manual clearing here
    }

    private void buildGlassSpawnRing(World world, int teamCount, double cx, double cz, int teamSpreadRadius) {
        if (teamCount <= 0) return;
        WorldBorder wb = world.getWorldBorder();
        double half = wb.getSize() / 2.0;
        double margin = 16;
        double maxRadiusInsideBorder = Math.max(32, half - margin);
        double radius = Math.min(Math.max(32, teamSpreadRadius), maxRadiusInsideBorder);
        double angleStep = (teamCount <= 1) ? 0 : 360.0 / teamCount;

        for (int i = 0; i < teamCount; i++) {
            double angle = Math.toRadians(i * angleStep);
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            int by = world.getHighestBlockYAt((int)Math.round(x), (int)Math.round(z));

            for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) {
                world.getBlockAt((int)Math.round(x)+dx, by+1, (int)Math.round(z)+dz).setType(Material.GLASS);
            }
            world.getBlockAt((int)Math.round(x)-2, by+1, (int)Math.round(z)).setType(Material.GLASS);
            world.getBlockAt((int)Math.round(x)-2, by+2, (int)Math.round(z)).setType(Material.GLASS);
            world.getBlockAt((int)Math.round(x)+2, by+1, (int)Math.round(z)).setType(Material.GLASS);
            world.getBlockAt((int)Math.round(x)+2, by+2, (int)Math.round(z)).setType(Material.GLASS);
        }
    }

    // /netherreset <Spieler> [Minuten]
    @SuppressWarnings("deprecation")
    public void handleNetherReset(Player gm, String[] args) {
        if (args.length < 1) { gm.sendMessage(ChatColor.RED + "Usage: /netherreset <Spieler> [Minuten]"); return; }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) { gm.sendMessage(ChatColor.RED + "Spieler nicht online."); return; }

        long used = 0L;
        if (args.length >= 2) {
            try {
                double minsLeft = Double.parseDouble(args[1]);
                long remaining = (long) (minsLeft * 60_000L);
                used = Math.max(0L, netherLimitMs - remaining);
            } catch (NumberFormatException ex) {
                gm.sendMessage(ChatColor.RED + "Zeit ungültig."); return;
            }
        }
        if (netherSessionStart.containsKey(t.getUniqueId())) {
            long start = netherSessionStart.remove(t.getUniqueId());
            netherUsedMs.put(t.getUniqueId(), netherUsedMs.getOrDefault(t.getUniqueId(), 0L) + (System.currentTimeMillis() - start));
        }
        netherUsedMs.put(t.getUniqueId(), used);
        gm.sendMessage(ChatColor.GREEN + "Nether-Zeit von " + t.getName() + " gesetzt. Verbleibend: " + (formatTime(netherLimitMs - used)));
        t.sendMessage(ChatColor.YELLOW + "Deine Nether-Zeit wurde angepasst.");
        warned30.remove(t.getUniqueId()); warned10.remove(t.getUniqueId()); warned5.remove(t.getUniqueId()); warned1.remove(t.getUniqueId());
        netherOvertime.remove(t.getUniqueId());
        saveState.run();
    }

    private String formatTime(long ms) {
        long totalSec = Math.max(0L, ms) / 1000L;
        long h = totalSec / 3600L, m = (totalSec % 3600L) / 60L, s = totalSec % 60L;
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        return String.format("%02dm %02ds", m, s);
    }
}
