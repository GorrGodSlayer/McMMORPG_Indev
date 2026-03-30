package com.mmorpg.plugin.listeners;

import com.mmorpg.plugin.core.MMORPGPlugin;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.data.PlayerData;
import com.mmorpg.plugin.systems.hud.HudSystem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final MMORPGPlugin plugin;
    private final DataManager  dataManager;
    private final HudSystem    hudSystem;

    public PlayerConnectionListener(MMORPGPlugin plugin,
                                    DataManager dataManager,
                                    HudSystem hudSystem) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
        this.hudSystem   = hudSystem;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        PlayerData data = dataManager.loadPlayer(player.getUniqueId());
        hudSystem.onPlayerJoin(player, data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        hudSystem.onPlayerQuit(player);
        dataManager.unloadPlayer(player.getUniqueId());
    }
}
