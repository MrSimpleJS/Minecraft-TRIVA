package de.varo.services;

import de.varo.VaroPlugin;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.*;
import java.util.function.Predicate;

public class WhitelistService implements Listener {
    public static class WlPending { public final String code; public final long createdAt; public WlPending(String c,long t){code=c;createdAt=t;} }

    private final VaroPlugin plugin;
    private final Predicate<UUID> isPrivileged; // OP or GM bypass
    private final Runnable saveFn; // plugin::saveState

    private final Set<UUID> whitelist = new HashSet<>();
    private final Map<UUID, WlPending> wlPending = new HashMap<>();
    private final Map<String, UUID> wlCodeIndex = new HashMap<>();
    private final Map<UUID, String> wlApproved = new HashMap<>();

    private final long wlCodeTtlMs;

    public WhitelistService(VaroPlugin plugin, Predicate<UUID> isPrivileged, Runnable saveFn, long codeTtlMs) {
        this.plugin = plugin;
        this.isPrivileged = isPrivileged;
        this.saveFn = saveFn;
        this.wlCodeTtlMs = codeTtlMs;
    }

    // ==== Events ====
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (isPrivileged.test(id)) return; // bypass
        if (whitelist.contains(id)) return;
        // Not whitelisted → ensure code exists, then deny
        cleanupExpiredWlCodes(false);
        String code = getOrCreateWlCode(id);
        String msg = ChatColor.RED + "Du bist nicht freigeschaltet."
            + ChatColor.GRAY + "\nMelde dich im Discord-Chat! dein Code: " + ChatColor.GOLD + code
            + ChatColor.GRAY + "\n(Der Code ist " + (wlCodeTtlMs/60000) + " Minuten gültig.)";
        e.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, msg);
    }

    // ==== Commands ====
    @SuppressWarnings("deprecation")
    public void cmdWlCode(CommandSender admin, String[] args) {
        if (args == null || args.length < 1) { admin.sendMessage(ChatColor.YELLOW + "/wlcode <CODE>"); return; }
        String code = args[0].toUpperCase(Locale.ROOT).trim();
        UUID id = wlCodeIndex.get(code);
        if (id == null) { admin.sendMessage(ChatColor.RED + "Ungültiger Code."); return; }
        whitelist.add(id);
        WlPending wp = wlPending.remove(id);
        if (wp != null) wlCodeIndex.remove(wp.code);
        wlApproved.put(id, code);
        saveFn.run();
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String name = (op != null && op.getName()!=null) ? op.getName() : id.toString();
        admin.sendMessage(ChatColor.GREEN + "Freigeschaltet: " + ChatColor.WHITE + name);
    }

    @SuppressWarnings("deprecation")
    public void cmdWlList(Player admin) {
        if (wlPending.isEmpty()) { admin.sendMessage(ChatColor.GRAY + "Keine offenen Codes."); return; }
        admin.sendMessage(ChatColor.GOLD + "—— Offene Whitelist-Codes ——");
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, WlPending> en : wlPending.entrySet()) {
            UUID id = en.getKey(); WlPending wp = en.getValue();
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = (op != null && op.getName()!=null) ? op.getName() : id.toString();
            long age = now - wp.createdAt;
            long left = Math.max(0L, wlCodeTtlMs - age);
            admin.sendMessage(ChatColor.YELLOW + wp.code + ChatColor.GRAY + " — " + ChatColor.WHITE + name
                    + ChatColor.GRAY + "  (verbleibend: " + ChatColor.WHITE + formatTime(left) + ChatColor.GRAY + ")");
        }
    }

    @SuppressWarnings("deprecation")
    public void cmdWlDel(Player admin, String[] args) {
        if (args == null || args.length < 1) { admin.sendMessage(ChatColor.YELLOW + "/wldel <CODE|Spieler>"); return; }
        String arg = args[0];
        UUID id = null;
        String codeUpper = arg.toUpperCase(Locale.ROOT);
        // try by code
        id = wlCodeIndex.get(codeUpper);
        if (id == null) {
            // try by player name
            OfflinePlayer op = Bukkit.getOfflinePlayer(arg);
            if (op != null) id = op.getUniqueId();
        }
        if (id == null) { admin.sendMessage(ChatColor.RED + "Nicht gefunden."); return; }
        WlPending removed = wlPending.remove(id);
        if (removed != null) wlCodeIndex.remove(removed.code);
        saveFn.run();
        admin.sendMessage(ChatColor.GREEN + "Entfernt.");
    }

    // ==== State I/O ====
    public void loadFromState(YamlConfiguration state) {
        whitelist.clear(); wlPending.clear(); wlCodeIndex.clear(); wlApproved.clear();
        try {
            List<String> wlList = state.getStringList("whitelist");
            if (wlList != null) for (String s : wlList) try { whitelist.add(UUID.fromString(s)); } catch (Exception ignored) {}
            // New structure: wl.pending.<uuid>.code + createdAt
            org.bukkit.configuration.ConfigurationSection pend = state.getConfigurationSection("wl.pending");
            if (pend != null) {
                for (String key : pend.getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(key);
                        String code = pend.getString(key + ".code");
                        long created = pend.getLong(key + ".createdAt", System.currentTimeMillis());
                        if (code != null && !code.isEmpty()) {
                            WlPending wp = new WlPending(code.toUpperCase(Locale.ROOT), created);
                            wlPending.put(id, wp);
                        }
                    } catch (Exception ignored) {}
                }
            }
            // Backward-compat: migrate old wlCodes (code->uuid)
            org.bukkit.configuration.ConfigurationSection old = state.getConfigurationSection("wlCodes");
            if (old != null) {
                for (String code : old.getKeys(false)) {
                    String u = old.getString(code);
                    try {
                        UUID id = UUID.fromString(u);
                        String c = code.toUpperCase(Locale.ROOT);
                        wlPending.putIfAbsent(id, new WlPending(c, System.currentTimeMillis()));
                    } catch (Exception ignored) {}
                }
            }
            // Load approved codes history: wl.approved.<uuid>.code
            org.bukkit.configuration.ConfigurationSection appr = state.getConfigurationSection("wl.approved");
            if (appr != null) {
                for (String key : appr.getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(key);
                        String code = appr.getString(key + ".code");
                        if (code != null && !code.isEmpty()) wlApproved.put(id, code.toUpperCase(Locale.ROOT));
                    } catch (Exception ignored) {}
                }
            }
            rebuildWlCodeIndex();
            cleanupExpiredWlCodes(false);
        } catch (Exception ex) {
            plugin.getLogger().warning("Whitelist state laden fehlgeschlagen: " + ex.getMessage());
        }
    }

    public void saveToState(YamlConfiguration state) {
        state.set("whitelist", new ArrayList<>(whitelist.stream().map(UUID::toString).toList()));
        state.set("wl.pending", null);
        for (Map.Entry<UUID, WlPending> en : wlPending.entrySet()) {
            String base = "wl.pending." + en.getKey();
            state.set(base + ".code", en.getValue().code);
            state.set(base + ".createdAt", en.getValue().createdAt);
        }
        state.set("wl.approved", null);
        for (Map.Entry<UUID, String> en : wlApproved.entrySet()) {
            String base = "wl.approved." + en.getKey();
            state.set(base + ".code", en.getValue());
        }
    }

    // ==== Helpers ====
    private String getOrCreateWlCode(UUID id) {
        WlPending ex = wlPending.get(id);
        if (ex != null && (System.currentTimeMillis() - ex.createdAt) <= wlCodeTtlMs) return ex.code;
        String code;
        do { code = randomCode(4); } while (wlCodeIndex.containsKey(code));
        WlPending wp = new WlPending(code, System.currentTimeMillis());
        wlPending.put(id, wp);
        wlCodeIndex.put(code, id);
        saveFn.run();
        return code;
    }

    private String randomCode(int len) {
        final String alph = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no confusing chars
        java.security.SecureRandom r;
        try { r = java.security.SecureRandom.getInstanceStrong(); } catch (Exception ex) { r = new java.security.SecureRandom(); }
        StringBuilder sb = new StringBuilder(len);
        for (int i=0;i<len;i++) sb.append(alph.charAt(r.nextInt(alph.length())));
        return sb.toString();
    }

    private void rebuildWlCodeIndex() {
        wlCodeIndex.clear();
        for (Map.Entry<UUID, WlPending> en : wlPending.entrySet()) {
            if (en.getValue() != null && en.getValue().code != null)
                wlCodeIndex.put(en.getValue().code.toUpperCase(Locale.ROOT), en.getKey());
        }
    }

    private void cleanupExpiredWlCodes(boolean save) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, WlPending>> it = wlPending.entrySet().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Map.Entry<UUID, WlPending> en = it.next();
            WlPending wp = en.getValue();
            if (wp == null || (now - wp.createdAt) > wlCodeTtlMs) {
                if (wp != null && wp.code != null) wlCodeIndex.remove(wp.code.toUpperCase(Locale.ROOT));
                it.remove();
                changed = true;
            }
        }
        if (changed && save) saveFn.run();
    }

    private String formatTime(long ms) {
        long totalSec = Math.max(0L, ms) / 1000L;
        long h = totalSec / 3600L, m = (totalSec % 3600L) / 60L, s = totalSec % 60L;
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        return String.format("%02dm %02ds", m, s);
    }
}
