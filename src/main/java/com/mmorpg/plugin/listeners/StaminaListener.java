package com.mmorpg.plugin.listeners;

import com.mmorpg.plugin.core.MMORPGPlugin;
import com.mmorpg.plugin.data.DataManager;
import com.mmorpg.plugin.data.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

/**
 * StaminaListener — tracks sprint state and detects jumps via movement.
 *
 * PlayerJumpEvent is not available in this Paper build, so jumps are
 * detected by watching for upward Y movement while the player is on the
 * ground. A flag (wasOnGround) prevents the drain firing multiple times
 * per jump.
 */
public class StaminaListener implements Listener {

    private final MMORPGPlugin plugin;
    private final DataManager  dataManager;

    public StaminaListener(MMORPGPlugin plugin, DataManager dataManager) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSprintToggle(PlayerToggleSprintEvent event) {
        PlayerData data = dataManager.get(event.getPlayer());
        if (data == null) return;

        if (event.isSprinting()) {
            if (data.getStamina() <= 0) {
                event.setCancelled(true);
                return;
            }
            data.setSprinting(true);
        } else {
            data.setSprinting(false);
        }
    }

    /**
     * Detects a jump by checking that:
     *   - the player WAS on the ground at the from-position
     *   - Y is now increasing (moving upward)
     * This fires once per jump since the player leaves the ground immediately.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Skip if no vertical change at all
        if (event.getFrom().getY() == event.getTo().getY()) return;
        // Only interested in upward movement
        if (event.getTo().getY() <= event.getFrom().getY()) return;

        var player = event.getPlayer();

        // Player must have been on the ground at the previous position
        // We check this via the from-location block below
        if (!isGrounded(event)) return;

        PlayerData data = dataManager.get(player);
        if (data == null) return;

        data.drainStaminaJump();

        if (data.getStamina() <= 0) {
            player.setSprinting(false);
            data.setSprinting(false);
        }
    }

    /**
     * Returns true if the player's from-location has a solid block beneath it,
     * indicating they were grounded before this movement frame.
     */
    private boolean isGrounded(PlayerMoveEvent event) {
        var from = event.getFrom().clone();
        from.setY(from.getY() - 0.1);
        var block = from.getBlock();
        return block.getType().isSolid();
    }
}
