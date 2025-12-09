package de.varo.features.commands;

import de.varo.VaroPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CommandsFeature implements CommandExecutor, TabCompleter {
    private final VaroPlugin plugin;

    public CommandsFeature(VaroPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (!(sender instanceof Player)) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        if (c.equals("wlcode")) {
            if (plugin.getWhitelistService() != null) plugin.getWhitelistService().cmdWlCode(sender, args);
            else sender.sendMessage(ChatColor.RED + "WL-Dienst fehlt.");
            return true;
        }
        sender.sendMessage("Nur ingame nutzbar.");
        return true;
    }
    Player p = (Player) sender;
        String c = cmd.getName().toLowerCase(Locale.ROOT);

        switch (c) {
            case "help":
            case "hilfe":
            case "varohelp":
                plugin.cmdHelp(p); return true;

            case "regeln":
            case "rules":
                plugin.cmdRegeln(p); return true;
            case "rulesaccept":
                if (plugin.hasAcceptedRules(p.getUniqueId())) { p.sendMessage(ChatColor.GRAY + "Schon akzeptiert."); return true; }
                plugin.markAcceptedRules(p);
                p.sendMessage(ChatColor.GREEN + "Regeln akzeptiert. Viel Erfolg!");
                return true;
            case "hud":
                plugin.toggleHud(p); return true;
            case "hudset":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                plugin.cmdHudSet(p, args); return true;
            case "debugshrink":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                plugin.cmdDebugShrink(p, args); return true;
            case "debugprojzeit":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                plugin.cmdDebugProjzeit(p, args); return true;
            case "gm":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur OP!"); return true; }
                plugin.toggleGm(p); return true;

            case "tpmenu":
                if (!plugin.isGameMaster(p)) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (plugin.getGmFeature() != null) plugin.getGmFeature().openTeleportMenu(p); return true;

            case "team":
                if (plugin.getTeamService() != null) plugin.getTeamService().handleTeamCommand(p, args); return true;

            case "teamfertig":
                if (plugin.getTeamService() != null) plugin.getTeamService().handleTeamFertig(p); return true;

            case "fertig":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                if (plugin.getGameService() != null) plugin.getGameService().startIfAllTeamsReady(); return true;

            case "netherreset":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                if (plugin.getGameService() != null) plugin.getGameService().handleNetherReset(p, args); return true;

            case "resetvaro":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                plugin.resetVaro(); return true;

            case "who":
            case "alive":
                if (plugin.getInfoService() != null) plugin.getInfoService().cmdWho(p); else plugin.cmdWho(p); return true;

            case "pausevaro":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                if (plugin.getGameService() != null) plugin.getGameService().togglePause(); return true;

            case "setcenter":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                if (plugin.getGameService() != null) plugin.getGameService().setCenter(p, args); return true;

            case "streamer":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                if (plugin.getStreamerService() != null) plugin.getStreamerService().handleStreamerCommand(p, args); return true;

            case "tracker":
                if (plugin.getTrackerService() != null) plugin.getTrackerService().giveInactiveTracker(p); return true;

            case "supplydrop":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                if (plugin.getSupplyDropService() != null) plugin.getSupplyDropService().spawnNow();
                return true;

            case "dropcoords":
                // Reveal last announced drop coords privately
                if (!plugin.isGameMaster(p) && !p.isOp()) { p.sendMessage(ChatColor.RED + "Nur für GMs."); return true; }
                if (plugin.getSupplyDropService() != null) plugin.getSupplyDropService().showLastCoords(p);
                return true;

            case "specteam":
                plugin.cmdSpecteam(p, args); return true;

            case "autocam":
                if (plugin.getAutoCamService() != null) plugin.getAutoCamService().toggle(p);
                return true;
            case "fightradar":
                if (!plugin.isGameMaster(p) && !p.isOp()) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (plugin.getFightRadarService() != null) plugin.getFightRadarService().toggle(p);
                return true;
            case "lagreport":
                if (!p.isOp() && !plugin.isGameMaster(p)) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                plugin.cmdLagReport(p);
                return true;

            case "lootlama":
                if (!p.isOp() && !plugin.isGameMaster(p)) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (args != null && args.length > 0 && args[0].equalsIgnoreCase("spawn")) {
                    if (plugin.getLootLamaService() == null) { p.sendMessage(ChatColor.RED + "Service nicht aktiv."); return true; }
                    boolean ok = plugin.getLootLamaService().forceSpawn();
                    if (ok) p.sendMessage(ChatColor.GREEN + "Loot-Lama gespawnt."); else p.sendMessage(ChatColor.RED + "Spawn fehlgeschlagen.");
                } else {
                    p.sendMessage(ChatColor.YELLOW + "/lootlama spawn");
                }
                return true;

            case "varo":
                if (plugin.getInfoService() != null) plugin.getInfoService().cmdVaroInfo(p); else plugin.cmdVaroInfo(p); return true;

            case "poi":
                plugin.cmdPoi(p); return true;
            case "poitp":
                // /poitp <drop|fight|border> [index]
                if (args == null || args.length < 1) { p.sendMessage(ChatColor.YELLOW + "/poitp <drop|fight|border> [index]"); return true; }
                String kind = args[0].toLowerCase(java.util.Locale.ROOT);
                plugin.cmdPoiTp(p, kind, args); return true;

            case "logs":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                plugin.cmdLogs(p, args); return true;

            case "miningstats":
                if (!plugin.isGameMaster(p)) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                plugin.cmdMiningStats(p, args); return true;

            case "review":
                if (!plugin.isGameMaster(p)) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (args != null && args.length >= 2 && args[1].equalsIgnoreCase("gui")) {
                    org.bukkit.entity.Player t = (args.length >= 1 ? org.bukkit.Bukkit.getPlayerExact(args[0]) : null);
                    if (t == null) { p.sendMessage(ChatColor.RED + "Spieler offline."); return true; }
                    plugin.openReviewGui(p, t); return true;
                }
                plugin.cmdReview(p, args); return true;

            case "report":
                plugin.cmdReport(p, args); return true;

            case "announce":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                plugin.cmdAnnounce(p, args); return true;

            case "pregen":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                plugin.cmdPregen(p, args); return true;

            case "acset":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                plugin.cmdAcSet(p, args); return true;

            case "acprofile":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                plugin.cmdAcProfile(p, args); return true;

            case "debugfaketeams":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                plugin.cmdDebugFakeTeams(p, args); return true;
            case "debugcleardummys":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                plugin.cmdDebugClearDummys(p); return true;
            case "debugspecdummy":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                if (args == null || args.length < 1) { p.sendMessage(ChatColor.YELLOW + "/debugspecdummy <nameTeil>"); return true; }
                if (plugin.getDummyPlayerService() == null) { p.sendMessage(ChatColor.RED + "Service fehlt."); return true; }
                org.bukkit.entity.ArmorStand as = plugin.getDummyPlayerService().findDummyByNamePart(args[0]);
                if (as == null) { p.sendMessage(ChatColor.RED + "Kein Dummy gefunden."); return true; }
                plugin.getDummyPlayerService().spectate(as, p);
                return true;
            case "debugnpcteams":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                plugin.cmdDebugNpcTeams(p, args); return true;

            case "sc":
                if (!p.isOp() && !plugin.isGameMaster(p)) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                plugin.staffChatToggle(p); return true;

            case "stschutz":
                // Personal streamer-meta protection: mask F3 coords where possible and mask broadcasts until reveal
                plugin.toggleStreamerPrivacy(p);
                return true;

            case "mute":
                if (!p.isOp() && !plugin.isGameMaster(p)) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                plugin.cmdMute(p, args); return true;

            case "unmute":
                if (!p.isOp() && !plugin.isGameMaster(p)) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                plugin.cmdUnmute(p, args); return true;

            case "warn":
                if (!p.isOp() && !plugin.isGameMaster(p)) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                plugin.cmdWarn(p, args); return true;

            case "vanish":
                if (!plugin.isGameMaster(p) && !p.isOp()) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (plugin.getAdminTools() != null) plugin.getAdminTools().toggleVanish(p); return true;

            case "freeze":
                if (!plugin.isGameMaster(p)) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (args == null || args.length < 1) { p.sendMessage(ChatColor.YELLOW + "/freeze <Spieler>"); return true; }
                {
                    org.bukkit.entity.Player t = org.bukkit.Bukkit.getPlayerExact(args[0]);
                    if (plugin.getAdminTools() != null) plugin.getAdminTools().toggleFreeze(p, t);
                }
                return true;

            case "inspect":
                if (!plugin.isGameMaster(p)) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (args == null || args.length < 1) { p.sendMessage(ChatColor.YELLOW + "/inspect <Spieler>"); return true; }
                {
                    org.bukkit.entity.Player t = org.bukkit.Bukkit.getPlayerExact(args[0]);
                    if (plugin.getAdminTools() != null) plugin.getAdminTools().inspect(p, t);
                }
                return true;

            case "rq": // report queue GUI
                if (!plugin.isGameMaster(p) && !p.isOp()) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (plugin.getReportsFeature() != null) plugin.getReportsFeature().openQueue(p); return true;

            case "schedule":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                p.sendMessage(ChatColor.YELLOW + "Scheduler lädt scheduler.yml automatisch. Zeiten im File anpassen.");
                return true;

            case "tpl": // silent tp to player
                if (!plugin.isGameMaster(p) && !p.isOp()) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (args == null || args.length < 1) { p.sendMessage(ChatColor.YELLOW + "/tpl <Spieler>"); return true; }
                {
                    org.bukkit.entity.Player t = org.bukkit.Bukkit.getPlayerExact(args[0]);
                    if (t == null) { p.sendMessage(ChatColor.RED + "Spieler offline."); return true; }
                    if (plugin.getAdminTools() != null) plugin.getAdminTools().tpSilent(p, t.getLocation());
                }
                return true;

            case "tpsafe": // tp near coordinates (safe landing)
                if (!plugin.isGameMaster(p) && !p.isOp()) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (args == null || args.length < 2) { p.sendMessage(ChatColor.YELLOW + "/tpsafe <x> <z> [radius]"); return true; }
                try {
                    int x = Integer.parseInt(args[0]);
                    int z = Integer.parseInt(args[1]);
                    int r = (args.length>=3 ? Math.max(2, Integer.parseInt(args[2])) : 6);
                    org.bukkit.World w = p.getWorld();
                    org.bukkit.Location best = null; int bestY = -1;
                    for (int dx=-r; dx<=r; dx++) for (int dz=-r; dz<=r; dz++) {
                        int tx = x+dx, tz = z+dz; int ty = w.getHighestBlockYAt(tx, tz)+1;
                        org.bukkit.Material under = w.getBlockAt(tx, ty-1, tz).getType();
                        if (under.isSolid() && ty>bestY) { bestY=ty; best=new org.bukkit.Location(w, tx+0.5, ty, tz+0.5); }
                    }
                    if (best == null) { p.sendMessage(ChatColor.RED + "Kein sicherer Ort gefunden."); return true; }
                    if (plugin.getAdminTools()!=null) plugin.getAdminTools().tpSilent(p, best); else p.teleport(best);
                    p.sendMessage(ChatColor.GREEN + "TP sicher nach " + ChatColor.WHITE + best.getBlockX()+","+best.getBlockY()+","+best.getBlockZ());
                } catch (Exception ex) { p.sendMessage(ChatColor.RED + "Ungültige Koords."); }
                return true;

            case "areafreeze":
                if (!plugin.isGameMaster(p)) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (args == null || args.length < 3) { p.sendMessage(ChatColor.YELLOW + "/areafreeze <x> <z> <radius>"); return true; }
                try {
                    int x = Integer.parseInt(args[0]);
                    int z = Integer.parseInt(args[1]);
                    int r = Math.max(3, Integer.parseInt(args[2]));
                    int count = 0;
                    for (org.bukkit.entity.Player pl : org.bukkit.Bukkit.getOnlinePlayers()) {
                        if (plugin.isGameMaster(pl)) continue;
                        if (!pl.getWorld().equals(p.getWorld())) continue;
                        if (pl.getLocation().distanceSquared(new org.bukkit.Location(p.getWorld(), x+0.5, pl.getLocation().getY(), z+0.5)) <= r*r) {
                            if (plugin.getAdminTools()!=null) plugin.getAdminTools().toggleFreeze(p, pl);
                            count++;
                        }
                    }
                    p.sendMessage(ChatColor.YELLOW + "Gefreezed in Zone: " + ChatColor.WHITE + count);
                } catch (Exception ex) { p.sendMessage(ChatColor.RED + "Parameter ungültig."); }
                return true;

            case "wlcode":
                if (!plugin.isGameMaster(p) && !p.isOp()) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (plugin.getWhitelistService() != null) plugin.getWhitelistService().cmdWlCode(p, args); else p.sendMessage(ChatColor.RED + "WL-Dienst fehlt.");
                return true;
            case "wllist":
                if (!plugin.isGameMaster(p) && !p.isOp()) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (plugin.getWhitelistService() != null) plugin.getWhitelistService().cmdWlList(p); else p.sendMessage(ChatColor.RED + "WL-Dienst fehlt.");
                return true;
            case "wldel":
                if (!plugin.isGameMaster(p) && !p.isOp()) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }
                if (plugin.getWhitelistService() != null) plugin.getWhitelistService().cmdWlDel(p, args); else p.sendMessage(ChatColor.RED + "WL-Dienst fehlt.");
                return true;
            case "summary":
                if (!p.isOp()) { p.sendMessage(ChatColor.RED + "Nur Admin!"); return true; }
                if (plugin.getStatsService() != null) plugin.getStatsService().cmdSummary(p, args); else plugin.cmdSummary(p, args); return true;
        }
        return false;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> res = new ArrayList<>();
        // Only player specific suggestions
        if (!(sender instanceof Player)) return res;
        switch (c) {
            case "team": {
                if (args.length == 1) {
                    String pfx = args[0].toLowerCase(Locale.ROOT);
                    String[] subs = {"create","invite","accept","decline","delete","delapprove","deldeny","help"};
                    for (String s : subs) if (s.startsWith(pfx)) res.add(s);
                } else if (args.length == 2) {
                    String sub = args[0].toLowerCase(Locale.ROOT);
                    if (sub.equals("invite") || sub.equals("accept") || sub.equals("delapprove") || sub.equals("deldeny")) {
                        String pfx = args[1].toLowerCase(Locale.ROOT);
                        for (Player pl : ((Player)sender).getServer().getOnlinePlayers()) {
                            if (pl.getName().toLowerCase(Locale.ROOT).startsWith(pfx)) res.add(pl.getName());
                        }
                    }
                }
                break; }
            case "report": {
                if (args.length == 1) {
                    String pfx = args[0].toLowerCase(Locale.ROOT);
                    for (Player pl : ((Player)sender).getServer().getOnlinePlayers()) {
                        if (pl.getName().equalsIgnoreCase(sender.getName())) continue; // skip self
                        if (pl.getName().toLowerCase(Locale.ROOT).startsWith(pfx)) res.add(pl.getName());
                    }
                }
                break; }
            case "tracker":
            case "tpl":
            case "freeze":
            case "inspect":
            case "miningstats":
            case "review": {
                if (args.length == 1) {
                    String pfx = args[0].toLowerCase(Locale.ROOT);
                    for (Player pl : ((Player)sender).getServer().getOnlinePlayers()) {
                        if (pl.getName().toLowerCase(Locale.ROOT).startsWith(pfx)) res.add(pl.getName());
                    }
                }
                break; }
        }
        // Limit result size for cleanliness
        return res.stream().limit(15).collect(Collectors.toList());
    }
}
