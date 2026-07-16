package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import com.moakiee.ae2lt.logic.craft.MatrixPatternCore;
import com.moakiee.ae2lt.logic.craft.MatrixPatternStorageTier;
import com.moakiee.ae2lt.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

public class MatrixPatternStorageBlockEntity extends BlockEntity implements MatrixPatternCore {
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_STACK = "Stack";
    private static final int T1_CAPACITY = 36;
    private static final int T2_CAPACITY = 72;

    private final NonNullList<ItemStack> items = NonNullList.withSize(T2_CAPACITY, ItemStack.EMPTY);
    private final PatternInventory inventory = new PatternInventory();
    private final List<IPatternDetails> cachedPatterns = new ArrayList<>();
    private BlockPos controllerPos;
    private boolean patternsDirty = true;
    private int usedSlots;

    public MatrixPatternStorageBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MATRIX_PATTERN_STORAGE.get(), pos, blockState);
    }

    public MatrixPatternStorageTier tier() {
        return component().patternStorageTier();
    }

    public int capacity() {
        return tier() == MatrixPatternStorageTier.T2 ? T2_CAPACITY : T1_CAPACITY;
    }

    public PatternInventory getInventory() {
        return inventory;
    }

    public boolean isEmpty() {
        return usedSlots == 0;
    }

    public boolean hasFreeSlot() {
        return usedSlots < capacity();
    }

    public List<ItemStack> copyContents() {
        var result = new ArrayList<ItemStack>(capacity());
        for (int slot = 0; slot < capacity(); slot++) {
            result.add(inventory.getStackInSlot(slot).copy());
        }
        return result;
    }

    public void dropStoredPatterns(Level level, BlockPos pos) {
        for (int slot = 0; slot < capacity(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack.copy());
            }
        }
    }

    public void loadContents(List<ItemStack> stacks) {
        for (int slot = 0; slot < capacity(); slot++) {
            ItemStack stack = slot < stacks.size() ? stacks.get(slot) : ItemStack.EMPTY;
            inventory.setStackInSlotInternal(slot, stack, false);
        }
        patternsDirty = true;
        setChangedAndUpdate();
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public void setControllerPos(BlockPos controllerPos) {
        this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
    }

    public boolean isT1() {
        return tier() == MatrixPatternStorageTier.T1;
    }

    public boolean isValidPatternStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (level == null) {
            return PatternDetailsHelper.isEncodedPattern(stack);
        }
        var details = PatternDetailsHelper.decodePattern(stack, level);
        return details instanceof IMolecularAssemblerSupportedPattern;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (patternsDirty) {
            rebuildPatternCache();
        }
        return List.copyOf(cachedPatterns);
    }

    @Override
    public boolean hasPattern(IPatternDetails details) {
        if (details == null) {
            return false;
        }
        if (patternsDirty) {
            rebuildPatternCache();
        }
        for (var pattern : cachedPatterns) {
            if (MatrixPatternCore.samePattern(pattern, details)) {
                return true;
            }
        }
        return false;
    }

    private void rebuildPatternCache() {
        if (level == null) {
            return;
        }
        cachedPatterns.clear();
        patternsDirty = false;
        for (int slot = 0; slot < capacity(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            var details = PatternDetailsHelper.decodePattern(stack, level);
            if (details instanceof IMolecularAssemblerSupportedPattern) {
                cachedPatterns.add(details);
            }
        }
    }

    private MatrixMultiblockComponent component() {
        var block = getBlockState().getBlock();
        if (block instanceof com.moakiee.ae2lt.block.MatrixMultiblockComponentBlock componentBlock) {
            return componentBlock.matrixComponent(getBlockState());
        }
        return MatrixMultiblockComponent.PATTERN_STORAGE_T1;
    }

    private void setChangedAndUpdate() {
        setChanged();
        if (level != null && !level.isClientSide) {
            notifyPortPatternsChanged();
        }
    }

    private void notifyPortPatternsChanged() {
        if (controllerPos == null || level == null || !level.isLoaded(controllerPos)) {
            return;
        }
        if (level.getBlockEntity(controllerPos) instanceof MatrixControllerBlockEntity controller) {
            var portPos = controller.getPortPos();
            if (portPos != null && level.isLoaded(portPos)
                    && level.getBlockEntity(portPos) instanceof MatrixPortBlockEntity port) {
                port.patternsChanged();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var items = new ListTag();
        for (int slot = 0; slot < capacity(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            var itemTag = new CompoundTag();
            itemTag.putInt(TAG_SLOT, slot);
            itemTag.put(TAG_STACK, stack.save(registries, new CompoundTag()));
            items.add(itemTag);
        }
        tag.put(TAG_ITEMS, items);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = null;

        inventory.clear();
        patternsDirty = true;
        if (!tag.contains(TAG_ITEMS, Tag.TAG_LIST)) {
            return;
        }
        var items = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            var itemTag = items.getCompound(i);
            int slot = itemTag.getInt(TAG_SLOT);
            if (slot < 0 || slot >= capacity() || !itemTag.contains(TAG_STACK, Tag.TAG_COMPOUND)) {
                continue;
            }
            var stack = ItemStack.parseOptional(registries, itemTag.getCompound(TAG_STACK));
            inventory.setStackInSlotInternal(slot, stack, false);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public final class PatternInventory implements IItemHandlerModifiable {
        @Override
        public int getSlots() {
            return capacity();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            validateSlot(slot);
            return items.get(slot);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            setStackInSlotInternal(slot, stack, true);
        }

        private void setStackInSlotInternal(int slot, ItemStack stack, boolean validatePattern) {
            validateSlot(slot);
            var previous = items.get(slot);
            if (stack == null || stack.isEmpty()) {
                items.set(slot, ItemStack.EMPTY);
            } else {
                var copy = stack.copyWithCount(1);
                if (validatePattern && !isValidPatternStack(copy)) {
                    throw new IllegalArgumentException("Stack is not a molecular assembler pattern: " + copy);
                }
                items.set(slot, copy);
            }
            updateUsedSlots(previous, items.get(slot));
            patternsDirty = true;
            setChangedAndUpdate();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            validateSlot(slot);
            // Occupancy first, validation (a full pattern decode) second: a matrix in steady state is
            // mostly full and the network probes inserts every tick, so decoding the incoming stack for
            // an already-occupied slot is pure waste. Only an empty slot that could actually accept the
            // stack pays for the decode.
            if (stack.isEmpty() || !items.get(slot).isEmpty()) {
                return stack;
            }
            if (!isValidPatternStack(stack)) {
                return stack;
            }
            if (!simulate) {
                var previous = items.get(slot);
                items.set(slot, stack.copyWithCount(1));
                updateUsedSlots(previous, items.get(slot));
                patternsDirty = true;
                setChangedAndUpdate();
            }
            if (stack.getCount() == 1) {
                return ItemStack.EMPTY;
            }
            var remainder = stack.copy();
            remainder.shrink(1);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            validateSlot(slot);
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            var existing = items.get(slot);
            if (existing.isEmpty()) {
                return ItemStack.EMPTY;
            }
            var extracted = existing.copyWithCount(1);
            if (!simulate) {
                var previous = items.get(slot);
                items.set(slot, ItemStack.EMPTY);
                updateUsedSlots(previous, ItemStack.EMPTY);
                patternsDirty = true;
                setChangedAndUpdate();
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            validateSlot(slot);
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            validateSlot(slot);
            return isValidPatternStack(stack);
        }

        private void clear() {
            for (int slot = 0; slot < items.size(); slot++) {
                items.set(slot, ItemStack.EMPTY);
            }
            usedSlots = 0;
            patternsDirty = true;
        }

        private void updateUsedSlots(ItemStack previous, ItemStack current) {
            boolean wasEmpty = previous == null || previous.isEmpty();
            boolean isEmpty = current == null || current.isEmpty();
            if (wasEmpty && !isEmpty) {
                usedSlots++;
            } else if (!wasEmpty && isEmpty) {
                usedSlots--;
            }
        }

        private void validateSlot(int slot) {
            if (slot < 0 || slot >= capacity()) {
                throw new IllegalArgumentException("Slot " + slot + " not in valid range [0," + capacity() + ")");
            }
        }
    }
}
