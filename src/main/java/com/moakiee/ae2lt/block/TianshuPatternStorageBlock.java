package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.TianshuPatternStorageBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Physical warehouse for encoded Tianshu closed-loop patterns. */
public final class TianshuPatternStorageBlock extends TianshuSupercomputingUnitBlock
        implements EntityBlock {
    public TianshuPatternStorageBlock(Properties properties) {
        super(properties, TianshuMultiblockComponent.CLOSED_LOOP_PATTERN_STORAGE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TianshuPatternStorageBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof TianshuPatternStorageBlockEntity storage) {
            storage.dropStoredPatterns(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
