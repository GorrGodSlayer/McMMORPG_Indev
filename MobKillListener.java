package com.mmorpg.plugin.listeners;

import com.mmorpg.plugin.core.MMORPGPlugin;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.data.PlayerData;
import com.mmorpg.plugin.systems.hud.HudSystem;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashMap;
import java.util.Map;

public class MobKillListener implements Listener {

    private final MMORPGPlugin plugin;
    private final DataManager  dataManager;
    private final HudSystem    hudSystem;

    private final Map<String, Long> soulValues = new HashMap<>();
    private long defaultSoulValue;

    public MobKillListener(MMORPGPlugin plugin, DataManager dataManager, HudSystem hudSystem) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
        this.hudSystem   = hudSystem;
        loadConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntityType() == EntityType.PLAYER) return;

        PlayerData data = dataManager.get(killer);
        if (data == null) return;

        long award = soulValues.getOrDefault(event.getEntityType().name(), defaultSoulValue);
        if (award <= 0) return;

        data.addSouls(award);
        hudSystem.refreshDisplay(killer);
    }

    public void loadConfig() {
        FileConfiguration cfg = plugin.getConfig();
        defaultSoulValue = cfg.getLong("souls.default-value", 1L);
        soulValues.clear();
        var section = cfg.getConfigurationSection("souls.values");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                soulValues.put(key.toUpperCase(), section.getLong(key, defaultSoulValue));
            }
        }
        plugin.getLogger().info("MobKillListener: " + soulValues.size() + " soul values loaded.");
    }
}
