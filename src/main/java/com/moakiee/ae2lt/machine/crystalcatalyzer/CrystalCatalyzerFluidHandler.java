package com.moakiee.ae2lt.machine.crystalcatalyzer;

import java.util.Objects;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Capability-facing fluid handler. Only exposes the single 16 B input tank,
 * and forbids extraction — drainage happens internally during recipe completion.
 */
public final class CrystalCatalyzerFluidHandler implements IFluidHandler {
    private final com.moakiee.ae2lt.machine.overloadfactory.NotifyingFluidTank tank;

    public CrystalCatalyzerFluidHandler(com.moakiee.ae2lt.machine.overloadfactory.NotifyingFluidTank tank) {
        this.tank = Objects.requireNonNull(tank, "tank");
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tankIndex) {
        return tankIndex == 0 ? tank.getFluid() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tankIndex) {
        return tankIndex == 0 ? tank.getCapacity() : 0;
    }

    @Override
    public boolean isFluidValid(int tankIndex, FluidStack stack) {
        return tankIndex == 0 && tank.isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return tank.fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
