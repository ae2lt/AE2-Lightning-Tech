package com.moakiee.ae2lt.machine.overloadfactory;

import java.util.Objects;
import java.util.function.Predicate;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public final class NotifyingFluidTank extends FluidStacksResourceHandler {
    private final Predicate<FluidStack> validator;
    private final Runnable changeListener;

    public NotifyingFluidTank(int capacity, Runnable changeListener) {
        this(capacity, fluidStack -> true, changeListener);
    }

    public NotifyingFluidTank(int capacity, Predicate<FluidStack> validator, Runnable changeListener) {
        super(1, capacity);
        this.validator = Objects.requireNonNull(validator, "validator");
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return super.isValid(index, resource)
                && !resource.isEmpty()
                && validator.test(resource.toStack(1));
    }

    public boolean isFluidValid(FluidStack stack) {
        return !stack.isEmpty() && validator.test(stack);
    }

    public int getCapacity() {
        return capacity;
    }

    public FluidStack getFluid() {
        return stacks.getFirst();
    }

    public int getFluidAmount() {
        return getAmountAsInt(0);
    }

    public int insertFluid(FluidStack resource) {
        if (resource.isEmpty()) {
            return 0;
        }
        try (var transaction = Transaction.openRoot()) {
            int inserted = insert(0, FluidResource.of(resource), resource.getAmount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    public FluidStack extractFluid(FluidStack resource) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        try (var transaction = Transaction.openRoot()) {
            int extracted = extract(0, FluidResource.of(resource), resource.getAmount(), transaction);
            transaction.commit();
            return extracted > 0 ? resource.copyWithAmount(extracted) : FluidStack.EMPTY;
        }
    }

    public FluidStack extractFluid(int maxDrain) {
        if (maxDrain <= 0 || getFluid().isEmpty()) {
            return FluidStack.EMPTY;
        }
        return extractFluid(getFluid().copyWithAmount(Math.min(maxDrain, getFluidAmount())));
    }

    public void setFluid(FluidStack stack) {
        if (stack.isEmpty()) {
            set(0, FluidResource.EMPTY, 0);
        } else {
            set(0, FluidResource.of(stack), Math.min(stack.getAmount(), capacity));
        }
    }

    public boolean isEmpty() {
        return getFluid().isEmpty();
    }

    public int getSpace() {
        return Math.max(0, capacity - getFluidAmount());
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        changeListener.run();
    }
}
