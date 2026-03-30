package com.mmorpg.plugin.systems.hud;

import com.mmorpg.plugin.core.MMORPGPlugin;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HudSystem — two-layer HUD display.
 *
 * ACTION BAR (above hotbar) — health, stamina, armour:
 *   ❤ ██████░░  450/500   ◈ ████░░░░  70/100   ⛨ ██████████  200/200
 *
 * BOSSBAR (top of screen) — mana bar fill + mana/souls in title:
 *   [████████████░░░░░░░░]
 *    ✦ 80/100  mana    ◆ 4,231 souls
 *
 * Three regen loops:
 *   manaRegenTask   — 1s  — regen mana, refresh both displays
 *   healthRegenTask — 4s  — regen health only
 *   staminaTask     — 1s  — drain/regen stamina based on sprint state
 *   displayTask     — 0.5s — refresh action bar so it never disappears
 */
public class HudSystem {

    private final MMORPGPlugin plugin;
    private final DataManager  dataManager;

    // ── Tasks ─────────────────────────────────────────────────────────────────
    private BukkitTask manaRegenTask;
    private BukkitTask healthRegenTask;
    private BukkitTask staminaTask;
    private BukkitTask displayTask;

    // ── Per-player BossBars ───────────────────────────────────────────────────
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    // ── Config ────────────────────────────────────────────────────────────────
    private long manaRegenTicks;
    private long healthRegenTicks;
    private long staminaTickTicks;
    private long displayRefreshTicks;

    // Action bar colors
    private String healthFilledColor;
    private String healthEmptyColor;
    private String healthLabelColor;
    private String staminaFilledColor;
    private String staminaEmptyColor;
    private String staminaLabelColor;
    private String armourFilledColor;
    private String armourEmptyColor;
    private String armourLabelColor;
    private String separatorColor;

    // BossBar colors
    private String manaFilledColor;
    private String manaEmptyColor;
    private String manaLabelColor;
    private String manaValueColor;
    private String soulsLabelColor;
    private String soulsValueColor;

    // Bar sizing
    private int  statBarLength;
    private int  manaBarLength;
    private char filled;
    private char empty;
    private String padding;

    // ── Constructor ───────────────────────────────────────────────────────────

