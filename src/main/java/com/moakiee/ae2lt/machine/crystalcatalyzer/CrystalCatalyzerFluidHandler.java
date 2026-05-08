package com.moakiee.ae2lt.machine.crystalcatalyzer;

import java.util.Objects;

import com.moakiee.ae2lt.machine.overloadfactory.NotifyingFluidTank;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Capability-facing fluid handler. Only exposes the single 16 B input tank,
 * and forbids extraction — drainage happens internally during recipe completion.
 */
public final class CrystalCatalyzerFluidHandler implements ResourceHandler<FluidResource> {
    private final NotifyingFluidTank tank;

    public CrystalCatalyzerFluidHandler(NotifyingFluidTank tank) {
        this.tank = Objects.requireNonNull(tank, "tank");
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        validateIndex(index);
        return tank.getResource(0);
    }

    @Override
    public long getAmountAsLong(int index) {
        validateIndex(index);
        return tank.getAmountAsLong(0);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        validateIndex(index);
        return tank.getCapacityAsLong(0, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        validateIndex(index);
        return tank.isValid(0, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        validateIndex(index);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return tank.insert(0, resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        validateIndex(index);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return 0;
    }

    private static void validateIndex(int index) {
        if (index != 0) {
            throw new IndexOutOfBoundsException(index);
        }
    }
}
