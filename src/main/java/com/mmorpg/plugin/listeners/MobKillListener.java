package com.mmorpg.plugin.listeners;

import com.mmorpg.plugin.core.MMORPGPlugin;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.data.PlayerData;
import com.mmorpg.plugin.systems.mana.ManaSystem;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * MobKillListener — awards souls when a player kills a mob.
 *
 * Soul values per mob type are configured in config.yml under souls.values.
 * Any mob not listed falls back to souls.default-value.
 *
 * Players do NOT earn souls for killing other players — only mobs.
 * The action bar is refreshed immediately after a kill so the counter
 * updates instantly rather than waiting for the next display tick.
 */
public class MobKillListener implements Listener {

    private final MMORPGPlugin plugin;
    private final DataManager  dataManager;
    private final ManaSystem   manaSystem;

    /** Cache of EntityType name → souls awarded. Rebuilt on reload. */
    private final Map<String, Long> soulValues = new HashMap<>();
    private long defaultSoulValue;

    public MobKillListener(MMORPGPlugin plugin,
                           DataManager dataManager,
                           ManaSystem manaSystem) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
        this.manaSystem  = manaSystem;
        loadConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        // Only award souls if a player landed the killing blow
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        // Never award souls for killing other players
        if (event.getEntityType() == EntityType.PLAYER) return;

        PlayerData data = dataManager.get(killer);
        if (data == null) return;

        long award = getSoulValue(event.getEntityType());
        if (award <= 0) return;

        data.addSouls(award);

        // Refresh the action bar immediately so the player sees the new total
        manaSystem.refreshDisplay(killer);
    }

    /** Returns the souls value for a given entity type. */
    private long getSoulValue(EntityType type) {
        return soulValues.getOrDefault(type.name(), defaultSoulValue);
    }

    /**
     * Loads soul values from config.yml.
     * Called on startup and on /mmorpg reload.
     *
     * Config structure:
     *   souls:
     *     default-value: 1
     *     values:
     *       ZOMBIE: 1
     *       SKELETON: 1
     *       CREEPER: 3
     *       ENDERMAN: 5
     *       BLAZE: 4
     *       WITHER_SKELETON: 8
     *       ELDER_GUARDIAN: 20
     *       WITHER: 50
     *       ENDER_DRAGON: 200
     */
    public void loadConfig() {
        FileConfiguration cfg = plugin.getConfig();
        defaultSoulValue = cfg.getLong("souls.default-value", 1L);

        soulValues.clear();
        var section = cfg.getConfigurationSection("souls.values");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                long value = section.getLong(key, defaultSoulValue);
                soulValues.put(key.toUpperCase(), value);
            }
        }
        plugin.getLogger().info("MobKillListener: loaded " + soulValues.size()
                + " custom soul values. Default: " + defaultSoulValue + ".");
    }
}
