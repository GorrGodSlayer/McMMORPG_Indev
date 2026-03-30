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

/**
 * DataManager — in-memory cache and YAML persistence.
 * Only class that reads or writes player YAML files.
 */
public class DataManager {

    private final MMORPGPlugin plugin;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    private File playerDataFolder;
    private BukkitTask autosaveTask;

    // ── Defaults (from config, applied to new players) ────────────────────────
    private double defaultMaxMana;
    private double defaultManaRegen;
    private double defaultMaxHealth;
    private double defaultHealthRegen;
    private double defaultMaxStamina;
    private double defaultStaminaRegen;
    private double defaultSprintDrain;
    private double defaultJumpDrain;
    private double defaultMaxArmour;

    public DataManager(MMORPGPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void init() {
        playerDataFolder = new File(
                plugin.getDataFolder(),
                plugin.getConfig().getString("storage.player-data-folder", "playerdata")
        );
        if (!playerDataFolder.exists() && !playerDataFolder.mkdirs()) {
            plugin.getLogger().severe("Could not create playerdata folder!");
        }
        loadConfigValues();
        startAutosave();
        plugin.getLogger().info("DataManager initialised.");
    }

    private void loadConfigValues() {
        var c = plugin.getConfig();
        defaultMaxMana      = c.getDouble("mana.default-max",              100.0);
        defaultManaRegen    = c.getDouble("mana.default-regen",              2.0);
        defaultMaxHealth    = c.getDouble("health.default-max",             500.0);
        defaultHealthRegen  = c.getDouble("health.default-regen",            1.0);
        defaultMaxStamina   = c.getDouble("stamina.default-max",            100.0);
        defaultStaminaRegen = c.getDouble("stamina.default-regen",           5.0);
        defaultSprintDrain  = c.getDouble("stamina.sprint-drain-per-tick",   1.0);
        defaultJumpDrain    = c.getDouble("stamina.jump-drain",              10.0);
        defaultMaxArmour    = c.getDouble("armour.default-max",             200.0);
    }

    private void startAutosave() {
        int secs = plugin.getConfig().getInt("storage.autosave-interval", 60);
        long ticks = secs * 20L;
        autosaveTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::saveDirty, ticks, ticks);
        plugin.getLogger().info("Autosave every " + secs + "s.");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public PlayerData get(UUID uuid)   { return cache.get(uuid); }
    public PlayerData get(Player p)    { return cache.get(p.getUniqueId()); }
    public Collection<PlayerData> getAll() { return cache.values(); }

    public PlayerData loadPlayer(UUID uuid) {
        File file = playerFile(uuid);
        PlayerData data;
        if (file.exists()) {
            data = deserialize(uuid, YamlConfiguration.loadConfiguration(file));
            plugin.getLogger().info("Loaded data for " + uuid);
        } else {
            data = freshData(uuid);
            plugin.getLogger().info("New player " + uuid + " — created defaults.");
        }
        cache.put(uuid, data);
        return data;
    }

    public void savePlayer(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;
        try {
            serialize(data).save(playerFile(uuid));
            data.clearDirty();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + uuid + ": " + e.getMessage());
        }
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        cache.remove(uuid);
    }

    public void saveAll() {
        if (autosaveTask != null) autosaveTask.cancel();
        cache.keySet().forEach(this::savePlayer);
        plugin.getLogger().info("Saved " + cache.size() + " player file(s).");
    }

    public void reloadConfig() { loadConfigValues(); }

    // ── Serialization ─────────────────────────────────────────────────────────

    private YamlConfiguration serialize(PlayerData d) {
        var y = new YamlConfiguration();

        y.set("mana.current",  d.getMana());
        y.set("mana.max",      d.getMaxMana());
        y.set("mana.regen",    d.getManaRegen());

        y.set("health.current", d.getHealth());
        y.set("health.max",     d.getMaxHealth());
        y.set("health.regen",   d.getHealthRegen());

        y.set("stamina.current", d.getStamina());
        y.set("stamina.max",     d.getMaxStamina());
        y.set("stamina.regen",   d.getStaminaRegen());

        y.set("armour.current", d.getArmour());
        y.set("armour.max",     d.getMaxArmour());

        y.set("souls", d.getSouls());

        return y;
    }

    private PlayerData deserialize(UUID uuid, YamlConfiguration y) {
        double maxMana      = y.getDouble("mana.max",      defaultMaxMana);
        double manaRegen    = y.getDouble("mana.regen",    defaultManaRegen);
        double maxHealth    = y.getDouble("health.max",    defaultMaxHealth);
        double healthRegen  = y.getDouble("health.regen",  defaultHealthRegen);
        double maxStamina   = y.getDouble("stamina.max",   defaultMaxStamina);
        double staminaRegen = y.getDouble("stamina.regen", defaultStaminaRegen);
        double maxArmour    = y.getDouble("armour.max",    defaultMaxArmour);

        PlayerData d = new PlayerData(uuid,
                maxMana, manaRegen,
                maxHealth, healthRegen,
                maxStamina, staminaRegen,
                defaultSprintDrain, defaultJumpDrain,
                maxArmour);

        d.setMana(    y.getDouble("mana.current",    maxMana));
        d.setHealth(  y.getDouble("health.current",  maxHealth));
        d.setStamina( y.getDouble("stamina.current", maxStamina));
        d.setArmour(  y.getDouble("armour.current",  maxArmour));
        d.setSouls(   y.getLong(  "souls",           0L));

        return d;
    }

    private PlayerData freshData(UUID uuid) {
        return new PlayerData(uuid,
                defaultMaxMana,    defaultManaRegen,
                defaultMaxHealth,  defaultHealthRegen,
                defaultMaxStamina, defaultStaminaRegen,
                defaultSprintDrain, defaultJumpDrain,
                defaultMaxArmour);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void saveDirty() {
        int n = 0;
        for (var e : cache.entrySet()) {
            if (e.getValue().isDirty()) { savePlayer(e.getKey()); n++; }
        }
        if (n > 0) plugin.getLogger().info("Autosave: flushed " + n + " record(s).");
    }

    private File playerFile(UUID uuid) {
        return new File(playerDataFolder, uuid + ".yml");
    }
}
