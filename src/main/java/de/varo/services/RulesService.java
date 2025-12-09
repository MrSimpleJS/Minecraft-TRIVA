package de.varo.services;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class RulesService {
    private final JavaPlugin plugin;
    private final Set<java.util.UUID> rulesAccepted;
    private final Runnable saveState;
    private final java.util.function.Consumer<String> log;
    private final java.util.Map<java.util.UUID, Integer> reminderTasks = new java.util.HashMap<>();

    public RulesService(JavaPlugin plugin, Set<java.util.UUID> rulesAccepted, Runnable saveState) {
        this.plugin = plugin;
        this.rulesAccepted = rulesAccepted;
        this.saveState = saveState;
        this.log = (msg) -> { try { plugin.getLogger().info(msg); } catch (Throwable ignored) {} };
    }

    public boolean hasAccepted(java.util.UUID id) { return rulesAccepted.contains(id); }

    public void markAccepted(Player p) {
        rulesAccepted.add(p.getUniqueId());
        p.sendMessage(org.bukkit.ChatColor.GREEN + "Danke! Du hast die Regeln akzeptiert.");
    try { log.accept("[rules] " + p.getName() + " hat die Regeln akzeptiert."); } catch (Throwable ignored) {}
        // Cancel any pending reminder
        Integer taskId = reminderTasks.remove(p.getUniqueId());
        if (taskId != null) { try { plugin.getServer().getScheduler().cancelTask(taskId); } catch (Throwable ignored) {} }
        if (saveState != null) try { saveState.run(); } catch (Throwable ignored) {}
    }

    public void scheduleOpenBook(Player p, long delayTicks) {
        new BukkitRunnable(){
            @Override public void run(){ if (p.isOnline()) openBook(p); }
        }.runTaskLater(plugin, Math.max(1L, delayTicks));
    }

    public void openBook(Player p) {
        try {
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) book.getItemMeta();
            if (meta != null) {
                meta.setTitle("TRIVA Regeln");
                meta.setAuthor("Server");

                // Fallback pages
                java.util.List<String> pages = new java.util.ArrayList<>();
                pages.add(org.bukkit.ChatColor.BOLD + "WICHTIG: Regeln\n\n"
                        + org.bukkit.ChatColor.RESET + "• Nur 2er-Teams erlaubt\n"
                        + "• Multi-Teams nicht erlaubt\n"
                        + "• Fairplay, kein Ghosting\n"
                        + "• Combat-Log wird bestraft\n\n"
                        + org.bukkit.ChatColor.DARK_GRAY + "Weiter mit ▶");
                pages.add(org.bukkit.ChatColor.BOLD + "Nether & Welt\n\n" + org.bukkit.ChatColor.RESET
                        + "• Nether-Zeit pro Spieler: 1 Stunde\n"
                        + "• Danach Druck/TP in Overworld\n"
                        + "• End ist deaktiviert\n\n"
                        + org.bukkit.ChatColor.DARK_GRAY + "Weiter mit ▶");
                pages.add(org.bukkit.ChatColor.BOLD + "Tipps\n\n" + org.bukkit.ChatColor.RESET
                        + "• Wenn deine Nether-Zeit fast abgelaufen ist,\n"
                        + "  nimm genug Obsidian mit, um sofort ein\n"
                        + "  Portal bauen zu können.\n\n"
                        + org.bukkit.ChatColor.DARK_GRAY + "Weiter mit ▶");
                pages.add(org.bukkit.ChatColor.BOLD + "Border & PvP / Fairplay\n\n" + org.bukkit.ChatColor.RESET
                        + "• Border schrumpft regelmäßig\n"
                        + "• Spawn-/Rejoin-Schutz aktiv\n"
                        + "• Verzauberte Goldäpfel verboten\n\n"
                        + "• Hacks sind verboten und werden geloggt\n"
                        + "• Bei auffälligem Verhalten droht Disqualifikation\n"
                        + "• Gewinnt ein Team unrechtmäßig, gilt der 2. Platz\n"
                        + "  als Sieger und erhält das Preisgeld\n\n"
                        + org.bukkit.ChatColor.GOLD + "Akzeptiere mit: /rulesaccept");
                meta.setPages(pages);

                // Component pages
                try {
                    // Page 1
                    TextComponent p1 = new TextComponent("WICHTIG: Regeln\n\n" +
                            "• Nur 2er-Teams erlaubt\n" +
                            "• Multi-Teams nicht erlaubt\n" +
                            "• Fairplay, kein Ghosting\n" +
                            "• Combat-Log wird bestraft\n\n" +
                            "Weiter mit ▶");
                    p1.setBold(true);
                    p1.setColor(ChatColor.GOLD);
                    meta.spigot().setPage(1, new BaseComponent[]{ p1 });

                    // Page 2
                    TextComponent p2 = new TextComponent("Nether & Welt\n\n" +
                            "• Nether-Zeit pro Spieler: 1 Stunde\n" +
                            "• Danach Druck/TP in Overworld\n" +
                            "• End ist deaktiviert\n\n" +
                            "Weiter mit ▶");
                    p2.setBold(true);
                    p2.setColor(ChatColor.GOLD);
                    meta.spigot().setPage(2, new BaseComponent[]{ p2 });

                    // Page 3 (Tips)
                    TextComponent p3 = new TextComponent("Tipps\n\n" +
                            "• Wenn deine Nether-Zeit fast abgelaufen ist,\n" +
                            "  nimm genug Obsidian mit, um sofort ein\n" +
                            "  Portal bauen zu können.\n\n" +
                            "Weiter mit ▶");
                    p3.setBold(true);
                    p3.setColor(ChatColor.GOLD);
                    meta.spigot().setPage(3, new BaseComponent[]{ p3 });

                    // Page 4 (Fairplay + Accept)
                    TextComponent p4Intro = new TextComponent("Border & PvP / Fairplay\n\n" +
                            "• Border schrumpft regelmäßig\n" +
                            "• Spawn-/Rejoin-Schutz aktiv\n" +
                            "• Verzauberte Goldäpfel verboten\n\n" +
                            "• Hacks sind verboten und werden geloggt\n" +
                            "• Bei auffälligem Verhalten droht Disqualifikation\n" +
                            "• Gewinnt ein Team unrechtmäßig, gilt der 2. Platz\n" +
                            "  als Sieger und erhält das Preisgeld\n\n");
                    p4Intro.setBold(true);
                    p4Intro.setColor(ChatColor.GOLD);

                    TextComponent accept = new TextComponent("[ AKZEPTIEREN ]");
                    accept.setBold(true);
                    accept.setColor(ChatColor.GREEN);
                    accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rulesaccept"));

                    TextComponent hint = new TextComponent("\n\n(oder tippe /rulesaccept)");
                    hint.setColor(ChatColor.DARK_GRAY);

                    meta.spigot().setPage(4, new BaseComponent[]{ p4Intro, accept, hint });
                } catch (Throwable ignored) {
                    // Component pages might not be supported; fallback already set
                }

                book.setItemMeta(meta);
            }
            p.openBook(book);
            // If player closes without accepting, remind after ~2 minutes
            scheduleReminderIfPending(p.getUniqueId());
        } catch (Throwable t) {
            p.sendMessage(org.bukkit.ChatColor.YELLOW + "Bitte akzeptiere die Regeln mit " + org.bukkit.ChatColor.WHITE + "/rulesaccept");
        }
    }

    private void scheduleReminderIfPending(java.util.UUID id) {
        if (rulesAccepted.contains(id)) return;
        // Avoid duplicate reminders
        if (reminderTasks.containsKey(id)) return;
        int tid = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            reminderTasks.remove(id);
            if (rulesAccepted.contains(id)) return;
            org.bukkit.entity.Player pl = plugin.getServer().getPlayer(id);
            if (pl != null && pl.isOnline()) {
                try { pl.sendMessage(org.bukkit.ChatColor.YELLOW + "Bitte akzeptiere die Regeln. Das Buch öffnet sich erneut …"); } catch (Throwable ignored) {}
                scheduleOpenBook(pl, 20L * 2); // short delay to open after message
            }
        }, 20L * 120).getTaskId(); // ~2 minutes
        reminderTasks.put(id, tid);
    }
}
