package com.mmorpg.plugin.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PlayerData — single source of truth for one online player's MMORPG state.
 *
 * Current fields: mana, health, stamina, armour, souls.
 * Race, class, abilities, XP, level added in later sprints.
 */
public class PlayerData {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final UUID playerUUID;

    // ── Mana ──────────────────────────────────────────────────────────────────
    private double mana;
    private double maxMana;
    private double manaRegen;       // per regen tick

    // ── Health ────────────────────────────────────────────────────────────────
    private double health;
    private double maxHealth;
    private double healthRegen;     // per health-regen tick (slower than mana)

    // ── Stamina ───────────────────────────────────────────────────────────────
    private double stamina;
    private double maxStamina;
    private double staminaRegen;    // per regen tick when not sprinting
    private double sprintDrain;     // stamina lost per sprint tick
    private double jumpDrain;       // stamina lost per jump

    // ── Armour ────────────────────────────────────────────────────────────────
    // Flat value — reduces incoming damage. No regen; restored by items/abilities.
    private double armour;
    private double maxArmour;

    // ── Souls ─────────────────────────────────────────────────────────────────
    private long souls = 0;

    // ── Runtime state ─────────────────────────────────────────────────────────
    /** True while the player is actively sprinting — set by StaminaListener. */
    private boolean sprinting = false;

    /** Ability cooldown expiry timestamps: abilityId → System.currentTimeMillis() expiry. */
    private final Map<String, Long> cooldowns = new HashMap<>();

    // ── Dirty flag ────────────────────────────────────────────────────────────
    private boolean dirty = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PlayerData(UUID playerUUID,
                      double maxMana,    double manaRegen,
                      double maxHealth,  double healthRegen,
                      double maxStamina, double staminaRegen,
                      double sprintDrain, double jumpDrain,
                      double maxArmour) {
        this.playerUUID   = playerUUID;

        this.maxMana      = maxMana;
        this.mana         = maxMana;
        this.manaRegen    = manaRegen;

        this.maxHealth    = maxHealth;
        this.health       = maxHealth;
        this.healthRegen  = healthRegen;

        this.maxStamina   = maxStamina;
        this.stamina      = maxStamina;
        this.staminaRegen = staminaRegen;
        this.sprintDrain  = sprintDrain;
        this.jumpDrain    = jumpDrain;

        this.maxArmour    = maxArmour;
        this.armour       = maxArmour;
    }

    // ── Mana ─────────────────────────────────────────────────────────────────

    public void regenMana(double amount) {
        this.mana = Math.min(maxMana, mana + amount);
    }

    public boolean consumeMana(double cost) {
        if (mana < cost) return false;
        mana -= cost;
        return true;
    }

    public void setMana(double v) { this.mana = clamp(v, 0, maxMana); }
    public void fillMana()        { this.mana = maxMana; }

    public float getManaFraction() { return fraction(mana, maxMana); }

    // ── Health ────────────────────────────────────────────────────────────────

    public void regenHealth(double amount) {
        this.health = Math.min(maxHealth, health + amount);
    }

    /**
     * Applies incoming damage after armour reduction.
     * Armour absorbs damage first; remainder hits health.
     * Returns the actual health damage dealt (after reduction).
     */
    public double applyDamage(double rawDamage) {
        double absorbed = Math.min(armour, rawDamage * 0.5);
        double remaining = rawDamage - absorbed;
        this.health = Math.max(0, health - remaining);
        markDirty();
        return remaining;
    }

    public void setHealth(double v) { this.health = clamp(v, 0, maxHealth); markDirty(); }
    public void fillHealth()        { this.health = maxHealth; markDirty(); }

    public boolean isDead()          { return health <= 0; }
    public float getHealthFraction() { return fraction(health, maxHealth); }

    // ── Stamina ───────────────────────────────────────────────────────────────

    public void regenStamina(double amount) {
        this.stamina = Math.min(maxStamina, stamina + amount);
    }

    public void drainStaminaSprint() {
        this.stamina = Math.max(0, stamina - sprintDrain);
    }

    public void drainStaminaJump() {
        this.stamina = Math.max(0, stamina - jumpDrain);
    }

    public boolean consumeStamina(double cost) {
        if (stamina < cost) return false;
        stamina -= cost;
        return true;
    }

    public void setStamina(double v) { this.stamina = clamp(v, 0, maxStamina); }
    public void fillStamina()        { this.stamina = maxStamina; }

    public float getStaminaFraction() { return fraction(stamina, maxStamina); }

    // ── Armour ────────────────────────────────────────────────────────────────

    public void setArmour(double v) { this.armour = clamp(v, 0, maxArmour); markDirty(); }

    // ── Souls ─────────────────────────────────────────────────────────────────

    public void addSouls(long amount) { this.souls += Math.max(0, amount); markDirty(); }
    public void setSouls(long amount) { this.souls = Math.max(0, amount);  markDirty(); }

    // ── Dirty flag ────────────────────────────────────────────────────────────

    public void markDirty()  { this.dirty = true; }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { this.dirty = false; }

    // ── Runtime state ─────────────────────────────────────────────────────────

    public boolean isSprinting()          { return sprinting; }
    public void setSprinting(boolean s)   { this.sprinting = s; }

    /** Returns the expiry timestamp for an ability cooldown (0 if not on cooldown). */
    public long getCooldown(String abilityId) {
        return cooldowns.getOrDefault(abilityId, 0L);
    }

    /** Stamps a cooldown expiry time for an ability. */
    public void setCooldown(String abilityId, long expiryMs) {
        cooldowns.put(abilityId, expiryMs);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private float fraction(double current, double max) {
        if (max <= 0) return 0f;
        return (float) Math.max(0, Math.min(1, current / max));
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID   getPlayerUUID()  { return playerUUID; }

    public double getMana()        { return mana; }
    public double getMaxMana()     { return maxMana; }
    public void   setMaxMana(double v) {
        this.maxMana = Math.max(0, v);
        this.mana    = Math.min(mana, maxMana);
        markDirty();
    }
    public double getManaRegen()   { return manaRegen; }
    public void   setManaRegen(double v) { this.manaRegen = Math.max(0, v); markDirty(); }

    public double getHealth()      { return health; }
    public double getMaxHealth()   { return maxHealth; }
    public void   setMaxHealth(double v) {
        this.maxHealth = Math.max(0, v);
        this.health    = Math.min(health, maxHealth);
        markDirty();
    }
    public double getHealthRegen() { return healthRegen; }
    public void   setHealthRegen(double v) { this.healthRegen = Math.max(0, v); markDirty(); }

    public double getStamina()     { return stamina; }
    public double getMaxStamina()  { return maxStamina; }
    public void   setMaxStamina(double v) {
        this.maxStamina = Math.max(0, v);
        this.stamina    = Math.min(stamina, maxStamina);
        markDirty();
    }
    public double getStaminaRegen()  { return staminaRegen; }
    public void   setStaminaRegen(double v) { this.staminaRegen = Math.max(0, v); markDirty(); }
    public double getSprintDrain()   { return sprintDrain; }
    public double getJumpDrain()     { return jumpDrain; }

    public double getArmour()      { return armour; }
    public double getMaxArmour()   { return maxArmour; }
    public void   setMaxArmour(double v) {
        this.maxArmour = Math.max(0, v);
        this.armour    = Math.min(armour, maxArmour);
        markDirty();
    }

    public long   getSouls()       { return souls; }
}
