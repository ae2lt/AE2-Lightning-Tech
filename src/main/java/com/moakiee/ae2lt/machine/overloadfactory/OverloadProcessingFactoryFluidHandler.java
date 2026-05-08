package com.moakiee.ae2lt.machine.overloadfactory;

import java.util.Objects;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class OverloadProcessingFactoryFluidHandler implements ResourceHandler<FluidResource> {
    private final NotifyingFluidTank inputTank;
    private final NotifyingFluidTank outputTank;

    public OverloadProcessingFactoryFluidHandler(NotifyingFluidTank inputTank, NotifyingFluidTank outputTank) {
        this.inputTank = Objects.requireNonNull(inputTank, "inputTank");
        this.outputTank = Objects.requireNonNull(outputTank, "outputTank");
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public FluidResource getResource(int index) {
        return tank(index).getResource(0);
    }

    @Override
    public long getAmountAsLong(int index) {
        return tank(index).getAmountAsLong(0);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        if (index == 1 && !resource.isEmpty()) {
            return 0;
        }
        return tank(index).getCapacityAsLong(0, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return index == 0 && inputTank.isValid(0, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (index != 0) {
            return 0;
        }
        return inputTank.insert(0, resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (index != 1) {
            return 0;
        }
        return outputTank.extract(0, resource, amount, transaction);
    }

    private NotifyingFluidTank tank(int index) {
        return switch (index) {
            case 0 -> inputTank;
            case 1 -> outputTank;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }
}
