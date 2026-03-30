package com.mmorpg.plugin.core;

import com.mmorpg.plugin.commands.CastCommand;
import com.mmorpg.plugin.commands.ManaCommand;
import com.mmorpg.plugin.commands.MmorpgCommand;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.listeners.CombatListener;
import com.mmorpg.plugin.listeners.MobKillListener;
import com.mmorpg.plugin.listeners.PlayerConnectionListener;
import com.mmorpg.plugin.listeners.StaminaListener;
import com.mmorpg.plugin.systems.hud.HudSystem;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MMORPGPlugin — entry point.
 *
 * Startup order:
 *   1. DataManager   — cache + persistence (must be first)
 *   2. HudSystem     — all regen loops + action bar display
 *   3. Listeners     — route events to systems
 *   4. Commands      — player and admin commands
 */
public final class MMORPGPlugin extends JavaPlugin {

    private static MMORPGPlugin instance;
    public static MMORPGPlugin getInstance() { return instance; }

    private DataManager  dataManager;
    private HudSystem    hudSystem;
    private MobKillListener mobKillListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        getLogger().info("=== MMORPGPlugin starting up ===");

        dataManager = new DataManager(this);
        dataManager.init();

        hudSystem = new HudSystem(this, dataManager);
        hudSystem.start();

        registerListeners();
        registerCommands();

        getLogger().info("=== MMORPGPlugin ready ===");
    }

    @Override
    public void onDisable() {
        if (hudSystem   != null) hudSystem.stop();
        if (dataManager != null) dataManager.saveAll();
        getLogger().info("=== MMORPGPlugin disabled ===");
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();

        pm.registerEvents(new PlayerConnectionListener(this, dataManager, hudSystem), this);
        pm.registerEvents(new StaminaListener(this, dataManager), this);
        pm.registerEvents(new CombatListener(this, dataManager, hudSystem), this);

        mobKillListener = new MobKillListener(this, dataManager, hudSystem);
        pm.registerEvents(mobKillListener, this);
    }

    private void registerCommands() {
        // /mana
        var manaCmd = new ManaCommand(this, dataManager, hudSystem);
        var mana = getCommand("mana");
        if (mana != null) { mana.setExecutor(manaCmd); mana.setTabCompleter(manaCmd); }

        // /mmorpg
        var mmorpgCmd = new MmorpgCommand(this, dataManager);
        var mmorpg = getCommand("mmorpg");
        if (mmorpg != null) { mmorpg.setExecutor(mmorpgCmd); mmorpg.setTabCompleter(mmorpgCmd); }

        // /cast
        var castCmd = new CastCommand(this, dataManager, hudSystem);
        var cast = getCommand("cast");
        if (cast != null) { cast.setExecutor(castCmd); cast.setTabCompleter(castCmd); }
    }

    public void reload() {
        reloadConfig();
        hudSystem.reloadConfig();
        dataManager.reloadConfig();
        if (mobKillListener != null) mobKillListener.loadConfig();
        getLogger().info("Configuration reloaded.");
    }

    public DataManager getDataManager() { return dataManager; }
    public HudSystem   getHudSystem()   { return hudSystem;   }
}
