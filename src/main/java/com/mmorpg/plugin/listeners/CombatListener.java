package com.mmorpg.plugin.listeners;

import com.mmorpg.plugin.core.MMORPGPlugin;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.data.PlayerData;
import com.mmorpg.plugin.systems.hud.HudSystem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * CombatListener — intercepts all damage to players and routes it through
 * the custom health system instead of vanilla hearts.
 *
 * Pipeline for incoming damage:
 *   1. Cancel the vanilla damage event (so hearts never move).
 *   2. Pass raw damage to PlayerData.applyDamage() which applies armour reduction.
 *   3. Refresh the HUD so the health bar updates immediately.
 *   4. Handle death (custom health <= 0) with a message.
 *      Real death handling (respawn, soul loss, etc.) will be expanded later.
 */
public class CombatListener implements Listener {

    private final MMORPGPlugin plugin;
    private final DataManager  dataManager;
    private final HudSystem    hudSystem;

    public CombatListener(MMORPGPlugin plugin,
                          DataManager dataManager,
                          HudSystem hudSystem) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
        this.hudSystem   = hudSystem;
    }

    /**
     * Intercepts ALL damage to players — from mobs, fall damage, fire, etc.
     * Cancels vanilla damage and applies it to custom health instead.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        PlayerData data = dataManager.get(player);
        if (data == null) return;

        // Cancel vanilla damage — we handle it ourselves
        event.setCancelled(true);

        double rawDamage = event.getFinalDamage();
        double dealtDamage = data.applyDamage(rawDamage);

        // Refresh HUD immediately so health bar drops visually
        hudSystem.refreshDisplay(player);

        // Handle death
        if (data.isDead()) {
            handleDeath(player, data);
        }
    }

    /**
     * Placeholder death handler.
     * For now: restore to 1 HP, send a message, and reset sprint state.
     * Full death system (respawn screen, soul penalty, etc.) comes later.
     */
    private void handleDeath(Player player, PlayerData data) {
        data.setHealth(1);
        data.setSprinting(false);
        player.setSprinting(false);
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "You have been defeated! (Death system coming soon)")
                .color(net.kyori.adventure.text.format.NamedTextColor.RED));
        hudSystem.refreshDisplay(player);
    }
}
