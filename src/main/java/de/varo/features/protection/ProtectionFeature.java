package de.varo.features.protection;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class ProtectionFeature implements Listener {
    private final Supplier<Boolean> paused;
    private final Set<UUID> frozen;
    private final Set<UUID> gameMasters;
    private final Map<UUID, Long> spawnProtectionUntil;
    private final Map<UUID, Long> rejoinProtectionUntil;
    private final Map<UUID, String> playerTeam;
    private final Map<UUID, Long> lastPvP;

    public ProtectionFeature(Supplier<Boolean> paused,
                             Set<UUID> frozen,
                             Set<UUID> gameMasters,
                             Map<UUID, Long> spawnProtectionUntil,
                             Map<UUID, Long> rejoinProtectionUntil,
                             Map<UUID, String> playerTeam,
                             Map<UUID, Long> lastPvP) {
        this.paused = paused;
        this.frozen = frozen;
        this.gameMasters = gameMasters;
        this.spawnProtectionUntil = spawnProtectionUntil;
        this.rejoinProtectionUntil = rejoinProtectionUntil;
        this.playerTeam = playerTeam;
        this.lastPvP = lastPvP;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();

        if (paused.get()) { e.setCancelled(true); return; }
        if (frozen.contains(p.getUniqueId()) && !gameMasters.contains(p.getUniqueId())) { e.setCancelled(true); return; }

        Long rp = rejoinProtectionUntil.get(p.getUniqueId());
        if (rp != null && rp > System.currentTimeMillis()) { e.setCancelled(true); return; }
        // Spawn-Schutz blockiert nur PvP; Umweltschaden/Mobschaden ist erlaubt (in onDamageByEntity geregelt)
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();

        if (paused.get()) { e.setCancelled(true); return; }

        long now = System.currentTimeMillis();

        Player attacker = null;
        if (e.getDamager() instanceof Player) attacker = (Player) e.getDamager();
        else if (e.getDamager() instanceof Projectile) {
            Projectile pr = (Projectile) e.getDamager();
            if (pr.getShooter() instanceof Player) attacker = (Player) pr.getShooter();
        }

        if (attacker != null) {
            Long ap = rejoinProtectionUntil.get(attacker.getUniqueId());
            if (ap != null && ap > now) { e.setCancelled(true); attacker.sendMessage(ChatColor.RED + "Rejoin-Schutz aktiv – du kannst nicht angreifen."); return; }

            Long aUntil = spawnProtectionUntil.get(attacker.getUniqueId());
            if ((frozen.contains(attacker.getUniqueId()) && !gameMasters.contains(attacker.getUniqueId())) || (aUntil != null && aUntil > now)) {
                e.setCancelled(true); attacker.sendMessage(ChatColor.RED + "Du hast Spawn-Schutz!"); return; }

            // kein Teamdamage
            String ta = playerTeam.get(attacker.getUniqueId());
            String tv = playerTeam.get(victim.getUniqueId());
            if (ta != null && ta.equals(tv)) { e.setCancelled(true); return; }
        }

        Long vUntil = spawnProtectionUntil.get(victim.getUniqueId());
        Long vp = rejoinProtectionUntil.get(victim.getUniqueId());
        boolean pvp = (attacker != null); // nur PvP, nicht Mobs/Umwelt
        if ((frozen.contains(victim.getUniqueId()) && !gameMasters.contains(victim.getUniqueId()))
            || (vp != null && vp > now)
            || (pvp && vUntil != null && vUntil > now)) {
            e.setCancelled(true); return; }

        long t = System.currentTimeMillis();
        if (attacker != null) {
            lastPvP.put(attacker.getUniqueId(), t);
            lastPvP.put(victim.getUniqueId(), t);
        }
    }
}
