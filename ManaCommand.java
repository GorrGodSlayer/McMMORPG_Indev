package com.mmorpg.plugin.commands;

import com.mmorpg.plugin.core.MMORPGPlugin;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.data.PlayerData;
import com.mmorpg.plugin.systems.hud.HudSystem;
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

public class ManaCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "mmorpg.admin.mana";
    private static final List<String> SUBCOMMANDS = Arrays.asList("set", "setmax", "setregen", "fill", "get", "help");

    private final MMORPGPlugin plugin;
    private final DataManager  dataManager;
    private final HudSystem    hudSystem;

    public ManaCommand(MMORPGPlugin plugin, DataManager dataManager, HudSystem hudSystem) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
        this.hudSystem   = hudSystem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(error("You don't have permission."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { sendHelp(sender); return true; }

        return switch (args[0].toLowerCase()) {
            case "set"      -> handleSet(sender, args);
            case "setmax"   -> handleSetMax(sender, args);
            case "setregen" -> handleSetRegen(sender, args);
            case "fill"     -> handleFill(sender, args);
            case "get"      -> handleGet(sender, args);
            default         -> { sendHelp(sender); yield true; }
        };
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(usage("set <player> <value>")); return true; }
        Player target = resolve(sender, args[1]); if (target == null) return true;
        Double value = parseDouble(sender, args[2]); if (value == null) return true;
        PlayerData data = getData(sender, target); if (data == null) return true;
        data.setMana(value);
        hudSystem.refreshDisplay(target);
        sender.sendMessage(info("Set " + target.getName() + "'s mana to " + fmt(data.getMana()) + "/" + fmt(data.getMaxMana())));
        return true;
    }

    private boolean handleSetMax(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(usage("setmax <player> <value>")); return true; }
        Player target = resolve(sender, args[1]); if (target == null) return true;
        Double value = parseDouble(sender, args[2]); if (value == null) return true;
        if (value <= 0) { sender.sendMessage(error("Max mana must be > 0.")); return true; }
        PlayerData data = getData(sender, target); if (data == null) return true;
        data.setMaxMana(value);
        hudSystem.refreshDisplay(target);
        sender.sendMessage(info("Set " + target.getName() + "'s max mana to " + fmt(value)));
        return true;
    }

    private boolean handleSetRegen(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(usage("setregen <player> <value>")); return true; }
        Player target = resolve(sender, args[1]); if (target == null) return true;
        Double value = parseDouble(sender, args[2]); if (value == null) return true;
        PlayerData data = getData(sender, target); if (data == null) return true;
        data.setManaRegen(value);
        sender.sendMessage(info("Set " + target.getName() + "'s mana regen to " + fmt(value) + "/tick"));
        return true;
    }

    private boolean handleFill(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(usage("fill <player>")); return true; }
        Player target = resolve(sender, args[1]); if (target == null) return true;
        PlayerData data = getData(sender, target); if (data == null) return true;
        data.fillMana();
        hudSystem.refreshDisplay(target);
        sender.sendMessage(info("Filled " + target.getName() + "'s mana."));
        return true;
    }

    private boolean handleGet(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(usage("get <player>")); return true; }
        Player target = resolve(sender, args[1]); if (target == null) return true;
        PlayerData data = getData(sender, target); if (data == null) return true;
        sender.sendMessage(Component.text("── " + target.getName() + " Mana ──").color(NamedTextColor.GOLD));
        sender.sendMessage(line("Current", fmt(data.getMana())));
        sender.sendMessage(line("Max",     fmt(data.getMaxMana())));
        sender.sendMessage(line("Regen",   fmt(data.getManaRegen()) + "/tick"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERM)) return List.of();
        if (args.length == 1) return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        if (args.length == 2 && List.of("set","setmax","setregen","fill","get").contains(args[0].toLowerCase()))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        if (args.length == 3 && List.of("set","setmax","setregen").contains(args[0].toLowerCase()))
            return List.of("50", "100", "200", "500");
        return List.of();
    }

    private Player resolve(CommandSender s, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) s.sendMessage(error("Player '" + name + "' not online."));
        return p;
    }
    private PlayerData getData(CommandSender s, Player p) {
        PlayerData d = dataManager.get(p);
        if (d == null) s.sendMessage(error("No data for " + p.getName() + "."));
        return d;
    }
    private Double parseDouble(CommandSender s, String str) {
        try { return Double.parseDouble(str); }
        catch (NumberFormatException e) { s.sendMessage(error("'" + str + "' is not a number.")); return null; }
    }
    private String fmt(double v) { return v == Math.floor(v) ? String.valueOf((int)v) : String.format("%.1f", v); }
    private Component info(String m)  { return Component.text("[Mana] ").color(NamedTextColor.AQUA).append(Component.text(m).color(NamedTextColor.WHITE)); }
    private Component error(String m) { return Component.text("[Mana] ").color(NamedTextColor.RED).append(Component.text(m).color(NamedTextColor.WHITE)); }
    private Component usage(String s) { return error("Usage: /mana " + s); }
    private Component line(String l, String v) { return Component.text("  " + l + ": ").color(NamedTextColor.GRAY).append(Component.text(v).color(NamedTextColor.WHITE)); }
    private void sendHelp(CommandSender s) {
        s.sendMessage(Component.text("── /mana ──").color(NamedTextColor.GOLD));
        s.sendMessage(Component.text("  /mana set <player> <value>").color(NamedTextColor.YELLOW));
        s.sendMessage(Component.text("  /mana setmax <player> <value>").color(NamedTextColor.YELLOW));
        s.sendMessage(Component.text("  /mana setregen <player> <value>").color(NamedTextColor.YELLOW));
        s.sendMessage(Component.text("  /mana fill <player>").color(NamedTextColor.YELLOW));
        s.sendMessage(Component.text("  /mana get <player>").color(NamedTextColor.YELLOW));
    }
}
