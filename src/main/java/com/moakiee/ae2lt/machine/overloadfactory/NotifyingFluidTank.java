package com.moakiee.ae2lt.machine.overloadfactory;

import java.util.Objects;
import java.util.function.Predicate;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class NotifyingFluidTank extends FluidTank {
    private final Runnable changeListener;

    public NotifyingFluidTank(int capacity, Runnable changeListener) {
        this(capacity, fluidStack -> true, changeListener);
    }

    public NotifyingFluidTank(int capacity, Predicate<FluidStack> validator, Runnable changeListener) {
        super(capacity, validator);
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    @Override
    protected void onContentsChanged() {
        changeListener.run();
    }
}
