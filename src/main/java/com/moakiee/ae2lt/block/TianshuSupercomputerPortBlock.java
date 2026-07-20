package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockUpdateScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import appeng.block.AEBaseEntityBlock;
import org.jetbrains.annotations.Nullable;

public class TianshuSupercomputerPortBlock extends AEBaseEntityBlock<TianshuSupercomputerPortBlockEntity> {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty FORMED =
            TianshuSupercomputerStructureBlock.FORMED;

    public TianshuSupercomputerPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof TianshuSupercomputerPortBlockEntity port) {
                TianshuSupercomputerPortBlockEntity.serverTick(tickLevel, pos, tickState, port);
            }
        };
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) TianshuMultiblockUpdateScheduler.scheduleNear(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) TianshuMultiblockUpdateScheduler.scheduleNear(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
