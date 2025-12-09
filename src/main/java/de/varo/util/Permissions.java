package de.varo.util;

import org.bukkit.Bukkit;

import java.util.Set;
import java.util.UUID;

public final class Permissions {
    private Permissions() {}

    public static boolean isGm(UUID id, Set<UUID> gameMasters) {
        return gameMasters != null && id != null && gameMasters.contains(id);
    }

    public static boolean isStreamer(UUID id, Set<UUID> streamers) {
        return streamers != null && id != null && streamers.contains(id);
    }

    public static boolean isOp(UUID id) {
        return id != null && Bukkit.getOfflinePlayer(id).isOp();
    }

    /** GM or Streamer or OP */
    public static boolean isPrivileged(UUID id, Set<UUID> gameMasters, Set<UUID> streamers) {
        return isGm(id, gameMasters) || isStreamer(id, streamers) || isOp(id);
    }

    /** GM or OP */
    public static boolean isGmOrOp(UUID id, Set<UUID> gameMasters) {
        return isGm(id, gameMasters) || isOp(id);
    }

    /** Streamer or OP */
    public static boolean isStreamerOrOp(UUID id, Set<UUID> streamers) {
        return isStreamer(id, streamers) || isOp(id);
    }
}
