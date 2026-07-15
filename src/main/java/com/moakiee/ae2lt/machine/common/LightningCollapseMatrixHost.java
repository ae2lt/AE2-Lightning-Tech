package com.moakiee.ae2lt.machine.common;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.inventories.InternalInventory;
import appeng.util.inv.PlayerInternalInventory;

import com.moakiee.ae2lt.registry.ModItems;

/**
 * Common contract for machines with a Lightning Collapse Matrix slot.
 *
 * <p>The slot index and capacity belong to the machine inventory. This keeps
 * world interaction and memory-card restoration correct for both single-matrix
 * machines and the Overload Processing Factory's larger matrix stack.</p>
 */
public interface LightningCollapseMatrixHost {
    IItemHandlerModifiable getMatrixInventory();

    int getMatrixSlot();

    default int getInstalledMatrixCount() {
        ItemStack installed = getMatrixInventory().getStackInSlot(getMatrixSlot());
        if (!installed.is(ModItems.LIGHTNING_COLLAPSE_MATRIX.get())) {
            return 0;
        }
        return Math.min(installed.getCount(), getMatrixSlotLimit());
    }

    default int getMatrixSlotLimit() {
        return getMatrixInventory().getSlotLimit(getMatrixSlot());
    }

    /**
     * Inserts as many matrices as the target machine can accept.
     */
    default boolean insertMatricesFromHand(Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);
        ItemStack remainder = getMatrixInventory().insertItem(getMatrixSlot(), heldItem.copy(), false);
        int inserted = heldItem.getCount() - remainder.getCount();
        if (inserted <= 0) {
            return false;
        }
        if (!player.getAbilities().instabuild) {
            heldItem.shrink(inserted);
        }
        return true;
    }

    /**
     * Reconciles the matrix slot with a memory-card template. Survival players
     * supply missing matrices from their main inventory; excess matrices are
     * returned instead of being deleted. Creative players get an exact,
     * cost-free copy of the template state.
     *
     * @return how many requested matrices could not be supplied
     */
    default int restoreMatricesFromMemoryCard(@Nullable Player player, int requestedCount) {
        if (player == null || player.level().isClientSide()) {
            return 0;
        }

        int desiredCount = LightningCollapseMatrixCounts.clampToSlotLimit(
                requestedCount, getMatrixSlotLimit());
        int installedCount = getInstalledMatrixCount();

        if (installedCount > desiredCount) {
            ItemStack removed = getMatrixInventory().extractItem(
                    getMatrixSlot(), installedCount - desiredCount, false);
            if (!player.getAbilities().instabuild) {
                returnToPlayer(player, removed);
            }
            installedCount -= removed.getCount();
        }

        int missingCount = desiredCount - installedCount;
        if (missingCount <= 0) {
            return 0;
        }

        ItemStack desiredStack = new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get(), missingCount);
        ItemStack simulatedRemainder = getMatrixInventory().insertItem(getMatrixSlot(), desiredStack, true);
        int insertableCount = missingCount - simulatedRemainder.getCount();
        if (insertableCount <= 0) {
            return missingCount;
        }

        if (player.getAbilities().instabuild) {
            ItemStack remainder = getMatrixInventory().insertItem(
                    getMatrixSlot(), desiredStack.copyWithCount(insertableCount), false);
            return missingCount - insertableCount + remainder.getCount();
        }

        InternalInventory source = new PlayerInternalInventory(player.getInventory());
        ItemStack supplied = source.removeItems(insertableCount, desiredStack, null);
        if (supplied.isEmpty()) {
            return missingCount;
        }

        ItemStack remainder = getMatrixInventory().insertItem(getMatrixSlot(), supplied, false);
        int inserted = supplied.getCount() - remainder.getCount();
        if (!remainder.isEmpty()) {
            ItemStack sourceRemainder = source.addItems(remainder);
            returnToPlayer(player, sourceRemainder);
        }
        return missingCount - inserted;
    }

    /**
     * Resolves a clicked matrix host, including the upper half of a two-block
     * machine such as the Tesla Coil.
     */
    @Nullable
    static LightningCollapseMatrixHost find(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof LightningCollapseMatrixHost host) {
            return host;
        }

        var state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.UPPER) {
            return null;
        }

        BlockPos lowerPos = pos.below();
        var lowerState = level.getBlockState(lowerPos);
        if (!lowerState.is(state.getBlock())
                || !lowerState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || lowerState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
            return null;
        }
        return level.getBlockEntity(lowerPos) instanceof LightningCollapseMatrixHost host ? host : null;
    }

    private static void returnToPlayer(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        InternalInventory destination = new PlayerInternalInventory(player.getInventory());
        ItemStack remainder = destination.addItems(stack);
        if (!remainder.isEmpty()) {
            player.drop(remainder, false);
        }
    }
}
