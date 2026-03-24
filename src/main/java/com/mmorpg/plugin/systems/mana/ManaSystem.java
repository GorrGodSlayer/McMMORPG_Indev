package com.mmorpg.plugin.systems.mana;

import com.mmorpg.plugin.core.MMORPGPlugin;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * ManaSystem — mana regeneration and action bar HUD.
 *
 * Action bar layout (centered, ~1/3 of screen width):
 *
 *   ✦ Mana  ████████████████░░░░░░░░  80/100   ◆ Souls  4,231
 *
 * The bar segment count is tunable in config.yml (default 24).
 * Padding spaces on each side center it on the action bar line.
 *
 * Two tasks:
 *   regenTask     — fires every regen-interval-ticks (default 20 = 1s)
 *                   adds mana and refreshes the bar.
 *   actionBarTask — fires every action-bar-refresh-ticks (default 10 = 0.5s)
 *                   display-only refresh so the bar never disappears.
 */
public class ManaSystem {

    private final MMORPGPlugin plugin;
    private final DataManager  dataManager;

    private BukkitTask regenTask;
    private BukkitTask actionBarTask;

    // ── Config ────────────────────────────────────────────────────────────────
    private long   regenIntervalTicks;
    private long   actionBarRefreshTicks;
    private double defaultMax;
    private double defaultRegen;

    // Mana bar
    private int    barLength;
    private char   barFilled;
    private char   barEmpty;
    private String barFilledColor;
    private String barEmptyColor;
    private String manaLabelColor;
    private String manaValueColor;

    // Souls
    private String soulsLabelColor;
    private String soulsValueColor;

