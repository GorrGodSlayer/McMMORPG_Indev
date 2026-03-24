package com.mmorpg.plugin.listeners;

import com.mmorpg.plugin.core.MMORPGPlugin;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.data.PlayerData;
import com.mmorpg.plugin.systems.mana.ManaSystem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * PlayerConnectionListener
 *
 * On join:  load PlayerData from disk → attach mana BossBar
 * On quit:  save PlayerData to disk  → remove mana BossBar
 *
 * Uses LOWEST priority on join so that PlayerData is available
 * to any other listener that fires at NORMAL or higher.
 */
public class PlayerConnectionListener implements Listener {

    private final MMORPGPlugin plugin;
    private final DataManager  dataManager;
    private final ManaSystem   manaSystem;

    public PlayerConnectionListener(MMORPGPlugin plugin,
                                    DataManager dataManager,
                                    ManaSystem manaSystem) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
        this.manaSystem  = manaSystem;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        // Load (or create) PlayerData and put it in the cache
        PlayerData data = dataManager.loadPlayer(player.getUniqueId());

        // Attach BossBar / XP bar display
        manaSystem.onPlayerJoin(player, data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();

        // Remove BossBar before we unload data
        manaSystem.onPlayerQuit(player);

        // Save and remove from cache
        dataManager.unloadPlayer(player.getUniqueId());
    }
}
