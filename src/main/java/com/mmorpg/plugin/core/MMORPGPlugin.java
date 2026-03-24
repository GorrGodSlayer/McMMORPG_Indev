package com.mmorpg.plugin.core;

import com.mmorpg.plugin.commands.ManaCommand;
import com.mmorpg.plugin.commands.MmorpgCommand;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.listeners.MobKillListener;
import com.mmorpg.plugin.listeners.PlayerConnectionListener;
import com.mmorpg.plugin.systems.mana.ManaSystem;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * MMORPGPlugin — entry point.
 *
 * Startup order:
 *   1. DataManager          (must be first — everything else reads from it)
 *   2. ManaSystem           (depends on DataManager)
 *   3. Listeners            (depend on all systems)
 *   4. Commands             (depend on all systems)
 *
 * More managers (RaceManager, ClassManager, AbilityManager, etc.)
 * will be registered here as they are built in later sprints.
 */
public final class MMORPGPlugin extends JavaPlugin {

    // ── Singleton access ────────────────────────────────────────────────────
    private static MMORPGPlugin instance;

    public static MMORPGPlugin getInstance() {
        return instance;
    }

    // ── Manager references ──────────────────────────────────────────────────
    private DataManager dataManager;
    private ManaSystem manaSystem;
    private MobKillListener mobKillListener;

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        instance = this;

        // Save default config.yml if not already present
        saveDefaultConfig();

        getLogger().info("=== MMORPGPlugin starting up ===");

        // 1. Data layer — must come first
        dataManager = new DataManager(this);
        dataManager.init();

        // 2. Mana system
        manaSystem = new ManaSystem(this, dataManager);
        manaSystem.start();

        // 3. Listeners
        registerListeners();

        // 4. Commands
        registerCommands();

        getLogger().info("=== MMORPGPlugin ready. ===");
    }

    @Override
    public void onDisable() {
        // Stop the mana regen loop before we flush data
        if (manaSystem != null) {
            manaSystem.stop();
        }

        // Flush ALL online player data to disk regardless of dirty flag
        if (dataManager != null) {
            dataManager.saveAll();
            getLogger().info("All player data saved.");
        }

        getLogger().info("=== MMORPGPlugin disabled. ===");
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(this, dataManager, manaSystem), this);
        mobKillListener = new MobKillListener(this, dataManager, manaSystem);
        getServer().getPluginManager().registerEvents(mobKillListener, this);
    }

    private void registerCommands() {
        // /mana — admin mana control
        ManaCommand manaCmd = new ManaCommand(this, dataManager, manaSystem);
        var manaExecutor = getCommand("mana");
        if (manaExecutor != null) {
            manaExecutor.setExecutor(manaCmd);
            manaExecutor.setTabCompleter(manaCmd);
        } else {
            getLogger().warning("Could not register /mana — is it in plugin.yml?");
        }

        // /mmorpg — top-level admin
        MmorpgCommand mmorpgCmd = new MmorpgCommand(this, dataManager);
        var mmorpgExecutor = getCommand("mmorpg");
        if (mmorpgExecutor != null) {
            mmorpgExecutor.setExecutor(mmorpgCmd);
            mmorpgExecutor.setTabCompleter(mmorpgCmd);
        }
    }

    // ── Public accessors ────────────────────────────────────────────────────

    public DataManager getDataManager() {
        return dataManager;
    }

    public ManaSystem getManaSystem() {
        return manaSystem;
    }

    /**
     * Reload config.yml and propagate changes to active systems.
     * Called by /mmorpg reload.
     */
    public void reload() {
        reloadConfig();
        manaSystem.reloadConfig();
        if (mobKillListener != null) mobKillListener.loadConfig();
        getLogger().info("Configuration reloaded.");
    }
}
