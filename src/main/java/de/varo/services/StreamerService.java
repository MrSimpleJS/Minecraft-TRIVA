package de.varo.services;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class StreamerService {
    private final Set<UUID> streamers;
    private final Consumer<Player> updateTabName;

    public StreamerService(Set<UUID> streamers, Consumer<Player> updateTabName) {
        this.streamers = streamers;
        this.updateTabName = updateTabName;
    }

    @SuppressWarnings("deprecation")
    public void handleStreamerCommand(Player sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /streamer <add|remove|list> <Spieler>");
            return;
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        if ("list".equals(sub)) {
            sender.sendMessage(ChatColor.AQUA + "Streamer: " + streamers.size());
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /streamer <add|remove|list> <Spieler>");
            return;
        }
        Player target = sender.getServer().getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "Spieler nicht online."); return; }
        if ("add".equals(sub)) {
            streamers.add(target.getUniqueId());
            updateTabName.accept(target);
            target.sendMessage(ChatColor.LIGHT_PURPLE + "Du bist jetzt als Streamer markiert.");
            // Streamer command overview
                target.sendMessage(ChatColor.GOLD + "Streamer-Tools:");
                try {
                    net.md_5.bungee.api.chat.TextComponent c1a = new net.md_5.bungee.api.chat.TextComponent(ChatColor.YELLOW + "/stschutz");
                    c1a.setUnderlined(true);
                    c1a.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/stschutz"));
                    c1a.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                            new net.md_5.bungee.api.chat.ComponentBuilder("Klicken zum Ausführen").color(net.md_5.bungee.api.ChatColor.YELLOW).create()));
                    net.md_5.bungee.api.chat.TextComponent c1b = new net.md_5.bungee.api.chat.TextComponent(ChatColor.GRAY + " — Streamer-Schutz an/aus (Koordinaten/Meta-Schutz: F3 & Achievements)");
                    target.spigot().sendMessage(c1a, c1b);

                    net.md_5.bungee.api.chat.TextComponent c2a = new net.md_5.bungee.api.chat.TextComponent(ChatColor.YELLOW + "/dropcoords");
                    c2a.setUnderlined(true);
                    c2a.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/dropcoords"));
                    c2a.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                            new net.md_5.bungee.api.chat.ComponentBuilder("Klicken zum Ausführen").color(net.md_5.bungee.api.ChatColor.YELLOW).create()));
                    net.md_5.bungee.api.chat.TextComponent c2b = new net.md_5.bungee.api.chat.TextComponent(ChatColor.GRAY + " — Letzte Supply-Drop-Position (nach Klick im Hinweis)");
                    target.spigot().sendMessage(c2a, c2b);
                } catch (Throwable t) {
                    // Fallback to plain text
                    target.sendMessage(ChatColor.YELLOW + "/stschutz" + ChatColor.GRAY + " — Streamer-Schutz an/aus (Koordinaten/Meta-Schutz: F3 & Achievements)");
                    target.sendMessage(ChatColor.YELLOW + "/dropcoords" + ChatColor.GRAY + " — Letzte Supply-Drop-Position (nach Klick im Hinweis)");
                }
                target.sendMessage(ChatColor.DARK_GRAY + "Hinweis: In Supply-Drop-Hinweisen kannst du klicken, um Koordinaten temporär freizuschalten.");
            sender.sendMessage(ChatColor.GREEN + "Streamer hinzugefügt.");
        } else if ("remove".equals(sub)) {
            streamers.remove(target.getUniqueId());
            updateTabName.accept(target);
            target.sendMessage(ChatColor.LIGHT_PURPLE + "Streamer-Status entfernt.");
            sender.sendMessage(ChatColor.GREEN + "Streamer entfernt.");
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /streamer <add|remove|list> <Spieler>");
        }
    }
}
