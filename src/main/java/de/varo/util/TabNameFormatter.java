package de.varo.util;

import org.bukkit.ChatColor;

public final class TabNameFormatter {
    private TabNameFormatter() {}

    /**
     * Returns the formatted tab prefix for a team name, truncated with an ellipsis if too long.
     */
    public static String teamPrefix(String teamName, ChatColor teamColor, int maxLen) {
        String t = truncate(teamName, Math.max(1, maxLen));
        ChatColor c = (teamColor == null ? ChatColor.WHITE : teamColor);
        return c + "[" + t + "] ";
    }

    /**
     * Truncates the given string to maxLen, appending a single-character ellipsis (…).
     */
    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (maxLen < 1 || s.length() <= maxLen) return s;
        return s.substring(0, Math.max(0, maxLen - 1)) + "…";
    }
}
