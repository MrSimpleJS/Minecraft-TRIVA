package de.varo.services;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

// Chat components for clickable actions
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class TeamService {
    private final Map<String, List<UUID>> teams;
    private final Map<UUID, String> playerTeam;
    private final Map<String, Boolean> teamReady;
    private final Map<UUID, String> pendingInvite;
    @SuppressWarnings("deprecation")
    private final Map<String, ChatColor> teamColors;
    @SuppressWarnings("deprecation")
    private final ChatColor[] availableColors;
    private int nextColorIndex = 0;

    private final Supplier<Boolean> gameRunningSupplier;
    private final Consumer<Player> updateTabName;
    private final Runnable saveState;
    private final Consumer<String> logConsumer;
    private final Predicate<UUID> isAdmin;

    // Pending team deletion requests (team -> requester)
    private final Map<String, UUID> pendingDeleteRequests = new HashMap<>();

    @SuppressWarnings("deprecation")
    public TeamService(Map<String, List<UUID>> teams,
                       Map<UUID, String> playerTeam,
                       Map<String, Boolean> teamReady,
                       Map<UUID, String> pendingInvite,
                       Map<String, ChatColor> teamColors,
                       ChatColor[] availableColors,
                       Supplier<Boolean> gameRunningSupplier,
                       Consumer<Player> updateTabName,
                       Runnable saveState,
                       Consumer<String> logConsumer,
                       Predicate<UUID> isAdmin) {
        this.teams = teams;
        this.playerTeam = playerTeam;
        this.teamReady = teamReady;
        this.pendingInvite = pendingInvite;
        this.teamColors = teamColors;
        this.availableColors = availableColors;
        this.gameRunningSupplier = gameRunningSupplier;
        this.updateTabName = updateTabName;
        this.saveState = saveState;
        this.logConsumer = logConsumer;
        this.isAdmin = isAdmin;
    }

    @SuppressWarnings("deprecation")
    public void handleTeamCommand(Player p, String[] args) {
        if (args.length == 0) { printTeamHelp(p); return; }
        boolean gameRunning = gameRunningSupplier.get();
        if (gameRunning) {
            // Allow admin approvals/denials even after start; block player-side changes
            String op = args[0].toLowerCase(java.util.Locale.ROOT);
            if (!(op.equals("delapprove") || op.equals("deldeny"))) {
                p.sendMessage(ChatColor.RED + "Team-Änderungen sind nach Start gesperrt.");
                return;
            }
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
            case "hilfe":
                printTeamHelp(p);
                break;
            case "create": {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "Nutze: /team create <Name>"); return; }
                String name = args[1];
                if (teams.containsKey(name)) { p.sendMessage(ChatColor.RED + "Team existiert schon."); return; }
                if (playerTeam.containsKey(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Du bist bereits in einem Team."); return; }
                teams.put(name, new ArrayList<>(Collections.singletonList(p.getUniqueId())));
                playerTeam.put(p.getUniqueId(), name);
                teamReady.put(name, false);
                ChatColor color = availableColors[nextColorIndex % availableColors.length]; nextColorIndex++;
                teamColors.put(name, color);
                p.sendMessage(ChatColor.GREEN + "Team " + color + name + ChatColor.GREEN + " erstellt.");
                if (logConsumer != null) logConsumer.accept(p.getName() + " hat Team " + name + " erstellt.");
                updateTabName.accept(p);
                break;
            }
            case "invite": {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "Nutze: /team invite <Spieler>"); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { p.sendMessage(ChatColor.RED + "Spieler nicht online."); return; }
                // Prevent self-invite
                if (target.getUniqueId().equals(p.getUniqueId())) {
                    p.sendMessage(ChatColor.RED + "Du kannst dich nicht selbst einladen.");
                    return;
                }
                String tn = playerTeam.get(p.getUniqueId());
                if (tn == null) { p.sendMessage(ChatColor.RED + "Du bist in keinem Team."); return; }
                if (teams.get(tn).size() >= 2) { p.sendMessage(ChatColor.RED + "Team bereits voll."); return; }
                pendingInvite.put(target.getUniqueId(), tn);
                sendClickableInvite(target, tn, p.getName());
                p.sendMessage(ChatColor.GREEN + "Einladung gesendet.");
                if (logConsumer != null) logConsumer.accept(p.getName() + " hat " + target.getName() + " in Team " + tn + " eingeladen.");
                break;
            }
            case "accept": {
                if (args.length < 2) { p.sendMessage(ChatColor.RED + "Nutze: /team accept <Teamname|Spielername>"); return; }
                String input = args[1];
                String resolvedTeam = input;
                // If not a known team, try resolving by inviter player name (online or offline)
                if (!teams.containsKey(resolvedTeam)) {
                    java.util.UUID inviterId = null;
                    Player on = Bukkit.getPlayerExact(input);
                    if (on != null) inviterId = on.getUniqueId();
                    else {
                        org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(input);
                        if (off != null && off.getName() != null && off.getName().equalsIgnoreCase(input)) inviterId = off.getUniqueId();
                    }
                    if (inviterId != null) {
                        String tnByPlayer = playerTeam.get(inviterId);
                        if (tnByPlayer != null) resolvedTeam = tnByPlayer;
                    }
                }
                // If still unknown, fall back to pending invite team (if exactly one exists)
                if (!teams.containsKey(resolvedTeam)) {
                    String pend = pendingInvite.get(p.getUniqueId());
                    if (pend != null && teams.containsKey(pend)) {
                        resolvedTeam = pend;
                    } else {
                        p.sendMessage(ChatColor.RED + "Team existiert nicht.");
                        return;
                    }
                }
                String pending = pendingInvite.get(p.getUniqueId());
                if (pending == null || !pending.equals(resolvedTeam)) { p.sendMessage(ChatColor.RED + "Keine Einladung von diesem Team."); return; }
                List<UUID> list = teams.get(resolvedTeam);
                if (list.size() >= 2) { p.sendMessage(ChatColor.RED + "Team bereits voll."); return; }
                list.add(p.getUniqueId());
                playerTeam.put(p.getUniqueId(), resolvedTeam);
                teamReady.put(resolvedTeam, false);
                pendingInvite.remove(p.getUniqueId());
                Bukkit.broadcastMessage(ChatColor.GREEN + p.getName() + " ist Team " + resolvedTeam + " beigetreten.");
                if (logConsumer != null) logConsumer.accept(p.getName() + " ist Team " + resolvedTeam + " beigetreten (Einladung akzeptiert).");
                for (UUID u : list) {
                    Player pl = Bukkit.getPlayer(u);
                    if (pl != null && pl.isOnline()) updateTabName.accept(pl);
                }
                // Teleport the new mate next to the inviter if online
                UUID inviterId = null;
                for (UUID uid : list) if (!uid.equals(p.getUniqueId())) { inviterId = uid; break; }
                if (inviterId != null) {
                    Player inviter = Bukkit.getPlayer(inviterId);
                    if (inviter != null && inviter.isOnline()) {
                        org.bukkit.Location base = inviter.getLocation();
                        org.bukkit.util.Vector dir = base.getDirection();
                        if (dir == null) dir = new org.bukkit.util.Vector(1,0,0);
                        org.bukkit.Location spot = base.clone().add(dir.clone().setY(0).normalize().multiply(1.5));
                        p.teleport(spot);
                        p.sendMessage(ChatColor.YELLOW + "Du stehst jetzt neben deinem Teampartner.");
                        inviter.sendMessage(ChatColor.GREEN + p.getName() + " ist beigetreten und wurde zu dir teleportiert.");
                    }
                }
                break;
            }
            case "decline": {
                String pend = pendingInvite.get(p.getUniqueId());
                if (args.length >= 2) {
                    String provided = args[1];
                    if (pend != null && !pend.equalsIgnoreCase(provided)) {
                        p.sendMessage(ChatColor.RED + "Keine Einladung von diesem Team.");
                        break;
                    }
                }
                if (pend == null) { p.sendMessage(ChatColor.RED + "Du hast keine ausstehende Einladung."); break; }
                pendingInvite.remove(p.getUniqueId());
                p.sendMessage(ChatColor.YELLOW + "Einladung abgelehnt.");
                // Notify team members (if online)
                java.util.List<java.util.UUID> mem = teams.getOrDefault(pend, java.util.Collections.emptyList());
                for (java.util.UUID u : mem) {
                    Player pl = Bukkit.getPlayer(u);
                    if (pl != null && pl.isOnline()) pl.sendMessage(ChatColor.GRAY + p.getName() + " hat die Einladung abgelehnt.");
                }
                if (logConsumer != null) logConsumer.accept(p.getName() + " hat Team-Einladung zu " + pend + " abgelehnt.");
                break;
            }
            case "delete": {
                String tn = playerTeam.get(p.getUniqueId());
                if (tn == null) { p.sendMessage(ChatColor.RED + "Du bist in keinem Team."); return; }
                if (!teams.containsKey(tn)) { p.sendMessage(ChatColor.RED + "Team existiert nicht mehr."); return; }
                if (pendingDeleteRequests.containsKey(tn)) { p.sendMessage(ChatColor.YELLOW + "Es gibt bereits eine Löschanfrage für dein Team."); return; }
                pendingDeleteRequests.put(tn, p.getUniqueId());
                p.sendMessage(ChatColor.GOLD + "Löschanfrage gesendet. Ein Admin muss diese bestätigen.");
                if (logConsumer != null) logConsumer.accept(p.getName() + " hat Löschung für Team " + tn + " angefragt.");
                notifyAdminsDeleteRequest(tn, p.getName());
                break;
            }
            case "delapprove": {
                if (!isAdmin.test(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Keine Berechtigung."); return; }
                if (args.length < 2) { p.sendMessage(ChatColor.YELLOW + "Nutze: /team delapprove <Team>"); return; }
                String team = args[1];
                if (!pendingDeleteRequests.containsKey(team)) { p.sendMessage(ChatColor.RED + "Keine ausstehende Löschanfrage für dieses Team."); return; }
                // Perform deletion
                java.util.List<java.util.UUID> members = new java.util.ArrayList<>(teams.getOrDefault(team, java.util.Collections.emptyList()));
                teams.remove(team);
                teamReady.remove(team);
                teamColors.remove(team);
                // Remove pending invites to this team
                java.util.Iterator<java.util.Map.Entry<java.util.UUID,String>> it = pendingInvite.entrySet().iterator();
                while (it.hasNext()) {
                    java.util.Map.Entry<java.util.UUID,String> e = it.next();
                    if (team.equals(e.getValue())) it.remove();
                }
                // Clear playerTeam mapping and update names
                for (java.util.UUID uid : members) {
                    playerTeam.remove(uid);
                    Player pl = Bukkit.getPlayer(uid);
                    if (pl != null && pl.isOnline()) {
                        pl.sendMessage(ChatColor.RED + "Dein Team wurde von einem Admin gelöscht.");
                        updateTabName.accept(pl);
                    }
                }
                pendingDeleteRequests.remove(team);
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Team " + team + " wurde gelöscht.");
                if (logConsumer != null) logConsumer.accept("Team " + team + " gelöscht (Admin: " + p.getName() + ")");
                saveState.run();
                break;
            }
            case "deldeny": {
                if (!isAdmin.test(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Keine Berechtigung."); return; }
                if (args.length < 2) { p.sendMessage(ChatColor.YELLOW + "Nutze: /team deldeny <Team>"); return; }
                String team = args[1];
                if (!pendingDeleteRequests.containsKey(team)) { p.sendMessage(ChatColor.RED + "Keine ausstehende Löschanfrage für dieses Team."); return; }
                java.util.UUID req = pendingDeleteRequests.remove(team);
                Player requester = (req != null ? Bukkit.getPlayer(req) : null);
                if (requester != null && requester.isOnline()) requester.sendMessage(ChatColor.RED + "Deine Team-Löschanfrage wurde abgelehnt.");
                p.sendMessage(ChatColor.YELLOW + "Abgelehnt: " + team);
                if (logConsumer != null) logConsumer.accept("Löschanfrage abgelehnt für Team " + team + " (Admin: " + p.getName() + ")");
                break;
            }
            default:
                printTeamHelp(p);
        }
        saveState.run();
    }

    @SuppressWarnings("deprecation")
    public void handleTeamFertig(Player p) {
        String tn = playerTeam.get(p.getUniqueId());
        if (tn == null) { p.sendMessage(ChatColor.RED + "Du bist in keinem Team."); return; }
        if (Boolean.TRUE.equals(teamReady.get(tn))) {
            p.sendMessage(ChatColor.RED + "Dein Team ist bereits als fertig markiert!");
            return;
        }
        List<UUID> list = teams.getOrDefault(tn, new ArrayList<>());
        if (list.size() < 2) { p.sendMessage(ChatColor.RED + "Team noch nicht voll (2/2)."); return; }
    teamReady.put(tn, true);
        Bukkit.broadcastMessage(ChatColor.GOLD + "Team " + tn + " (2/2) ist fertig!");
    if (logConsumer != null) logConsumer.accept(p.getName() + " hat Team " + tn + " als fertig markiert.");
        saveState.run();
    }

    // ===== Helpers =====
    private void sendClickableInvite(Player target, String team, String inviterName) {
    // Prefix
    TextComponent prefix = new TextComponent("Einladung zum Team ");

    // Team name in its team color (fallback AQUA)
    org.bukkit.ChatColor teamBukkit = teamColors.get(team);
    net.md_5.bungee.api.ChatColor teamColor = net.md_5.bungee.api.ChatColor.AQUA;
    if (teamBukkit != null) {
        try { teamColor = net.md_5.bungee.api.ChatColor.valueOf(teamBukkit.name()); } catch (IllegalArgumentException ignored) {}
    }
    TextComponent teamName = new TextComponent(team);
    teamName.setColor(teamColor);

    // Mid text
    TextComponent mid = new TextComponent(" von ");

    // Inviter name in GOLD for readability
    TextComponent inviter = new TextComponent(inviterName);
    inviter.setColor(net.md_5.bungee.api.ChatColor.GOLD);

    // Actions
    TextComponent accept = new TextComponent("[Annehmen]");
    accept.setBold(true);
    accept.setColor(net.md_5.bungee.api.ChatColor.GREEN);
    accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team accept " + team));
    accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
        new ComponentBuilder("Klicke, um dem Team beizutreten").color(net.md_5.bungee.api.ChatColor.GREEN).create()));

    // Spacing
    TextComponent sep = new TextComponent(" ");
    TextComponent space = new TextComponent(" ");

    TextComponent decline = new TextComponent("[Ablehnen]");
    decline.setBold(true);
    decline.setColor(net.md_5.bungee.api.ChatColor.RED);
    decline.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team decline " + team));
    decline.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
        new ComponentBuilder("Klicke, um die Einladung abzulehnen").color(net.md_5.bungee.api.ChatColor.RED).create()));

    target.spigot().sendMessage(prefix, teamName, mid, inviter, sep, accept, space, decline);
    target.sendMessage(org.bukkit.ChatColor.GRAY + "Oder nutze: " + org.bukkit.ChatColor.YELLOW + "/team accept " + team + org.bukkit.ChatColor.GRAY + " / " + org.bukkit.ChatColor.YELLOW + "/team decline " + team);
    }

    private void notifyAdminsDeleteRequest(String team, String requesterName) {
    for (Player pl : Bukkit.getOnlinePlayers()) {
        if (!isAdmin.test(pl.getUniqueId())) continue;
        TextComponent base = new TextComponent("Löschanfrage für Team " + team + " von " + requesterName + " ");
        TextComponent approve = new TextComponent("[Zustimmen]");
        approve.setBold(true);
        approve.setColor(net.md_5.bungee.api.ChatColor.GOLD);
        approve.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team delapprove " + team));
        approve.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("Team löschen").color(net.md_5.bungee.api.ChatColor.YELLOW).create()));

        TextComponent space = new TextComponent(" ");

        TextComponent deny = new TextComponent("[Ablehnen]");
        deny.setBold(true);
        deny.setColor(net.md_5.bungee.api.ChatColor.RED);
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team deldeny " + team));
        deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("Löschanfrage ablehnen").color(net.md_5.bungee.api.ChatColor.RED).create()));

        pl.spigot().sendMessage(base, approve, space, deny);
    }
    }

    private void printTeamHelp(Player p) {
        p.sendMessage(ChatColor.GOLD + "—— Team Hilfe ——");
        p.sendMessage(ChatColor.YELLOW + "/team create <Name>" + ChatColor.GRAY + " — Team erstellen");
        p.sendMessage(ChatColor.YELLOW + "/team invite <Spieler>" + ChatColor.GRAY + " — Spieler einladen");
        p.sendMessage(ChatColor.YELLOW + "/team accept <Team|Spieler>" + ChatColor.GRAY + " — Einladung annehmen");
        p.sendMessage(ChatColor.YELLOW + "/team decline [Team]" + ChatColor.GRAY + " — Einladung ablehnen");
        p.sendMessage(ChatColor.YELLOW + "/team delete" + ChatColor.GRAY + " — Löschung beantragen (Admin muss zustimmen)");
        p.sendMessage(ChatColor.YELLOW + "/team delapprove <Team>" + ChatColor.GRAY + " — (Admin) Team löschen");
        p.sendMessage(ChatColor.YELLOW + "/team deldeny <Team>" + ChatColor.GRAY + " — (Admin) Löschanfrage ablehnen");
        p.sendMessage(ChatColor.DARK_GRAY + "Tipp: Nutze " + ChatColor.WHITE + "/team hilfe" + ChatColor.DARK_GRAY + " jederzeit für diese Übersicht.");
    }
}
