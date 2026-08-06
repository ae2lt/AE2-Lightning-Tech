package com.moakiee.ae2lt.machine.lightningchamber;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.inventories.InternalInventory;

/**
 * Item handler that supports slot limits larger than the carried stack's
 * vanilla max size. This is required for machines that internally store more
 * than 64 items in a single slot.
 *
 * <p>Do not replace this with a plain ItemStackHandler + getSlotLimit override.
 * NeoForge's default insert path still clamps to the inserted stack's own max
 * size, which means automation commonly stops at 64 even when the slot says
 * 1024.</p>
 */
public abstract class LargeStackItemHandler implements IItemHandlerModifiable, InternalInventory {
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_COUNT_INT = "CountInt";
    private static final String TAG_STACK = "Stack";

    private final NonNullList<ItemStack> stacks;
    @Nullable
    private final Runnable changeListener;

    protected LargeStackItemHandler(int size, @Nullable Runnable changeListener) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
        this.changeListener = changeListener;
    }

    @Override
    public final int getSlots() {
        return stacks.size();
    }

    @Override
    public abstract int getSlotLimit(int slot);

    @Override
    public final int size() {
        return stacks.size();
    }

    @Override
    public final ItemStack getStackInSlot(int slot) {
        validateSlotIndex(slot);
        return stacks.get(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        setStackInSlotInternal(slot, stack, true);
    }

    @Override
    public final void setItemDirect(int slotIndex, ItemStack stack) {
        setStackInSlotUnchecked(slotIndex, stack);
    }

    protected final void setStackInSlotUnchecked(int slot, ItemStack stack) {
        setStackInSlotInternal(slot, stack, false);
    }

    private void setStackInSlotInternal(int slot, ItemStack stack, boolean validateItem) {
        validateSlotIndex(slot);
        Objects.requireNonNull(stack, "stack");

        if (!stack.isEmpty()) {
            // Note: we intentionally do NOT enforce stack.getCount() <= getSlotLimit(slot)
            // here. Save loading and client slot sync both call this path, and may carry
            // stacks whose count exceeds the current slot limit (e.g. legacy saves from
            // when the matrix slot capped at 64 instead of 32). Insertion via insertItem
            // still enforces the limit, so automation cannot grow stacks beyond it.
            if (validateItem && !isItemValid(slot, stack)) {
                throw new IllegalArgumentException("Stack " + stack + " is not valid for slot " + slot);
            }
        }

        stacks.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        onContentsChanged(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return insertItemInternal(slot, stack, simulate, true);
    }

    protected final ItemStack insertItemUnchecked(int slot, ItemStack stack, boolean simulate) {
        return insertItemInternal(slot, stack, simulate, false);
    }

    private ItemStack insertItemInternal(int slot, ItemStack stack, boolean simulate, boolean validateItem) {
        validateSlotIndex(slot);
        Objects.requireNonNull(stack, "stack");

        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (validateItem && !isItemValid(slot, stack)) {
            return stack;
        }

        ItemStack existing = stacks.get(slot);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) {
            return stack;
        }

        // Intentionally ignore stack.getMaxStackSize() here.
        int freeSpace = getSlotLimit(slot) - existing.getCount();
        if (freeSpace <= 0) {
            return stack;
        }

        int toInsert = Math.min(stack.getCount(), freeSpace);
        if (toInsert <= 0) {
            return stack;
        }

        if (!simulate) {
            ItemStack newStack;
            if (existing.isEmpty()) {
                newStack = stack.copyWithCount(toInsert);
            } else {
                newStack = existing.copy();
                newStack.grow(toInsert);
            }
            stacks.set(slot, newStack);
            onContentsChanged(slot);
        }

        if (toInsert == stack.getCount()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack.copy();
        remainder.shrink(toInsert);
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        validateSlotIndex(slot);
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack existing = stacks.get(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int toExtract = Math.min(amount, existing.getCount());
        if (toExtract <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack extracted = existing.copyWithCount(toExtract);
        if (!simulate) {
            if (toExtract == existing.getCount()) {
                stacks.set(slot, ItemStack.EMPTY);
            } else {
                ItemStack reduced = existing.copy();
                reduced.shrink(toExtract);
                stacks.set(slot, reduced);
            }
            onContentsChanged(slot);
        }

        return extracted;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        return true;
    }

    protected final void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= stacks.size()) {
            throw new IllegalArgumentException("Slot " + slot + " not in valid range - [0," + stacks.size() + ")");
        }
    }

    protected void onContentsChanged(int slot) {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    @Override
    public void sendChangeNotification(int slot) {
        validateSlotIndex(slot);
        onContentsChanged(slot);
    }

    public final void clear() {
        for (int slot = 0; slot < stacks.size(); slot++) {
            if (!stacks.get(slot).isEmpty()) {
                stacks.set(slot, ItemStack.EMPTY);
                onContentsChanged(slot);
            }
        }
    }

    public final void saveToTag(CompoundTag tag, String key, HolderLookup.Provider registries) {
        if (isEmpty()) {
            tag.remove(key);
            return;
        }

        ListTag items = new ListTag();
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            CompoundTag itemTag = new CompoundTag();
            itemTag.putInt(TAG_SLOT, slot);
            itemTag.putInt(TAG_COUNT_INT, stack.getCount());
            Tag stackTag = stack.copyWithCount(1).save(registries, new CompoundTag());
            itemTag.put(TAG_STACK, stackTag);
            items.add(itemTag);
        }
        tag.put(key, items);
    }

    public final void loadFromTag(CompoundTag tag, String key, HolderLookup.Provider registries) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            stacks.set(slot, ItemStack.EMPTY);
        }

        if (!tag.contains(key, Tag.TAG_LIST)) {
            return;
        }

        ListTag items = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag itemTag = items.getCompound(i);
            int slot = itemTag.getInt(TAG_SLOT);
            if (slot < 0 || slot >= stacks.size()) {
                continue;
            }

            ItemStack stack = itemTag.contains(TAG_STACK, Tag.TAG_COMPOUND)
                    ? ItemStack.parseOptional(registries, itemTag.getCompound(TAG_STACK))
                    : ItemStack.parseOptional(registries, itemTag);
            if (stack.isEmpty()) {
                continue;
            }

            int savedCount = itemTag.contains(TAG_COUNT_INT, Tag.TAG_INT)
                    ? itemTag.getInt(TAG_COUNT_INT)
                    : stack.getCount();
            // Preserve the saved count as-is, even if it exceeds the current slot limit.
            // This grandfathers in stacks from older versions whose limit has since been
            // lowered (e.g. matrix slot 64 -> 32). The insert path still enforces the
            // current limit, so external automation cannot grow stacks beyond it; the
            // player can drain the excess through the GUI.
            stack = stack.copyWithCount(Math.max(1, savedCount));
            stacks.set(slot, stack);
        }
    }

    public final boolean isEmpty() {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
