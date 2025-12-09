package de.varo.services;

import de.varo.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class JoinFlowService implements Listener {
    private final Set<UUID> gameMasters;
    private final Set<UUID> streamers;
    private final Map<UUID, Long> lastPvP;
    private final Map<UUID, Long> spawnProtectionUntil;
    private final Map<UUID, Long> rejoinProtectionUntil;
    private final java.util.function.Supplier<Boolean> isGameRunning;
    private final java.util.function.Consumer<Player> freezePlayer;
    // unfreeze not needed in join flow; kept minimal
    private final java.util.function.BiConsumer<Player, Boolean> updateScoreboardFor; // (player, force)
    private final java.util.function.Consumer<Player> updateTabName;
    private final java.util.function.Consumer<Player> giveSpecteamSelector;
    private final java.util.function.Consumer<Player> scheduleOpenSpecteamMenu;
    // Rules/First-join helpers
    private final java.util.function.Function<UUID, String> teamOf;
    private final java.util.function.Consumer<Player> scheduleOpenRulesBook;
    private final java.util.function.Predicate<UUID> hasAcceptedRules;
    private final long combatTagMs;
    private final long rejoinProtectMs;

    public JoinFlowService(Set<UUID> gameMasters,
                           Set<UUID> streamers,
                           Map<UUID, Long> lastPvP,
                           Map<UUID, Long> spawnProtectionUntil,
                           Map<UUID, Long> rejoinProtectionUntil,
                           java.util.function.Supplier<Boolean> isGameRunning,
                           java.util.function.Consumer<Player> freezePlayer,
                           java.util.function.BiConsumer<Player, Boolean> updateScoreboardFor,
                           java.util.function.Consumer<Player> updateTabName,
                           java.util.function.Consumer<Player> giveSpecteamSelector,
                           java.util.function.Consumer<Player> scheduleOpenSpecteamMenu,
                           java.util.function.Function<UUID, String> teamOf,
                           java.util.function.Consumer<Player> scheduleOpenRulesBook,
                           java.util.function.Predicate<UUID> hasAcceptedRules,
                           long combatTagMs,
                           long rejoinProtectMs) {
        this.gameMasters = gameMasters; this.streamers = streamers; this.lastPvP = lastPvP;
        this.spawnProtectionUntil = spawnProtectionUntil; this.rejoinProtectionUntil = rejoinProtectionUntil;
        this.isGameRunning = isGameRunning; this.freezePlayer = freezePlayer;
        this.updateScoreboardFor = updateScoreboardFor; this.updateTabName = updateTabName;
        this.giveSpecteamSelector = giveSpecteamSelector; this.scheduleOpenSpecteamMenu = scheduleOpenSpecteamMenu;
        this.teamOf = teamOf; this.scheduleOpenRulesBook = scheduleOpenRulesBook; this.hasAcceptedRules = hasAcceptedRules;
        this.combatTagMs = combatTagMs; this.rejoinProtectMs = rejoinProtectMs;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Custom join message by role
        String roleLabel; org.bukkit.ChatColor roleColor;
        if (gameMasters.contains(p.getUniqueId()) || p.isOp()) { roleLabel = Lang.tr("join.role.gm"); roleColor = org.bukkit.ChatColor.RED; }
        else if (streamers.contains(p.getUniqueId())) { roleLabel = Lang.tr("join.role.streamer"); roleColor = org.bukkit.ChatColor.LIGHT_PURPLE; }
        else { roleLabel = Lang.tr("join.role.player"); roleColor = org.bukkit.ChatColor.YELLOW; }
        String joinMsg = roleColor + roleLabel + org.bukkit.ChatColor.GRAY + " \"" + org.bukkit.ChatColor.WHITE + p.getName() + org.bukkit.ChatColor.GRAY + "\" " + Lang.tr("join.message");
        e.setJoinMessage(joinMsg);

        if (!isGameRunning.get()) handlePreGameJoin(p); else handleActiveGameJoin(p);
        // Strip or give items handled by callers via helpers
        updateTabName.accept(p);
        updateScoreboardFor.accept(p, true);

        // First-join rules: if regular player has no team and hasn't accepted rules yet, open book after short delay
        try {
            boolean noTeam = (teamOf.apply(p.getUniqueId()) == null);
            boolean accepted = hasAcceptedRules.test(p.getUniqueId());
            boolean isGmOrOp = gameMasters.contains(p.getUniqueId()) || p.isOp();
            if (!isGmOrOp && noTeam && !accepted) {
                scheduleOpenRulesBook.accept(p);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.setQuitMessage(null);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        boolean isStreamer = streamers.contains(p.getUniqueId());
        // Forced overlay check and re-attachment handled by outer plugin’s spectate feature
        if (!isStreamer) return;
        p.setGameMode(GameMode.ADVENTURE);
        freezePlayer.accept(p);
        giveSpecteamSelector.accept(p);
        scheduleOpenSpecteamMenu.accept(p);
    }

    private void handlePreGameJoin(Player p) {
        World w = Bukkit.getWorlds().get(0);
        Location center = new Location(w, 0.5, w.getHighestBlockYAt(0, 0) + 1, 0.5);
        if (!p.getWorld().equals(w) || p.getLocation().distanceSquared(center) > 9.0) p.teleport(center);
        if (!gameMasters.contains(p.getUniqueId())) {
            freezePlayer.accept(p);
            p.sendMessage(org.bukkit.ChatColor.AQUA + Lang.tr("join.freeze"));
        } else {
            try { p.setAllowFlight(true); p.setFlying(true); } catch (Throwable ignored) {}
        }
        p.sendMessage(org.bukkit.ChatColor.AQUA + Lang.tr("join.team.create") + org.bukkit.ChatColor.YELLOW + "/team create <Name>");
        p.sendMessage(org.bukkit.ChatColor.AQUA + Lang.tr("join.team.invite") + org.bukkit.ChatColor.YELLOW + "/team invite <Spieler>");
        p.sendMessage(org.bukkit.ChatColor.AQUA + Lang.tr("join.team.accept") + org.bukkit.ChatColor.YELLOW + "/team accept <Teamname|Spielername>");
        p.sendMessage(org.bukkit.ChatColor.AQUA + Lang.tr("join.team.ready") + org.bukkit.ChatColor.YELLOW + "/teamfertig" + org.bukkit.ChatColor.AQUA + ".");
    }

    private void handleActiveGameJoin(Player p) {
        Long last = lastPvP.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < combatTagMs) {
            long until = System.currentTimeMillis() + rejoinProtectMs;
            rejoinProtectionUntil.put(p.getUniqueId(), until);
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, (int)(rejoinProtectMs/50), 0, true, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, (int)(rejoinProtectMs/50), 4, true, false, false));
            p.sendMessage(org.bukkit.ChatColor.YELLOW + Lang.tr("join.rejoinProtect", (rejoinProtectMs/1000)));
        } else {
            spawnProtectionUntil.put(p.getUniqueId(), System.currentTimeMillis() + 60_000L);
            p.sendMessage(org.bukkit.ChatColor.GREEN + Lang.tr("join.spawnProtect"));
        }
    }
}
