package com.mmorpg.plugin.abilities;

import com.mmorpg.plugin.data.PlayerData;
import com.mmorpg.plugin.systems.hud.HudSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * DemoAoeBurst — proof-of-concept ability.
 *
 * Effect:
 *   - Costs 30 mana and 20 stamina to cast.
 *   - 8-second cooldown (tracked in PlayerData.cooldowns — stubbed here
 *     since the full AbilityManager isn't built yet).
 *   - Spawns an expanding ring of particles at the caster's feet.
 *   - Deals 50 damage to all LivingEntities within 6 blocks (excluding caster).
 *   - Plays an impact sound on cast.
 *   - Sends feedback messages to the caster.
 *
 * This class is intentionally self-contained so it works as a standalone
 * proof of concept before the AbilityManager framework is built.
 */
public class DemoAoeBurst {

    // ── Ability constants ─────────────────────────────────────────────────────
    public static final double MANA_COST      = 30.0;
    public static final double STAMINA_COST   = 20.0;
    public static final double DAMAGE         = 50.0;
    public static final double RADIUS         = 6.0;
    public static final long   COOLDOWN_MS    = 8_000L;
    public static final String COOLDOWN_KEY   = "demo_aoe_burst";

    private final JavaPlugin plugin;
    private final HudSystem  hudSystem;

    public DemoAoeBurst(JavaPlugin plugin, HudSystem hudSystem) {
        this.plugin    = plugin;
        this.hudSystem = hudSystem;
    }

    /**
     * Attempts to cast the ability for the given player.
     * Returns true if the cast succeeded, false if it was blocked (no mana,
     * no stamina, or on cooldown) — the caller sends the appropriate message.
     */
    public boolean cast(Player caster, PlayerData data) {

        // ── Gate 1: cooldown ──────────────────────────────────────────────────
        long now = System.currentTimeMillis();
        long expiry = data.getCooldown(COOLDOWN_KEY);
        if (now < expiry) {
            long remaining = (expiry - now) / 1000 + 1;
            caster.sendMessage(Component.text(
                    "Ability on cooldown! " + remaining + "s remaining.")
                    .color(NamedTextColor.YELLOW));
            return false;
        }

        // ── Gate 2: mana ──────────────────────────────────────────────────────
        if (data.getMana() < MANA_COST) {
            caster.sendMessage(Component.text(
                    "Not enough mana! Need " + (int) MANA_COST
                    + ", have " + (int) data.getMana() + ".")
                    .color(NamedTextColor.AQUA));
            return false;
        }

        // ── Gate 3: stamina ───────────────────────────────────────────────────
        if (data.getStamina() < STAMINA_COST) {
            caster.sendMessage(Component.text(
                    "Not enough stamina! Need " + (int) STAMINA_COST
                    + ", have " + (int) data.getStamina() + ".")
                    .color(NamedTextColor.GREEN));
            return false;
        }

        // ── All gates passed — execute ────────────────────────────────────────
        data.consumeMana(MANA_COST);
        data.consumeStamina(STAMINA_COST);

        // Drain 25% of current health to show health regen in action
        double healthDrain = data.getHealth() * 0.25;
        data.setHealth(data.getHealth() - healthDrain);

        data.setCooldown(COOLDOWN_KEY, now + COOLDOWN_MS);

        executeEffect(caster, data, healthDrain);
        return true;
    }

    // ── Effect execution ──────────────────────────────────────────────────────

    private void executeEffect(Player caster, PlayerData casterData, double healthDrain) {
        Location origin = caster.getLocation();

        // ── Sound ─────────────────────────────────────────────────────────────
        caster.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
        caster.getWorld().playSound(origin, Sound.BLOCK_BEACON_ACTIVATE,  0.5f, 0.8f);

        // ── Particle ring — expands outward over 10 frames ────────────────────
        new BukkitRunnable() {
            int frame = 0;
            final int totalFrames = 10;

            @Override
            public void run() {
                if (frame >= totalFrames) { cancel(); return; }

                double radius = (RADIUS / totalFrames) * (frame + 1);
                int    points = 32;

                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI / points) * i;
                    double x = origin.getX() + radius * Math.cos(angle);
                    double z = origin.getZ() + radius * Math.sin(angle);
                    Location loc = new Location(origin.getWorld(), x, origin.getY() + 0.1, z);

                    // Inner ring: electric blue
                    origin.getWorld().spawnParticle(
                            Particle.DUST,
                            loc, 1,
                            new Particle.DustOptions(Color.fromRGB(85, 153, 255), 1.2f));

                    // Outer sparkle: white
                    if (frame == totalFrames - 1) {
                        origin.getWorld().spawnParticle(
                                Particle.DUST,
                                loc, 3, 0.2, 0.2, 0.2,
                                new Particle.DustOptions(Color.WHITE, 0.8f));
                    }
                }

                // Central upward burst on first frame
                if (frame == 0) {
                    origin.getWorld().spawnParticle(
                            Particle.EXPLOSION, origin, 1, 0, 0, 0, 0);
                }

                frame++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // ── Damage all LivingEntities in radius ───────────────────────────────
        List<Entity> nearby = caster.getNearbyEntities(RADIUS, RADIUS, RADIUS);
        int hit = 0;
        for (Entity entity : nearby) {
            if (entity == caster) continue;
            if (!(entity instanceof LivingEntity target)) continue;

            // Apply damage — if it's a player, go through their PlayerData
            // so armour reduction is applied. Otherwise use vanilla damage.
            if (entity instanceof Player targetPlayer) {
                // Will be routed through CombatListener's damage event
                targetPlayer.damage(DAMAGE, caster);
            } else {
                target.damage(DAMAGE, caster);
            }
            hit++;
        }

        // ── Feedback ──────────────────────────────────────────────────────────
        String hitMsg = hit == 0
                ? "No targets hit."
                : "Hit " + hit + " target" + (hit == 1 ? "" : "s") + " for " + (int) DAMAGE + " damage!";

        caster.sendMessage(
                Component.text("◆ AOE Burst! ").color(NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text(hitMsg).color(NamedTextColor.WHITE)));

        caster.sendMessage(
                Component.text("  Mana: -" + (int) MANA_COST
                        + "  Stamina: -" + (int) STAMINA_COST
                        + "  Health: -" + (int) healthDrain
                        + "  Cooldown: " + (COOLDOWN_MS / 1000) + "s")
                        .color(NamedTextColor.GRAY));

        // Refresh HUD immediately so mana/stamina bars drop visually
        hudSystem.refreshDisplay(caster);
    }
}