    // Padding to center the bar on screen
    private String padding;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ManaSystem(MMORPGPlugin plugin, DataManager dataManager) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
        loadConfig();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        regenTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tickRegen,
                regenIntervalTicks, regenIntervalTicks
        );

        if (actionBarRefreshTicks < regenIntervalTicks) {
            actionBarTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin, this::tickDisplay,
                    actionBarRefreshTicks, actionBarRefreshTicks
            );
        }

        plugin.getLogger().info("ManaSystem started. "
                + "Regen every " + regenIntervalTicks + " ticks, "
                + "bar refresh every " + actionBarRefreshTicks + " ticks.");
    }

    public void stop() {
        if (regenTask     != null) { regenTask.cancel();     regenTask = null;     }
        if (actionBarTask != null) { actionBarTask.cancel(); actionBarTask = null; }
        Component empty = Component.empty();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(empty);
        }
    }

    public void reloadConfig() {
        stop();
        loadConfig();
        start();
    }

    // ── Player hooks ──────────────────────────────────────────────────────────

    public void onPlayerJoin(Player player, PlayerData data) {
        sendActionBar(player, data);
    }

    public void onPlayerQuit(Player player) {
        player.sendActionBar(Component.empty());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Immediately refreshes the action bar for one player. */
    public void refreshDisplay(Player player) {
        PlayerData data = dataManager.get(player);
        if (data == null) return;
        sendActionBar(player, data);
    }

    // ── Tick handlers ─────────────────────────────────────────────────────────

    private void tickRegen() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = dataManager.get(player);
            if (data == null) continue;
            data.regenMana(data.getManaRegen());
            sendActionBar(player, data);
        }
    }

    private void tickDisplay() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = dataManager.get(player);
            if (data == null) continue;
            sendActionBar(player, data);
        }
    }

    // ── Action bar rendering ──────────────────────────────────────────────────

    private void sendActionBar(Player player, PlayerData data) {
        player.sendActionBar(buildComponent(data));
    }

    /**
     * Builds the full action bar Component.
     *
     * Layout:
     *   [padding] ✦ Mana [bar] cur/max   ◆ Souls  n,nnn [padding]
     *
     * Padding spaces on each side push the content to the center of
     * the action bar line, which is approximately 1/3 of the screen.
     *
     * Future additions (stamina, class, level) slot in between
     * the souls value and the right padding without changing this structure.
     */
    private Component buildComponent(PlayerData data) {
        int   curMana = (int) data.getMana();
        int   maxMana = (int) data.getMaxMana();
        float frac    = data.getManaFraction();
        long  souls   = data.getSouls();

        TextColor manaLabel  = TextColor.fromHexString(manaLabelColor);
        TextColor manaVal    = TextColor.fromHexString(manaValueColor);
        TextColor soulsLabel = TextColor.fromHexString(soulsLabelColor);
        TextColor soulsVal   = TextColor.fromHexString(soulsValueColor);

        // ── Mana section ──────────────────────────────────────────────────────
        Component manaSection = Component.text("\u2726 Mana  ")   // ✦ Mana
                .color(manaLabel)
                .decoration(TextDecoration.ITALIC, false)
                .append(buildVisualBar(frac))
                .append(Component.text("  " + curMana + "/" + maxMana)
                        .color(manaVal)
                        .decoration(TextDecoration.ITALIC, false));

        // ── Separator ─────────────────────────────────────────────────────────
        Component sep = Component.text("   \u2502   ")            // │
                .color(TextColor.fromHexString("#2a2a3a"))
                .decoration(TextDecoration.ITALIC, false);

        // ── Souls section ─────────────────────────────────────────────────────
        Component soulsSection = Component.text("\u25C6 Souls  ")  // ◆ Souls
                .color(soulsLabel)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(formatSouls(souls))
                        .color(soulsVal)
                        .decoration(TextDecoration.ITALIC, false));

        // ── Assemble with padding ─────────────────────────────────────────────
        Component pad = Component.text(padding)
                .decoration(TextDecoration.ITALIC, false);

        return pad
                .append(manaSection)
                .append(sep)
                .append(soulsSection)
                .append(pad);
    }

    /**
     * Builds the filled/empty visual mana bar.
     * Each portion is a separate colored Component rendered inline.
     */
    private Component buildVisualBar(float fraction) {
        int filled = Math.round(fraction * barLength);
        int empty  = barLength - filled;

        TextColor filledColor = TextColor.fromHexString(barFilledColor);
        TextColor emptyColor  = TextColor.fromHexString(barEmptyColor);

        Component bar = Component.text(String.valueOf(barFilled).repeat(Math.max(0, filled)))
                .color(filledColor)
                .decoration(TextDecoration.ITALIC, false);

        if (empty > 0) {
            bar = bar.append(
                    Component.text(String.valueOf(barEmpty).repeat(empty))
                            .color(emptyColor)
                            .decoration(TextDecoration.ITALIC, false)
            );
        }
        return bar;
    }

    /**
     * Formats souls with comma separators for readability.
     * e.g. 4231 → "4,231"  |  1000000 → "1,000,000"
     */
    private String formatSouls(long souls) {
        return String.format("%,d", souls);
    }

    // ── Config loading ────────────────────────────────────────────────────────

    private void loadConfig() {
        var cfg = plugin.getConfig();

        defaultMax            = cfg.getDouble("mana.default-max",             100.0);
        defaultRegen          = cfg.getDouble("mana.default-regen",             2.0);
        regenIntervalTicks    = cfg.getLong(  "mana.regen-interval-ticks",      20L);
        actionBarRefreshTicks = cfg.getLong(  "mana.action-bar-refresh-ticks",  10L);

        barLength      = cfg.getInt(   "mana.action-bar.bar-length",       24);
        barFilled      = cfg.getString("mana.action-bar.bar-filled-char",  "\u2588").charAt(0);
        barEmpty       = cfg.getString("mana.action-bar.bar-empty-char",   "\u2591").charAt(0);
        barFilledColor = validHex(cfg.getString("mana.action-bar.bar-filled-color", "#5599FF"), "#5599FF");
        barEmptyColor  = validHex(cfg.getString("mana.action-bar.bar-empty-color",  "#1a2a44"), "#1a2a44");
        manaLabelColor = validHex(cfg.getString("mana.action-bar.label-color",      "#88aaff"), "#88aaff");
        manaValueColor = validHex(cfg.getString("mana.action-bar.value-color",      "#cce0ff"), "#cce0ff");

        soulsLabelColor = validHex(cfg.getString("souls.action-bar.label-color", "#cc88ff"), "#cc88ff");
        soulsValueColor = validHex(cfg.getString("souls.action-bar.value-color", "#eeccff"), "#eeccff");

        // Build the centering padding from config
        int padSpaces = cfg.getInt("mana.action-bar.padding-spaces", 12);
        padding = " ".repeat(Math.max(0, padSpaces));
    }

    private String validHex(String hex, String fallback) {
        try {
            if (hex == null || !hex.startsWith("#") || hex.length() != 7) return fallback;
            Integer.parseInt(hex.substring(1), 16);
            return hex;
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid hex color '" + hex + "' — using " + fallback);
            return fallback;
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public double getDefaultMax()   { return defaultMax;   }
    public double getDefaultRegen() { return defaultRegen; }
}
