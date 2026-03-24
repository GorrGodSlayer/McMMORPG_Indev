package com.mmorpg.plugin.data;

import com.mmorpg.plugin.core.MMORPGPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * DataManager — owns the in-memory PlayerData cache and all disk I/O.
 *
 * Rules:
 *   - No other class reads or writes YAML files directly.
 *   - Every system calls DataManager.get(uuid) and works against
 *     the shared in-memory PlayerData instance.
 *   - Autosave flushes only dirty records every N seconds.
 *   - saveAll() flushes everything (called on onDisable).
 */
public class DataManager {

    private final MMORPGPlugin plugin;

    /** The single in-memory cache: one PlayerData per online player. */
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    private File playerDataFolder;
    private BukkitTask autosaveTask;

    // ── Default mana values (applied when no saved data exists) ──────────────
    // These will later be replaced by class-specific baselines.
    private double defaultMaxMana;
    private double defaultManaRegen;

    public DataManager(MMORPGPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void init() {
        // Resolve / create the playerdata folder
        playerDataFolder = new File(
                plugin.getDataFolder(),
                plugin.getConfig().getString("storage.player-data-folder", "playerdata")
        );
        if (!playerDataFolder.exists() && !playerDataFolder.mkdirs()) {
            plugin.getLogger().severe("Could not create playerdata folder!");
        }

        loadConfigValues();
        startAutosave();

        plugin.getLogger().info("DataManager initialised. Player data folder: "
                + playerDataFolder.getAbsolutePath());
    }

    private void loadConfigValues() {
        defaultMaxMana   = plugin.getConfig().getDouble("mana.default-max",   100.0);
        defaultManaRegen = plugin.getConfig().getDouble("mana.default-regen",   2.0);
    }

    private void startAutosave() {
        int intervalSeconds = plugin.getConfig().getInt("storage.autosave-interval", 60);
        long intervalTicks  = intervalSeconds * 20L;

        autosaveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::saveDirty,
                intervalTicks,
                intervalTicks
        );
        plugin.getLogger().info("Autosave scheduled every " + intervalSeconds + " seconds.");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the PlayerData for a given UUID, or null if not loaded.
     * Will always return non-null for an online player after loadPlayer() has been called.
     */
    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    /** Convenience overload — accepts a Player directly. */
    public PlayerData get(Player player) {
        return cache.get(player.getUniqueId());
    }

    /**
     * Loads a player's data from disk into the cache.
     * Called by PlayerConnectionListener on PlayerJoinEvent.
     * If no saved file exists, creates a fresh PlayerData with defaults.
     */
    public PlayerData loadPlayer(UUID uuid) {
        File file = playerFile(uuid);
        PlayerData data;

        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            data = deserialize(uuid, yaml);
            plugin.getLogger().info("Loaded player data for " + uuid);
        } else {
            data = new PlayerData(uuid, defaultMaxMana, defaultManaRegen);
            plugin.getLogger().info("No save file for " + uuid + " — created default PlayerData.");
        }

        cache.put(uuid, data);
        return data;
    }

    /**
     * Saves a single player's data to disk unconditionally.
     * Clears the dirty flag after writing.
     * Called on player quit and by saveAll().
     */
    public void savePlayer(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;

        File file = playerFile(uuid);
        YamlConfiguration yaml = serialize(data);

        try {
            yaml.save(file);
            data.clearDirty();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to save player data for " + uuid, e);
        }
    }

    /**
     * Removes a player's data from the cache after saving.
     * Called by PlayerConnectionListener on PlayerQuitEvent.
     */
    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        cache.remove(uuid);
    }

    /**
     * Saves all cached PlayerData to disk regardless of dirty flag.
     * Called on onDisable() to guarantee nothing is lost on shutdown.
     */
    public void saveAll() {
        if (autosaveTask != null) autosaveTask.cancel();
        for (UUID uuid : cache.keySet()) {
            savePlayer(uuid);
        }
        plugin.getLogger().info("Saved " + cache.size() + " player data file(s).");
    }

    /** Returns all currently loaded PlayerData objects (one per online player). */
    public Collection<PlayerData> getAll() {
        return cache.values();
    }

    /**
     * Reloads config values (called by /mmorpg reload).
     * Does NOT re-read existing player files — only updates defaults
     * for new players who join after the reload.
     */
    public void reloadConfig() {
        loadConfigValues();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Saves only dirty PlayerData entries. Called by the autosave scheduler. */
    private void saveDirty() {
        int count = 0;
        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
            if (entry.getValue().isDirty()) {
                savePlayer(entry.getKey());
                count++;
            }
        }
        if (count > 0) {
            plugin.getLogger().info("Autosave: flushed " + count + " dirty player record(s).");
        }
    }

    private File playerFile(UUID uuid) {
        return new File(playerDataFolder, uuid + ".yml");
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private YamlConfiguration serialize(PlayerData data) {
        YamlConfiguration yaml = new YamlConfiguration();

        // Mana
        yaml.set("mana.current",  data.getMana());
        yaml.set("mana.max",      data.getMaxMana());
        yaml.set("mana.regen",    data.getManaRegen());

        // Sprint 2+: race, class, level, xp, abilities will be added here

        return yaml;
    }

    private PlayerData deserialize(UUID uuid, YamlConfiguration yaml) {
        double maxMana   = yaml.getDouble("mana.max",   defaultMaxMana);
        double manaRegen = yaml.getDouble("mana.regen", defaultManaRegen);

        PlayerData data = new PlayerData(uuid, maxMana, manaRegen);

        // Restore current mana — defaults to maxMana if key is absent
        double current = yaml.getDouble("mana.current", maxMana);
        data.setMana(current);

        // Sprint 2+: race, class, level, xp, abilities will be read here

        return data;
    }
}
