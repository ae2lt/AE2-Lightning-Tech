package com.moakiee.ae2lt.logic.energy;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/**
 * Centralised AE-power accounting used by the Overloaded ME Interface and the
 * Overloaded Pattern Provider. The model mirrors AE2's vanilla I/O bus
 * pricing — one logical operation costs {@link #AE_PER_OPERATION} AE. The
 * overloaded item/fluid channels use larger batches than vanilla AE2 because
 * these machines are intended for high-throughput I/O.
 *
 * <p>Cost is dimension-agnostic by design: cross-dimension transfers cost the
 * same as same-dimension transfers.
 */
public final class PowerCostUtil {

    /** AE charged for each logical transfer operation (4 items / 500 mB / etc.). */
    public static final double AE_PER_OPERATION = 1.0;

    /**
     * Idle drain kept untouched by bulk consumers. Leave enough for the current
     * tick's idle payment so active transfers cannot push the grid into AE2's
     * powered-off reboot delay.
     */
    private static final double RESERVE_TICKS = 1.0;

    private PowerCostUtil() {
    }

    /** AE amount bulk consumers must leave in the grid (see {@link #RESERVE_TICKS}). */
    public static double idleReserve(IGrid grid) {
        return idleReserveForIdlePowerUsage(grid.getEnergyService().getIdlePowerUsage());
    }

    private static double idleReserveForIdlePowerUsage(double idlePowerUsage) {
        return Math.max(0.0, idlePowerUsage) * RESERVE_TICKS;
    }

    public static double cost(AEKey key, long amount) {
        if (key == null || amount <= 0) {
            return 0.0;
        }
        return OverloadedIoCost.cost(amount, amountPerOperation(key)) * AE_PER_OPERATION;
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
        var energyService = grid.getEnergyService();
        double reserve = idleReserveForIdlePowerUsage(energyService.getIdlePowerUsage());
        double available = energyService
                .extractAEPower(need + reserve, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        double usable = available - reserve;
        if (usable + 1.0e-6 >= need) {
            return requested;
        }
        long affordableOps = (long) Math.floor(usable / AE_PER_OPERATION);
        if (affordableOps <= 0) {
            return 0;
        }
        return OverloadedIoCost.amountForOperations(requested, amountPerOperation(key), affordableOps);
    }

    private static long amountPerOperation(AEKey key) {
        if (AEItemKey.is(key)) {
            return OverloadedIoCost.ITEMS_PER_OPERATION;
        }
        if (AEFluidKey.is(key)) {
            return OverloadedIoCost.FLUID_PER_OPERATION;
        }
        return Math.max(1L, key.getAmountPerOperation());
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
        var energyService = grid.getEnergyService();
        double total = need + idleReserveForIdlePowerUsage(energyService.getIdlePowerUsage());
        double available = energyService
                .extractAEPower(total, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return available + 1.0e-6 >= total;
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
