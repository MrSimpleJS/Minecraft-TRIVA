package de.varo.util;

import org.bukkit.ChatColor;

/**
 * Small helpers to keep message formatting consistent.
 */
public final class Messages {
    private Messages() {}

    /**
     * Formats a gold-highlighted title like: "—— Text ——".
     */
    public static String title(String text) {
        return ChatColor.GOLD + "—— " + text + " ——";
    }
}
