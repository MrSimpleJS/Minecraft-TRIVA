package de.varo.services;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class DeathService implements Listener {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final Map<String, List<UUID>> teams;
    private final Map<UUID, String> playerTeam;
    private final Set<UUID> streamers;
    private final Set<UUID> gameMasters;
    private final Map<UUID, Integer> playerKills;
    private final Map<UUID, Integer> playerDeaths;
    private final Map<UUID, UUID> forcedSpectateTargets;
    private final java.util.function.BiConsumer<Player, Player> startOverlay; // (viewer,target)
    private final java.util.function.Consumer<Player> applyObservationState;
    private final java.util.function.Consumer<Player> giveSpecteamSelector;

    public DeathService(org.bukkit.plugin.java.JavaPlugin plugin,
                        Map<String, List<UUID>> teams,
                        Map<UUID, String> playerTeam,
                        Set<UUID> streamers,
                        Set<UUID> gameMasters,
                        Map<UUID, Integer> playerKills,
                        Map<UUID, Integer> playerDeaths,
                        Map<UUID, UUID> forcedSpectateTargets,
                        java.util.function.BiConsumer<Player, Player> startOverlay,
                        java.util.function.Consumer<Player> applyObservationState,
                        java.util.function.Consumer<Player> giveSpecteamSelector) {
        this.plugin = plugin; this.teams = teams; this.playerTeam = playerTeam; this.streamers = streamers; this.gameMasters = gameMasters;
        this.playerKills = playerKills; this.playerDeaths = playerDeaths; this.forcedSpectateTargets = forcedSpectateTargets;
        this.startOverlay = startOverlay; this.applyObservationState = applyObservationState; this.giveSpecteamSelector = giveSpecteamSelector;
    }

    // === Assist Tracking ===
    private static class DamageRecord { final long ts; final UUID damager; final double dmg; DamageRecord(long ts, UUID d, double dmg){ this.ts=ts; this.damager=d; this.dmg=dmg; }}
    private final Map<UUID, java.util.List<DamageRecord>> recentDamage = new java.util.HashMap<>(); // victim -> records

    @EventHandler(ignoreCancelled = true)
    public void onDamage(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        Player damagerPlayer = null;
        org.bukkit.entity.Entity src = e.getDamager();
        if (src instanceof Player) damagerPlayer = (Player) src; else if (src instanceof Projectile) {
            Object sh = ((Projectile) src).getShooter();
            if (sh instanceof Player) damagerPlayer = (Player) sh;
        }
        if (damagerPlayer == null) return;
        if (damagerPlayer.getUniqueId().equals(victim.getUniqueId())) return;
        double finalDmg = e.getFinalDamage();
        if (finalDmg <= 0) return;
        long now = System.currentTimeMillis();
        int windowSec = plugin.getConfig().getInt("kills.assist.windowSeconds", 10);
        long cutoff = now - windowSec * 1000L;
        java.util.List<DamageRecord> list = recentDamage.computeIfAbsent(victim.getUniqueId(), k -> new java.util.ArrayList<>());
        list.add(new DamageRecord(now, damagerPlayer.getUniqueId(), finalDmg));
        // prune old
        if (list.size() > 1) list.removeIf(dr -> dr.ts < cutoff);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.setDeathMessage(null);
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        playerDeaths.merge(victim.getUniqueId(), 1, Integer::sum);

        String reason;
        String weapon = null;
        Double distanceM = null;
        Double killerHp = null;
        if (killer != null) {
            addKill(killer);
            reason = killer.getName();
            try {
                org.bukkit.inventory.ItemStack inHand = killer.getInventory().getItemInMainHand();
                if (inHand != null && inHand.getType() != org.bukkit.Material.AIR) weapon = inHand.getType().name();
                killerHp = killer.getHealth();
                distanceM = victim.getLocation().distance(killer.getLocation());
            } catch (Throwable ignored) {}
        } else {
            org.bukkit.event.entity.EntityDamageEvent last = victim.getLastDamageCause();
            if (last instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) {
                org.bukkit.entity.Entity damager = ((org.bukkit.event.entity.EntityDamageByEntityEvent) last).getDamager();
                if (damager instanceof Projectile) {
                    Object sh = ((Projectile) damager).getShooter();
                    if (sh instanceof org.bukkit.entity.Entity) damager = (org.bukkit.entity.Entity) sh;
                }
                reason = prettyEntityName(damager.getType());
            } else if (last != null) {
                reason = prettyCauseName(last.getCause());
            } else {
                reason = "Unbekannt";
            }
        }

        boolean isStreamer = streamers.contains(victim.getUniqueId());
        String team = playerTeam.get(victim.getUniqueId());
        if (isStreamer) {
            victim.setGameMode(GameMode.ADVENTURE);
            applyObservationState.accept(victim);
            giveSpecteamSelector.accept(victim);
            victim.sendMessage(ChatColor.LIGHT_PURPLE + "Streamer-Modus: " + ChatColor.YELLOW + "Du kannst jetzt alle Teams beobachten (Specteam-Selector)." );
            // open menu is up to caller
        }
        if (team == null) {
            if (!isStreamer) {
                victim.setGameMode(GameMode.SPECTATOR);
                victim.sendMessage(ChatColor.YELLOW + "Du bist Zuschauer.");
            }
            return;
        }

        List<UUID> mem = teams.getOrDefault(team, Collections.emptyList());
        UUID mateId = null;
        for (UUID id : mem) if (!id.equals(victim.getUniqueId())) { mateId = id; break; }

        boolean mateAlive = false;
        if (mateId != null) {
            org.bukkit.OfflinePlayer mop = Bukkit.getOfflinePlayer(mateId);
            if (mop.isOnline()) {
                Player mp = mop.getPlayer();
                mateAlive = (mp != null && mp.getGameMode() != GameMode.SPECTATOR && !mp.isDead());
            }
        }

        if (!isStreamer && mateAlive && mateId != null) {
            Player mp = Bukkit.getPlayer(mateId);
            if (mp != null) {
                victim.setGameMode(GameMode.ADVENTURE);
                applyObservationState.accept(victim);
                victim.teleport(mp.getLocation());
                forcedSpectateTargets.put(victim.getUniqueId(), mp.getUniqueId());
                startOverlay.accept(victim, mp);
                giveSpecteamSelector.accept(victim);
            }
            victim.sendMessage(ChatColor.YELLOW + "Du beobachtest jetzt deinen Team-Mate (Meta-Schutz aktiv).\n" + ChatColor.GRAY + "Du kannst nicht interagieren.");
        } else {
            if (!isStreamer) {
                victim.setGameMode(GameMode.SPECTATOR);
            }
        }

        int teamTotal = mem.size();
        int remaining = mateAlive ? Math.max(0, teamTotal - 1) : 0;
        // Broadcast killfeed line with extras
        String extras = "";
        if (distanceM != null) extras += ChatColor.GRAY + " [" + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.0f", distanceM) + "m" + ChatColor.GRAY + "]";
        if (weapon != null) extras += ChatColor.GRAY + " [" + ChatColor.WHITE + weapon + ChatColor.GRAY + "]";
        if (killerHp != null) extras += ChatColor.GRAY + " (" + ChatColor.WHITE + String.format(java.util.Locale.ROOT, "%.1f", killerHp) + ChatColor.RED + "❤" + ChatColor.GRAY + ")";
        String assistSegment = buildAssistSegment(victim.getUniqueId(), (killer!=null? killer.getUniqueId(): null));
        String killLine = ChatColor.DARK_RED + "✖ " + ChatColor.RED + victim.getName()
            + ChatColor.GRAY + " eliminiert durch " + ChatColor.GOLD + reason + extras
            + (assistSegment.isEmpty()? "" : ChatColor.DARK_AQUA + " Assist:" + assistSegment)
            + ChatColor.GRAY + " — verbleibend (" + remaining + "/" + teamTotal + ")";
        Bukkit.broadcastMessage(killLine);

        // GM-only [TP] button linking to death location for fast review
        try {
            org.bukkit.Location dl = victim.getLocation();
            if (dl != null && dl.getWorld() != null) {
                int x = dl.getBlockX();
                int y = Math.max(1, Math.min(319, dl.getBlockY()));
                int z = dl.getBlockZ();
                String wn = dl.getWorld().getName();
                net.md_5.bungee.api.chat.TextComponent tpBtn = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.ChatColor.RED + "[TP]");
                tpBtn.setUnderlined(true);
                tpBtn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                    String.format(java.util.Locale.ROOT, "/poitp atw %s %d %d %d", wn, x, y, z)));
                net.md_5.bungee.api.chat.TextComponent prefix = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.ChatColor.DARK_RED + "Kill: ");
                net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.ChatColor.WHITE + ChatColor.stripColor(killLine));
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    if (gameMasters.contains(pl.getUniqueId()) || pl.isOp()) {
                        pl.spigot().sendMessage(new net.md_5.bungee.api.chat.TextComponent[]{ prefix, msg, new net.md_5.bungee.api.chat.TextComponent(" "), tpBtn });
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (!mateAlive) {
        // GM-only temporary death marker (particles) with TTL
        try {
            int ttl = plugin.getConfig().getInt("kills.deathMarkerSeconds", 30);
            if (ttl > 0) {
                org.bukkit.Location dl = victim.getLocation();
                new BukkitRunnable(){
                    int ticks = 0;
                    @Override public void run(){
                        if (ticks++ >= ttl*20/10) { cancel(); return; }
                        for (Player pl : Bukkit.getOnlinePlayers()) {
                            if (!(gameMasters.contains(pl.getUniqueId()) || pl.isOp())) continue;
                            try { pl.spawnParticle(org.bukkit.Particle.DUST, dl.clone().add(0.5, 0.2, 0.5), 18, 0.6, 0.3, 0.6, 0,
                                new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(200,30,30), 1.2f)); } catch (Throwable ignored) {}
                        }
                    }
                }.runTaskTimer(plugin, 0L, 10L);
            }
        } catch (Throwable ignored) {}
            String reasonV = "Ausgeschieden, getötet von: " + reason;
            String reasonM = "Mate wurde getötet von: " + reason;
            for (UUID id : mem) {
                if (gameMasters.contains(id) || streamers.contains(id)) continue;
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                Bukkit.getBanList(BanList.Type.NAME).addBan(op.getName(), (id.equals(victim.getUniqueId()) ? reasonV : reasonM), null, "TRIVA");
                new BukkitRunnable(){
                    @Override public void run(){
                        try {
                            Player online = Bukkit.getPlayer(id);
                            if (online != null && online.isOnline()) {
                                online.kickPlayer(ChatColor.RED + (id.equals(victim.getUniqueId()) ? reasonV : reasonM));
                            }
                        } catch (Throwable t) {
                            plugin.getLogger().warning("Kick task failed for UUID " + id + ": " + t.getMessage());
                        }
                    }
                }.runTaskLater(plugin, 20L);
            }
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Team " + team + " wurde eliminiert!");
        }
    }

    private void addKill(Player killer) {
        playerKills.put(killer.getUniqueId(), playerKills.getOrDefault(killer.getUniqueId(), 0) + 1);
    }

    private String buildAssistSegment(UUID victimId, UUID killerId) {
        java.util.List<DamageRecord> list = recentDamage.get(victimId);
        if (list == null || list.isEmpty()) return "";
        int windowSec = plugin.getConfig().getInt("kills.assist.windowSeconds", 10);
        long cutoff = System.currentTimeMillis() - windowSec * 1000L;
        double minPercent = plugin.getConfig().getDouble("kills.assist.minPercent", 0.15);
        int maxShown = plugin.getConfig().getInt("kills.assist.maxShown", 3);
        java.util.Map<UUID, Double> dmgPer = new java.util.HashMap<>();
        final double[] totalRef = {0.0};
        for (DamageRecord dr : list) {
            if (dr.ts < cutoff) continue;
            if (killerId != null && dr.damager.equals(killerId)) continue; // killer excluded from assists
            dmgPer.merge(dr.damager, dr.dmg, Double::sum);
            totalRef[0] += dr.dmg;
        }
        // prune victim map to avoid memory leak
        if (list.size() > 50) list.removeIf(dr -> dr.ts < cutoff);
        if (totalRef[0] <= 0) return "";
        java.util.List<java.util.Map.Entry<UUID, Double>> entries = new java.util.ArrayList<>(dmgPer.entrySet());
        entries.removeIf(en -> en.getValue() / totalRef[0] < minPercent);
        if (entries.isEmpty()) return "";
        entries.sort((a,b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (java.util.Map.Entry<UUID, Double> en : entries) {
            if (shown >= maxShown) break;
            UUID id = en.getKey();
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op.getName() == null ? "Spieler" : op.getName();
            double pct = (en.getValue() / totalRef[0]) * 100.0;
            sb.append(" ").append(ChatColor.AQUA).append(name).append(ChatColor.GRAY).append("(")
              .append(ChatColor.WHITE).append(String.format(java.util.Locale.ROOT, "%.0f", pct)).append("%")
              .append(ChatColor.GRAY).append(")");
            shown++;
        }
        return sb.toString();
    }

    private String prettyEntityName(org.bukkit.entity.EntityType type) {
        switch (type) {
            case ZOMBIE: return "Zombie";
            case SKELETON: return "Skelett";
            case CREEPER: return "Creeper";
            case SPIDER: return "Spinne";
            case ENDERMAN: return "Enderman";
            case BLAZE: return "Blaze";
            case WITHER: return "Wither";
            case ENDER_DRAGON: return "Enderdrache";
            case WARDEN: return "Wächter";
            case GHAST: return "Ghast";
            case PILLAGER: return "Plünderer";
            case VEX: return "Vex";
            case IRON_GOLEM: return "Eisengolem";
            case WOLF: return "Wolf";
            default:
                String raw = type.name().toLowerCase(java.util.Locale.ROOT).replace('_',' ');
                return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }
    }

    private String prettyCauseName(org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        switch (cause) {
            case FALL: return "Fallschaden";
            case LAVA: return "Lava";
            case FIRE: return "Feuer";
            case FIRE_TICK: return "Verbrennung";
            case DROWNING: return "Ertrinken";
            case SUFFOCATION: return "Ersticken";
            case STARVATION: return "Verhungern";
            case VOID: return "Leere";
            case WITHER: return "Wither";
            case MAGIC: return "Magie";
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION: return "Explosion";
            case LIGHTNING: return "Blitz";
            case POISON: return "Gift";
            case FREEZE: return "Erfrierung";
            case DRAGON_BREATH: return "Drachenatem";
            case HOT_FLOOR: return "Magmablock";
            case CONTACT: return "Kaktus";
            case CRAMMING: return "Gedränge";
            case THORNS: return "Dornen";
            case SONIC_BOOM: return "Sonic Boom";
            case WORLD_BORDER: return "Weltgrenze";
            default:
                String raw = cause.name().toLowerCase(java.util.Locale.ROOT).replace('_',' ');
                return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }
    }
}