    public HudSystem(MMORPGPlugin plugin, DataManager dataManager) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
        loadConfig();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            freezeVanillaHealth(p);
            PlayerData d = dataManager.get(p);
            if (d != null) createBossBar(p, d);
        }

        manaRegenTask   = schedule(this::tickMana,    manaRegenTicks);
        healthRegenTask = schedule(this::tickHealth,   healthRegenTicks);
        staminaTask     = schedule(this::tickStamina,  staminaTickTicks);

        if (displayRefreshTicks < manaRegenTicks) {
            displayTask = schedule(this::tickDisplay, displayRefreshTicks);
        }

        plugin.getLogger().info("HudSystem started.");
    }

    public void stop() {
        cancel(manaRegenTask);
        cancel(healthRegenTask);
        cancel(staminaTask);
        cancel(displayTask);
        manaRegenTask = healthRegenTask = staminaTask = displayTask = null;

        // Clear action bars and remove all boss bars
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(Component.empty());
        }
        removeAllBossBars();
    }

    public void reloadConfig() { stop(); loadConfig(); start(); }

    // ── Player hooks ──────────────────────────────────────────────────────────

    public void onPlayerJoin(Player player, PlayerData data) {
        freezeVanillaHealth(player);
        createBossBar(player, data);
        sendActionBar(player, data);
    }

    public void onPlayerQuit(Player player) {
        player.sendActionBar(Component.empty());
        removeBossBar(player);
    }

    public void freezeVanillaHealth(Player player) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(20.0);
        player.setHealth(20.0);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void refreshDisplay(Player player) {
        PlayerData data = dataManager.get(player);
        if (data == null) return;
        sendActionBar(player, data);
        updateBossBar(player, data);
    }

    // ── Tick handlers ─────────────────────────────────────────────────────────

    private void tickMana() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = dataManager.get(p);
            if (d == null) continue;
            d.regenMana(d.getManaRegen());
            sendActionBar(p, d);
            updateBossBar(p, d);
        }
    }

    private void tickHealth() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = dataManager.get(p);
            if (d == null) continue;
            if (d.getHealth() < d.getMaxHealth()) d.regenHealth(d.getHealthRegen());
        }
    }

    private void tickStamina() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = dataManager.get(p);
            if (d == null) continue;
            if (d.isSprinting()) {
                d.drainStaminaSprint();
                if (d.getStamina() <= 0) {
                    p.setSprinting(false);
                    d.setSprinting(false);
                }
            } else {
                d.regenStamina(d.getStaminaRegen());
            }
        }
    }

    private void tickDisplay() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = dataManager.get(p);
            if (d == null) continue;
            sendActionBar(p, d);
        }
    }

    // ── Action bar (health / stamina / armour) ────────────────────────────────

    private void sendActionBar(Player player, PlayerData data) {
        player.sendActionBar(buildActionBar(data));
    }

    /**
     * Action bar layout:
     *   ❤ ██████░░  450/500   ◈ ████░░░░  70/100   ⛨ ██████████  200/200
     */
    private Component buildActionBar(PlayerData d) {
        Component div = txt("   ", separatorColor);

        Component health = txt("\u2764 ", healthLabelColor)
                .append(visualBar(d.getHealthFraction(), statBarLength, healthFilledColor, healthEmptyColor))
                .append(txt("  " + (int) d.getHealth() + "/" + (int) d.getMaxHealth(), healthLabelColor));

        Component stamina = txt("\u25C8 ", staminaLabelColor)
                .append(visualBar(d.getStaminaFraction(), statBarLength, staminaFilledColor, staminaEmptyColor))
                .append(txt("  " + (int) d.getStamina() + "/" + (int) d.getMaxStamina(), staminaLabelColor));

        Component armour = txt("\u26E8 ", armourLabelColor)
                .append(visualBar((float)(d.getArmour() / d.getMaxArmour()), statBarLength, armourFilledColor, armourEmptyColor))
                .append(txt("  " + (int) d.getArmour() + "/" + (int) d.getMaxArmour(), armourLabelColor));

        return Component.text(padding).decoration(TextDecoration.ITALIC, false)
                .append(health).append(div)
                .append(stamina).append(div)
                .append(armour);
    }

    // ── BossBar (mana / souls) ────────────────────────────────────────────────

    private void createBossBar(Player player, PlayerData data) {
        removeBossBar(player);
        BossBar bar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SEGMENTED_20);
        bar.setProgress(data.getManaFraction());
        bar.setTitle(buildBossBarTitle(data));
        bar.addPlayer(player);
        bar.setVisible(true);
        bossBars.put(player.getUniqueId(), bar);
    }

    private void updateBossBar(Player player, PlayerData data) {
        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) { createBossBar(player, data); return; }
        bar.setProgress(Math.max(0, Math.min(1, data.getManaFraction())));
        bar.setTitle(buildBossBarTitle(data));
    }

    private void removeBossBar(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) { bar.removePlayer(player); bar.setVisible(false); }
    }

    private void removeAllBossBars() {
        for (var entry : bossBars.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) entry.getValue().removePlayer(p);
            entry.getValue().setVisible(false);
        }
        bossBars.clear();
    }

    /**
     * BossBar title:
     *   ✦ Mana  80 / 100     ◆ Souls  4,231
     */
    private String buildBossBarTitle(PlayerData data) {
        return "\u2726 Mana  " + (int) data.getMana() + " / " + (int) data.getMaxMana()
                + "          "
                + "\u25C6 Souls  " + String.format("%,d", data.getSouls());
    }

    // ── Shared rendering helpers ──────────────────────────────────────────────

    private Component visualBar(float fraction, int length, String filledHex, String emptyHex) {
        int f = Math.round(Math.max(0, Math.min(1, fraction)) * length);
        int e = length - f;
        Component bar = txt(String.valueOf(filled).repeat(Math.max(0, f)), filledHex);
        if (e > 0) bar = bar.append(txt(String.valueOf(empty).repeat(e), emptyHex));
        return bar;
    }

    private Component txt(String text, String hex) {
        return Component.text(text)
                .color(TextColor.fromHexString(hex))
                .decoration(TextDecoration.ITALIC, false);
    }

    // ── Config ────────────────────────────────────────────────────────────────

    private void loadConfig() {
        var c = plugin.getConfig();

        manaRegenTicks      = c.getLong("mana.regen-interval-ticks",        20L);
        healthRegenTicks    = c.getLong("health.regen-interval-ticks",       80L);
        staminaTickTicks    = c.getLong("stamina.tick-interval-ticks",       20L);
        displayRefreshTicks = c.getLong("mana.action-bar-refresh-ticks",     10L);

        statBarLength = c.getInt("hud.stat-bar-length", 8);
        manaBarLength = c.getInt("hud.mana-bar-length", 20);
        filled        = c.getString("hud.bar-filled-char", "\u2588").charAt(0);
        empty         = c.getString("hud.bar-empty-char",  "\u2591").charAt(0);
        padding       = " ".repeat(Math.max(0, c.getInt("hud.padding-spaces", 0)));

        separatorColor     = hex(c.getString("hud.separator-color",        "#2a2a3a"));

        healthFilledColor  = hex(c.getString("health.color.filled",         "#ff4444"));
        healthEmptyColor   = hex(c.getString("health.color.empty",          "#3a1111"));
        healthLabelColor   = hex(c.getString("health.color.label",          "#ff8888"));

        staminaFilledColor = hex(c.getString("stamina.color.filled",        "#44cc44"));
        staminaEmptyColor  = hex(c.getString("stamina.color.empty",         "#113311"));
        staminaLabelColor  = hex(c.getString("stamina.color.label",         "#88ee88"));

        armourFilledColor  = hex(c.getString("armour.color.filled",         "#aaaacc"));
        armourEmptyColor   = hex(c.getString("armour.color.empty",          "#222233"));
        armourLabelColor   = hex(c.getString("armour.color.label",          "#ccccee"));

        manaFilledColor    = hex(c.getString("mana.bossbar.filled-color",   "#5599FF"));
        manaEmptyColor     = hex(c.getString("mana.bossbar.empty-color",    "#1a2a44"));
        manaLabelColor     = hex(c.getString("mana.bossbar.label-color",    "#88aaff"));
        manaValueColor     = hex(c.getString("mana.bossbar.value-color",    "#cce0ff"));

        soulsLabelColor    = hex(c.getString("souls.bossbar.label-color",   "#cc88ff"));
        soulsValueColor    = hex(c.getString("souls.bossbar.value-color",   "#eeccff"));
    }

    private String hex(String raw) {
        if (raw != null && raw.startsWith("#") && raw.length() == 7) {
            try { Integer.parseInt(raw.substring(1), 16); return raw; } catch (NumberFormatException ignored) {}
        }
        plugin.getLogger().warning("Bad hex color '" + raw + "' — using #ffffff");
        return "#ffffff";
    }

    private BukkitTask schedule(Runnable r, long ticks) {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, r, ticks, ticks);
    }

    private void cancel(BukkitTask t) { if (t != null) t.cancel(); }
}
