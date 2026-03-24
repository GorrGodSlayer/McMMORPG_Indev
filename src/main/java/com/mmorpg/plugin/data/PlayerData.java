package com.mmorpg.plugin.data;

import java.util.UUID;

/**
 * PlayerData — the single source of truth for one online player's MMORPG state.
 *
 * Sprint 1 scope: mana + souls.
 * Race, class, abilities, XP, level will be added in later sprints.
 */
public class PlayerData {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final UUID playerUUID;

    // ── Mana ──────────────────────────────────────────────────────────────────
    private double mana;
    private double maxMana;
    private double manaRegen;

    // ── Souls ─────────────────────────────────────────────────────────────────
    // Souls are collected by killing mobs. They persist between sessions.
    // No maximum cap at this stage — that will come with class/rank systems.
    private long souls = 0;

    // ── Dirty flag ────────────────────────────────────────────────────────────
    private boolean dirty = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PlayerData(UUID playerUUID, double maxMana, double manaRegen) {
        this.playerUUID = playerUUID;
        this.maxMana    = maxMana;
        this.mana       = maxMana;
        this.manaRegen  = manaRegen;
    }

    // ── Mana operations ───────────────────────────────────────────────────────

    /** Adds mana from regen tick — does NOT mark dirty. */
    public void regenMana(double amount) {
        this.mana = Math.min(maxMana, mana + amount);
    }

    /** Deducts mana for an ability cast. Returns false if insufficient. */
    public boolean consumeMana(double cost) {
        if (mana < cost) return false;
        mana -= cost;
        return true;
    }

    /** Sets mana directly, clamped 0 ↔ maxMana. */
    public void setMana(double value) {
        this.mana = Math.max(0, Math.min(maxMana, value));
    }

    /** Instantly fills mana to max. */
    public void fillMana() {
        this.mana = maxMana;
    }

    /** Returns mana as a 0.0–1.0 fraction for display purposes. */
    public float getManaFraction() {
        if (maxMana <= 0) return 0f;
        return (float) Math.max(0, Math.min(1, mana / maxMana));
    }

    // ── Souls operations ──────────────────────────────────────────────────────

    /**
     * Adds souls from a mob kill.
     * Marks dirty so the new total is persisted on next autosave.
     */
    public void addSouls(long amount) {
        this.souls += Math.max(0, amount);
        markDirty();
    }

    /** Directly sets the souls total (admin command use). */
    public void setSouls(long amount) {
        this.souls = Math.max(0, amount);
        markDirty();
    }

    // ── Dirty flag ────────────────────────────────────────────────────────────

    public void markDirty()  { this.dirty = true; }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { this.dirty = false; }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID   getPlayerUUID() { return playerUUID; }

    public double getMana()       { return mana; }

    public double getMaxMana()    { return maxMana; }
    public void   setMaxMana(double maxMana) {
        this.maxMana = Math.max(0, maxMana);
        this.mana    = Math.min(this.mana, this.maxMana);
        markDirty();
    }

    public double getManaRegen()  { return manaRegen; }
    public void   setManaRegen(double manaRegen) {
        this.manaRegen = Math.max(0, manaRegen);
        markDirty();
    }

    public long getSouls()        { return souls; }
}
