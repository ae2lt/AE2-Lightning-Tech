package com.moakiee.ae2lt.celestweave;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.module.ResistanceSubmodule;

/**
 * Bills phase-shield damage by the highest covered hit in a fixed one-second window.
 *
 * <p>A later hit in the same window pays only the difference between the previous and new
 * high-water marks. Total FE and EHV charged by one window are independently capped at the first
 * undying trigger cost.
 */
public final class PhaseShieldChargeWindow {
    public static final int WINDOW_TICKS = 20;

    private static final String TAG_WINDOW_UNTIL = "PhaseShieldChargeUntil";
    private static final String TAG_COVERED_DAMAGE = "PhaseShieldCoveredDamage";

    private PhaseShieldChargeWindow() {
    }

    public static Quote quote(ItemStack armor, long gameTime, float preventedDamage) {
        return quote(readState(armor), gameTime, preventedDamage);
    }

    static Quote quote(State state, long gameTime, double preventedDamage) {
        State safeState = state == null ? State.EMPTY : state;
        double damage = normalizeDamage(preventedDamage);
        if (damage <= 0.0D) {
            return new Quote(0L, 0L, safeState);
        }

        boolean activeWindow = safeState.windowUntil() > gameTime;
        double previousCovered = activeWindow ? safeState.coveredDamage() : 0.0D;
        double nextCovered = Math.max(previousCovered, damage);
        long windowUntil = activeWindow
                ? safeState.windowUntil()
                : saturatingAdd(gameTime, WINDOW_TICKS);

        long previousFe = totalCost(
                previousCovered,
                ArmorOverloadRules.PHASE_SHIELD_ACTIVE_COST_FE_PER_DAMAGE,
                ArmorOverloadRules.UNDYING_TRIGGER_COST_FE);
        long nextFe = totalCost(
                nextCovered,
                ArmorOverloadRules.PHASE_SHIELD_ACTIVE_COST_FE_PER_DAMAGE,
                ArmorOverloadRules.UNDYING_TRIGGER_COST_FE);
        long previousEhv = totalCost(
                previousCovered,
                1L,
                ArmorOverloadRules.UNDYING_TRIGGER_COST_EHV);
        long nextEhv = totalCost(
                nextCovered,
                1L,
                ArmorOverloadRules.UNDYING_TRIGGER_COST_EHV);

        return new Quote(
                Math.max(0L, nextFe - previousFe),
                Math.max(0L, nextEhv - previousEhv),
                new State(windowUntil, nextCovered));
    }

    public static void record(ItemStack armor, Quote quote) {
        if (armor == null || armor.isEmpty() || quote == null) {
            return;
        }
        CompoundTag data = CelestweaveArmorState.getSubmoduleData(armor, ResistanceSubmodule.T2);
        data.putLong(TAG_WINDOW_UNTIL, quote.nextState().windowUntil());
        data.putDouble(TAG_COVERED_DAMAGE, quote.nextState().coveredDamage());
        CelestweaveArmorState.setSubmoduleData(armor, ResistanceSubmodule.T2, data);
    }

    private static State readState(ItemStack armor) {
        if (armor == null || armor.isEmpty()) {
            return State.EMPTY;
        }
        CompoundTag data = CelestweaveArmorState.getSubmoduleData(armor, ResistanceSubmodule.T2);
        long windowUntil = data.contains(TAG_WINDOW_UNTIL, Tag.TAG_LONG)
                ? data.getLong(TAG_WINDOW_UNTIL)
                : Long.MIN_VALUE;
        double coveredDamage = data.contains(TAG_COVERED_DAMAGE, Tag.TAG_DOUBLE)
                ? data.getDouble(TAG_COVERED_DAMAGE)
                : 0.0D;
        return new State(windowUntil, coveredDamage);
    }

    private static long totalCost(double damage, long costPerDamage, long cap) {
        if (damage <= 0.0D || costPerDamage <= 0L || cap <= 0L) {
            return 0L;
        }
        double rawCost = damage * costPerDamage;
        if (!Double.isFinite(rawCost) || rawCost >= cap) {
            return cap;
        }
        return Math.min(cap, (long) Math.ceil(rawCost));
    }

    private static double normalizeDamage(double damage) {
        if (Double.isNaN(damage) || damage <= 0.0D) {
            return 0.0D;
        }
        return Math.min(Float.MAX_VALUE, damage);
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    record State(long windowUntil, double coveredDamage) {
        static final State EMPTY = new State(Long.MIN_VALUE, 0.0D);

        State {
            coveredDamage = normalizeDamage(coveredDamage);
        }
    }

    public record Quote(long feCost, long ehvCost, State nextState) {
        public Quote {
            feCost = Math.max(0L, feCost);
            ehvCost = Math.max(0L, ehvCost);
            nextState = nextState == null ? State.EMPTY : nextState;
        }
    }
}
