package com.moakiee.ae2lt.blockentity;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import com.moakiee.ae2lt.logic.EjectModeRegistry;
import com.moakiee.ae2lt.registry.ModBlockEntities;

/**
 * Lightweight runtime-only BlockEntity returned by the getBlockEntity Mixin
 * at eject-mode interception positions (M.relative(F)).
 * <p>
 * Not persisted, not associated with any chunk. Its sole purpose is to satisfy
 * {@code level.getBlockEntity(pos) != null} checks that some machines perform
 * before querying capabilities.
 */
public class GhostOutputBlockEntity extends BlockEntity {
    private static final IItemHandler REJECTING_ITEM_HANDLER = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    private static final IFluidHandler REJECTING_FLUID_HANDLER = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    public GhostOutputBlockEntity(BlockPos pos) {
        super(ModBlockEntities.GHOST_OUTPUT.get(), pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (level == null || side == null || EjectModeRegistry.isBypassed()) {
            return super.getCapability(capability, side);
        }

        var entry = EjectModeRegistry.lookupByFace(level.dimension(), worldPosition.asLong(), side);
        if (entry == null) {
            return super.getCapability(capability, side);
        }

        var host = entry.getHost();
        if (host != null && host.getLevel() != null) {
            EjectModeRegistry.setBypass(true);
            try {
                return host.getCapability(capability, side);
            } finally {
                EjectModeRegistry.setBypass(false);
            }
        }

        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return LazyOptional.of(() -> REJECTING_ITEM_HANDLER).cast();
        }
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return LazyOptional.of(() -> REJECTING_FLUID_HANDLER).cast();
        }
        return LazyOptional.empty();
    }
}
