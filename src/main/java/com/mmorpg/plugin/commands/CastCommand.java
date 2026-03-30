package com.mmorpg.plugin.commands;

import com.mmorpg.plugin.abilities.DemoAoeBurst;
import com.mmorpg.plugin.core.MMORPGPlugin;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.data.PlayerData;
import com.mmorpg.plugin.systems.hud.HudSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /cast — proof-of-concept ability command.
 *
 * Usage:
 *   /cast demo   — fires the DemoAoeBurst ability at the caster's location
 *   /cast help   — lists available demo abilities
 *
 * This command will be replaced by the keybind-driven AbilityManager
 * in a later sprint. It exists purely to demonstrate and test the
 * ability pipeline (mana cost, stamina cost, cooldown, damage, particles)
 * without needing the full input system in place.
 */
public class CastCommand implements CommandExecutor, TabCompleter {

    private final DataManager  dataManager;
    private final DemoAoeBurst demoAbility;

    public CastCommand(MMORPGPlugin plugin,
                       DataManager dataManager,
                       HudSystem hudSystem) {
        this.dataManager  = dataManager;
        this.demoAbility  = new DemoAoeBurst(plugin, hudSystem);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        // Only players can cast abilities
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can cast abilities.")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return true;
        }

        PlayerData data = dataManager.get(player);
        if (data == null) {
            player.sendMessage(Component.text("Player data not loaded — try rejoining.")
                    .color(NamedTextColor.RED));
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "demo" -> { demoAbility.cast(player, data); yield true; }
            default     -> { sendHelp(player); yield true; }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return List.of("demo", "help");
        }
        return List.of();
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("─── /cast abilities ───").color(NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(
                Component.text("  /cast demo").color(NamedTextColor.YELLOW)
                        .append(Component.text(" — AOE burst · " + (int) DemoAoeBurst.MANA_COST
                                + " mana · " + (int) DemoAoeBurst.STAMINA_COST
                                + " stamina · " + DemoAoeBurst.RADIUS + "m radius · "
                                + DemoAoeBurst.COOLDOWN_MS / 1000 + "s cooldown")
                                .color(NamedTextColor.GRAY)));
    }
}
