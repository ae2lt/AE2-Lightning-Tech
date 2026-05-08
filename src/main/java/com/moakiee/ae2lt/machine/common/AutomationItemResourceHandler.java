package com.moakiee.ae2lt.machine.common;

import java.util.Objects;

import appeng.api.inventories.InternalInventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public abstract class AutomationItemResourceHandler implements ResourceHandler<ItemResource> {
    protected final InternalInventory inventory;
    private final ResourceHandler<ItemResource> delegate;

    protected AutomationItemResourceHandler(InternalInventory inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.delegate = inventory.toResourceHandler();
    }

    @Override
    public final int size() {
        return delegate.size();
    }

    @Override
    public final ItemResource getResource(int index) {
        return delegate.getResource(index);
    }

    @Override
    public final long getAmountAsLong(int index) {
        return delegate.getAmountAsLong(index);
    }

    @Override
    public final long getCapacityAsLong(int index, ItemResource resource) {
        validateSlotIndex(index);
        if (!resource.isEmpty() && !isValid(index, resource)) {
            return 0;
        }
        return delegate.getCapacityAsLong(index, resource);
    }

    @Override
    public final boolean isValid(int index, ItemResource resource) {
        validateSlotIndex(index);
        return !resource.isEmpty() && canInsert(index, resource.toStack());
    }

    @Override
    public final int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0 || !canInsert(index, resource.toStack())) {
            return 0;
        }
        return delegate.insert(index, resource, amount, transaction);
    }

    @Override
    public final int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0 || !canExtract(index, resource)) {
            return 0;
        }
        return delegate.extract(index, resource, amount, transaction);
    }

    protected boolean canInsert(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        return inventory.isItemValid(slot, stack);
    }

    protected boolean canExtract(int slot, ItemResource resource) {
        validateSlotIndex(slot);
        return false;
    }

    protected final void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= inventory.size()) {
            throw new IllegalArgumentException(
                    "Slot " + slot + " not in valid range - [0," + inventory.size() + ")");
        }
    }
}
