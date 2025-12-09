package de.varo.features.chat;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

public class ChatFeature implements Listener {
    private final Map<java.util.UUID, String> playerTeam;
    private final Map<String, ChatColor> teamColors;
    private final Set<java.util.UUID> gameMasters;
    private final Set<java.util.UUID> streamers;

    public ChatFeature(Map<java.util.UUID, String> playerTeam,
                       Map<String, ChatColor> teamColors,
                       Set<java.util.UUID> gameMasters,
                       Set<java.util.UUID> streamers) {
        this.playerTeam = playerTeam;
        this.teamColors = teamColors;
        this.gameMasters = gameMasters;
        this.streamers = streamers;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String tn = playerTeam.get(p.getUniqueId());
        String teamLabel;
        if (tn != null) {
            ChatColor col = teamColors.getOrDefault(tn, ChatColor.WHITE);
            teamLabel = col + "[" + tn + "]";
        } else {
            teamLabel = ChatColor.DARK_GRAY + "[kein Team]";
        }
        String rolePrefix = "";
        if (gameMasters.contains(p.getUniqueId())) rolePrefix = ChatColor.RED + "GAMEMASTER: ";
        else if (streamers.contains(p.getUniqueId())) rolePrefix = ChatColor.LIGHT_PURPLE + "ST: ";

        String displayName = ChatColor.GRAY + p.getName();
        e.setFormat(teamLabel + ChatColor.GRAY + " " + rolePrefix + displayName + ChatColor.GRAY + " » " + ChatColor.RESET + "%2$s");
    }
}
