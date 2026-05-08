package com.moakiee.ae2lt.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

@SuppressWarnings("removal")
public final class LegacyTransferAdapters {
    private LegacyTransferAdapters() {
    }

    public static ResourceHandler<ItemResource> itemHandler(IItemHandlerModifiable handler) {
        return new ItemHandlerAdapter(handler);
    }

    public static ResourceHandler<FluidResource> fluidHandler(IFluidHandler handler) {
        return new FluidHandlerAdapter(handler);
    }

    public static EnergyHandler energyHandler(IEnergyStorage storage) {
        return new EnergyStorageAdapter(storage);
    }

    public static EnergyHandler energyHandler(EnergyHandler storage) {
        return storage;
    }

    private static final class ItemHandlerAdapter extends SnapshotJournal<ItemStack[]>
            implements ResourceHandler<ItemResource> {
        private final IItemHandlerModifiable handler;

        private ItemHandlerAdapter(IItemHandlerModifiable handler) {
            this.handler = handler;
        }

        @Override
        public int size() {
            return handler.getSlots();
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.of(handler.getStackInSlot(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return handler.getStackInSlot(index).getCount();
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            if (!resource.isEmpty() && !isValid(index, resource)) {
                return 0;
            }
            return handler.getSlotLimit(index);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return !resource.isEmpty() && handler.isItemValid(index, resource.toStack());
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0) {
                return 0;
            }

            updateSnapshots(transaction);
            var remaining = handler.insertItem(index, resource.toStack(amount), false);
            return amount - remaining.getCount();
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || !resource.matches(handler.getStackInSlot(index))) {
                return 0;
            }

            updateSnapshots(transaction);
            return handler.extractItem(index, amount, false).getCount();
        }

        @Override
        protected ItemStack[] createSnapshot() {
            var stacks = new ItemStack[handler.getSlots()];
            for (int i = 0; i < stacks.length; i++) {
                stacks[i] = handler.getStackInSlot(i).copy();
            }
            return stacks;
        }

        @Override
        protected void revertToSnapshot(ItemStack[] snapshot) {
            for (int i = 0; i < snapshot.length; i++) {
                handler.setStackInSlot(i, snapshot[i].copy());
            }
        }
    }

    private static final class FluidHandlerAdapter implements ResourceHandler<FluidResource> {
        private final IFluidHandler handler;

        private FluidHandlerAdapter(IFluidHandler handler) {
            this.handler = handler;
        }

        @Override
        public int size() {
            return handler.getTanks();
        }

        @Override
        public FluidResource getResource(int index) {
            return FluidResource.of(handler.getFluidInTank(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return handler.getFluidInTank(index).getAmount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            if (!resource.isEmpty() && !isValid(index, resource)) {
                return 0;
            }
            return handler.getTankCapacity(index);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return !resource.isEmpty() && handler.isFluidValid(index, resource.toStack(1));
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || !isValid(index, resource)) {
                return 0;
            }
            return handler.fill(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || !resource.matches(handler.getFluidInTank(index))) {
                return 0;
            }
            FluidStack extracted = handler.drain(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
            return extracted.getAmount();
        }
    }

    private static final class EnergyStorageAdapter implements EnergyHandler {
        private final IEnergyStorage storage;

        private EnergyStorageAdapter(IEnergyStorage storage) {
            this.storage = storage;
        }

        @Override
        public long getAmountAsLong() {
            return storage.getEnergyStored();
        }

        @Override
        public long getCapacityAsLong() {
            return storage.getMaxEnergyStored();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return storage.receiveEnergy(amount, false);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return storage.extractEnergy(amount, false);
        }
    }
}
