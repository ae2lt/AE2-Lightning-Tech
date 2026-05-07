package com.moakiee.ae2lt.logic.energy;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/**
 * Centralised AE-power accounting used by the Overloaded ME Interface and the
 * Overloaded Pattern Provider. The model mirrors AE2's vanilla I/O bus
 * pricing — one logical operation costs {@link #AE_PER_OPERATION} AE, where
 * the operation count is {@code ceil(amount / key.getAmountPerOperation())}.
 *
 * <p>Cost is dimension-agnostic by design: cross-dimension transfers cost the
 * same as same-dimension transfers.
 */
public final class PowerCostUtil {

    /** AE charged for each logical transfer operation (1 item / 125 mB / etc.). */
    public static final double AE_PER_OPERATION = 1.0;

    private PowerCostUtil() {
    }

    public static double cost(AEKey key, long amount) {
        if (key == null || amount <= 0) {
            return 0.0;
        }
        long perOp = Math.max(1L, key.getAmountPerOperation());
        // ceilDiv handles amount near Long.MAX_VALUE without (amount + perOp - 1) overflow.
        long ops = Math.ceilDiv(amount, perOp);
        return ops * AE_PER_OPERATION;
    }

    public static double totalCost(KeyCounter[] inputs) {
        if (inputs == null) {
            return 0.0;
        }
        double total = 0.0;
        for (var counter : inputs) {
            if (counter == null) continue;
            for (var entry : counter) {
                total += cost(entry.getKey(), entry.getLongValue());
            }
        }
        return total;
    }

    /**
     * Cap {@code requested} to the largest amount whose AE cost the grid can
     * still pay. Returns 0 when the grid is unavailable or cannot afford a
     * single operation. Uses SIMULATE — caller is responsible for actually
     * consuming the power via {@link #consume} after the transfer succeeds.
     */
    public static long maxAffordable(@Nullable IGrid grid, AEKey key, long requested) {
        if (grid == null || key == null || requested <= 0) {
            return 0;
        }
        double need = cost(key, requested);
        if (need <= 0.0) {
            return requested;
        }
        double available = grid.getEnergyService()
                .extractAEPower(need, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (available + 1.0e-6 >= need) {
            return requested;
        }
        long perOp = Math.max(1L, key.getAmountPerOperation());
        long affordableOps = (long) Math.floor(available / AE_PER_OPERATION);
        if (affordableOps <= 0) {
            return 0;
        }
        long capped;
        try {
            capped = Math.multiplyExact(affordableOps, perOp);
        } catch (ArithmeticException overflow) {
            capped = Long.MAX_VALUE;
        }
        return Math.max(0, Math.min(requested, capped));
    }

    /**
     * Drain the AE corresponding to {@code amount} of {@code key}. No-op when
     * grid is null or amount is non-positive. Caller must already have capped
     * {@code amount} via {@link #maxAffordable} so the network is guaranteed
     * to have at least this much power.
     */
    public static void consume(@Nullable IGrid grid, AEKey key, long amount) {
        if (grid == null || key == null || amount <= 0) {
            return;
        }
        double need = cost(key, amount);
        if (need <= 0.0) {
            return;
        }
        grid.getEnergyService().extractAEPower(need, Actionable.MODULATE, PowerMultiplier.CONFIG);
    }

    /**
     * SIMULATE-check whether {@code grid} can cover {@code need} AE right now.
     * Used by pattern push paths that need an all-or-nothing decision before
     * committing to an external machine call.
     */
    public static boolean canAfford(@Nullable IGrid grid, double need) {
        if (need <= 0.0) {
            return true;
        }
        if (grid == null) {
            return false;
        }
        double available = grid.getEnergyService()
                .extractAEPower(need, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return available + 1.0e-6 >= need;
    }

    /**
     * Drain a precomputed AE cost (used after {@link #canAfford} returned true).
     */
    public static void consumeRaw(@Nullable IGrid grid, double need) {
        if (grid == null || need <= 0.0) {
            return;
        }
        grid.getEnergyService().extractAEPower(need, Actionable.MODULATE, PowerMultiplier.CONFIG);
    }
}
