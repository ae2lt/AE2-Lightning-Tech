package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.MatrixPatternStorageBlockEntity;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class MatrixPatternStorageBlock extends MatrixFormedBlock implements EntityBlock {
    public MatrixPatternStorageBlock(Properties properties, MatrixMultiblockComponent component) {
        super(properties, component);
        if (!component.isPatternStorage()) {
            throw new IllegalArgumentException("Matrix pattern storage block requires a pattern-storage component");
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MatrixPatternStorageBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                  BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        return null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && !(newState.getBlock() instanceof MatrixPatternStorageBlock)
                && !level.isClientSide
                && level.getBlockEntity(pos) instanceof MatrixPatternStorageBlockEntity storage) {
            storage.dropStoredPatterns(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
