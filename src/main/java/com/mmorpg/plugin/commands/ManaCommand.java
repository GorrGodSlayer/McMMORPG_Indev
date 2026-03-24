package com.mmorpg.plugin.commands;

import com.mmorpg.plugin.core.MMORPGPlugin;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.data.PlayerData;
import com.mmorpg.plugin.systems.mana.ManaSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /mana — admin command for mana management.
 *
 * Subcommands:
 *   /mana set <player> <value>        — set current mana to a specific value
 *   /mana setmax <player> <value>     — set max mana
 *   /mana setregen <player> <value>   — set mana regen per tick
 *   /mana fill <player>               — instantly fill mana to max
 *   /mana get <player>                — print current mana info
 *   /mana help                        — show all subcommands
 *
 * All subcommands require the mmorpg.admin.mana permission.
 */
public class ManaCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "mmorpg.admin.mana";

    private static final List<String> SUBCOMMANDS =
            Arrays.asList("set", "setmax", "setregen", "fill", "get", "help");

    private final MMORPGPlugin plugin;
    private final DataManager  dataManager;
    private final ManaSystem   manaSystem;

    public ManaCommand(MMORPGPlugin plugin, DataManager dataManager, ManaSystem manaSystem) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
        this.manaSystem  = manaSystem;
    }

    // ── Command execution ─────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "set"      -> handleSet(sender, args);
            case "setmax"   -> handleSetMax(sender, args);
            case "setregen" -> handleSetRegen(sender, args);
            case "fill"     -> handleFill(sender, args);
            case "get"      -> handleGet(sender, args);
            default         -> { sendHelp(sender); yield true; }
        };
    }

    // ── Subcommand handlers ───────────────────────────────────────────────────

    /**
     * /mana set <player> <value>
     * Sets the player's current mana to [value] (clamped 0 ↔ maxMana).
     */
    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(usage("set <player> <value>"));
            return true;
        }

        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return true;

        Double value = parseDouble(sender, args[2]);
        if (value == null) return true;

        PlayerData data = getDataOrWarn(sender, target);
        if (data == null) return true;

        data.setMana(value);
        manaSystem.refreshDisplay(target);

        sender.sendMessage(info(
                "Set " + target.getName() + "'s mana to "
                + fmt(data.getMana()) + " / " + fmt(data.getMaxMana())));

        if (!sender.equals(target)) {
            target.sendMessage(info("Your mana was set to " + fmt(data.getMana())
                    + " by an admin."));
        }
        return true;
    }

    /**
     * /mana setmax <player> <value>
     * Sets the player's maximum mana. Current mana is re-clamped automatically.
     */
    private boolean handleSetMax(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(usage("setmax <player> <value>"));
            return true;
        }

        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return true;

        Double value = parseDouble(sender, args[2]);
        if (value == null) return true;

        if (value <= 0) {
            sender.sendMessage(error("Max mana must be greater than 0."));
            return true;
        }

        PlayerData data = getDataOrWarn(sender, target);
        if (data == null) return true;

        data.setMaxMana(value);
        manaSystem.refreshDisplay(target);

        sender.sendMessage(info(
                "Set " + target.getName() + "'s max mana to " + fmt(value)
                + ". Current: " + fmt(data.getMana())));
        return true;
    }

    /**
     * /mana setregen <player> <value>
     * Sets how much mana the player regenerates per regen tick.
     */
    private boolean handleSetRegen(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(usage("setregen <player> <value>"));
            return true;
        }

        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return true;

        Double value = parseDouble(sender, args[2]);
        if (value == null) return true;

        if (value < 0) {
            sender.sendMessage(error("Mana regen cannot be negative."));
            return true;
        }

        PlayerData data = getDataOrWarn(sender, target);
        if (data == null) return true;

        data.setManaRegen(value);

        sender.sendMessage(info(
                "Set " + target.getName() + "'s mana regen to " + fmt(value) + "/tick."));
        return true;
    }

    /**
     * /mana fill <player>
     * Instantly fills the player's mana to their maximum.
     */
    private boolean handleFill(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(usage("fill <player>"));
            return true;
        }

        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return true;

        PlayerData data = getDataOrWarn(sender, target);
        if (data == null) return true;

        data.fillMana();
        manaSystem.refreshDisplay(target);

        sender.sendMessage(info(
                target.getName() + "'s mana filled to " + fmt(data.getMaxMana()) + "."));

        if (!sender.equals(target)) {
            target.sendMessage(info("Your mana was filled by an admin."));
        }
        return true;
    }

    /**
     * /mana get <player>
     * Prints the player's full mana breakdown.
     */
    private boolean handleGet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(usage("get <player>"));
            return true;
        }

        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return true;

        PlayerData data = getDataOrWarn(sender, target);
        if (data == null) return true;

        sender.sendMessage(Component.text("─── " + target.getName() + " — Mana ───")
                .color(NamedTextColor.GOLD));
        sender.sendMessage(line("Current",   fmt(data.getMana())));
        sender.sendMessage(line("Maximum",   fmt(data.getMaxMana())));
        sender.sendMessage(line("Regen/tick", fmt(data.getManaRegen())));
        sender.sendMessage(line("Fraction",  String.format("%.1f%%", data.getManaFraction() * 100)));
        return true;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission(PERM)) return List.of();

        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (List.of("set","setmax","setregen","fill","get").contains(sub)) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (List.of("set","setmax","setregen").contains(sub)) {
                return List.of("0", "50", "100", "200", "500");
            }
        }

        return List.of();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Player resolvePlayer(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            sender.sendMessage(error("Player '" + name + "' is not online."));
        }
        return target;
    }

    private PlayerData getDataOrWarn(CommandSender sender, Player player) {
        PlayerData data = dataManager.get(player);
        if (data == null) {
            sender.sendMessage(error("No player data found for " + player.getName()
                    + ". This should not happen — please report it."));
        }
        return data;
    }

    private Double parseDouble(CommandSender sender, String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            sender.sendMessage(error("'" + s + "' is not a valid number."));
            return null;
        }
    }

    private String fmt(double value) {
        // Show one decimal place only if needed
        return value == Math.floor(value)
                ? String.valueOf((int) value)
                : String.format("%.1f", value);
    }

    private List<String> filter(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private Component info(String msg) {
        return Component.text("[Mana] ").color(NamedTextColor.AQUA)
                .append(Component.text(msg).color(NamedTextColor.WHITE));
    }

    private Component error(String msg) {
        return Component.text("[Mana] ").color(NamedTextColor.RED)
                .append(Component.text(msg).color(NamedTextColor.WHITE));
    }

    private Component usage(String sub) {
        return error("Usage: /mana " + sub);
    }

    private Component line(String label, String value) {
        return Component.text("  " + label + ": ").color(NamedTextColor.GRAY)
                .append(Component.text(value).color(NamedTextColor.WHITE));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("─── /mana commands ───").color(NamedTextColor.GOLD));
        sender.sendMessage(helpLine("/mana set <player> <value>",     "Set current mana"));
        sender.sendMessage(helpLine("/mana setmax <player> <value>",  "Set max mana"));
        sender.sendMessage(helpLine("/mana setregen <player> <value>","Set mana regen/tick"));
        sender.sendMessage(helpLine("/mana fill <player>",            "Fill mana to max"));
        sender.sendMessage(helpLine("/mana get <player>",             "Show mana breakdown"));
    }

    private Component helpLine(String cmd, String desc) {
        return Component.text("  " + cmd).color(NamedTextColor.YELLOW)
                .append(Component.text(" — " + desc).color(NamedTextColor.GRAY));
    }
}
