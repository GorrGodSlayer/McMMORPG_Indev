package com.mmorpg.plugin.commands;

import com.mmorpg.plugin.core.MMORPGPlugin;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /mmorpg — top-level admin command.
 *
 * Subcommands:
 *   /mmorpg reload           — hot-reload config.yml
 *   /mmorpg debug <player>   — dump a player's full PlayerData to chat
 *   /mmorpg help             — list subcommands
 *
 * More subcommands (set race, set class, etc.) will be added here
 * as those systems are built in later sprints.
 */
public class MmorpgCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "mmorpg.admin";
    private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "debug", "help");

    private final MMORPGPlugin plugin;
    private final DataManager  dataManager;

    public MmorpgCommand(MMORPGPlugin plugin, DataManager dataManager) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
    }

    // ── Command execution ─────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(error("You don't have permission to use this command."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "debug"  -> handleDebug(sender, args);
            default       -> { sendHelp(sender); yield true; }
        };
    }

    // ── Subcommand handlers ───────────────────────────────────────────────────

    private boolean handleReload(CommandSender sender) {
        plugin.reload();
        sender.sendMessage(info("Configuration reloaded successfully."));
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(error("Usage: /mmorpg debug <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(error("Player '" + args[1] + "' is not online."));
            return true;
        }

        PlayerData data = dataManager.get(target);
        if (data == null) {
            sender.sendMessage(error("No player data found for " + target.getName() + "."));
            return true;
        }

        sender.sendMessage(Component.text("─── Debug: " + target.getName() + " ───")
                .color(NamedTextColor.GOLD));

        // Sprint 1: mana fields only
        // Race, class, level, XP, abilities will be added in later sprints
        sender.sendMessage(line("UUID",       data.getPlayerUUID().toString()));
        sender.sendMessage(line("Mana",       fmt(data.getMana()) + " / " + fmt(data.getMaxMana())));
        sender.sendMessage(line("Regen/tick", fmt(data.getManaRegen())));
        sender.sendMessage(line("Fraction",   String.format("%.1f%%", data.getManaFraction() * 100)));
        sender.sendMessage(line("Dirty flag", String.valueOf(data.isDirty())));

        return true;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission(PERM)) return List.of();

        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Component info(String msg) {
        return Component.text("[MMORPG] ").color(NamedTextColor.GOLD)
                .append(Component.text(msg).color(NamedTextColor.WHITE));
    }

    private Component error(String msg) {
        return Component.text("[MMORPG] ").color(NamedTextColor.RED)
                .append(Component.text(msg).color(NamedTextColor.WHITE));
    }

    private Component line(String label, String value) {
        return Component.text("  " + label + ": ").color(NamedTextColor.GRAY)
                .append(Component.text(value).color(NamedTextColor.WHITE));
    }

    private String fmt(double value) {
        return value == Math.floor(value)
                ? String.valueOf((int) value)
                : String.format("%.1f", value);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("─── /mmorpg commands ───").color(NamedTextColor.GOLD));
        sender.sendMessage(helpLine("/mmorpg reload",         "Hot-reload config.yml"));
        sender.sendMessage(helpLine("/mmorpg debug <player>", "Dump player data to chat"));
    }

    private Component helpLine(String cmd, String desc) {
        return Component.text("  " + cmd).color(NamedTextColor.YELLOW)
                .append(Component.text(" — " + desc).color(NamedTextColor.GRAY));
    }
}
