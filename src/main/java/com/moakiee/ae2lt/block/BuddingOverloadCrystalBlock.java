package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;

public class BuddingOverloadCrystalBlock extends Block {
    public static final int GROWTH_CHANCE = 5;
    public static final int DECAY_CHANCE = 12;
    private static final Direction[] DIRECTIONS = Direction.values();

    public BuddingOverloadCrystalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource randomSource) {
        if (randomSource.nextInt(GROWTH_CHANCE) != 0) {
            return;
        }

        // Try to grow cluster
        Direction direction = Util.getRandom(DIRECTIONS, randomSource);
        BlockPos targetPos = pos.relative(direction);
        BlockState targetState = level.getBlockState(targetPos);
        Block newCluster = null;
        if (canClusterGrowAtState(targetState)) {
            newCluster = ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD.get();
        } else if (targetState.is(ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD.get())
                && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            newCluster = ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD.get();
        } else if (targetState.is(ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD.get())
                && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            newCluster = ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD.get();
        } else if (targetState.is(ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD.get())
                && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            newCluster = ModBlocks.OVERLOAD_CRYSTAL_CLUSTER.get();
        }

        if (newCluster == null) {
            return;
        }

        // Grow overload crystal
        BlockState newClusterState = newCluster.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, direction)
                .setValue(AmethystClusterBlock.WATERLOGGED, targetState.getFluidState().getType() == Fluids.WATER);
        level.setBlockAndUpdate(targetPos, newClusterState);

        // Damage the budding block after a successful growth (flawless never decays)
        if (this == ModBlocks.FLAWLESS_BUDDING_OVERLOAD_CRYSTAL.get() || randomSource.nextInt(DECAY_CHANCE) != 0) {
            return;
        }
        Block newBlock;
        if (this == ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL.get()) {
            newBlock = ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get();
        } else if (this == ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get()) {
            newBlock = ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get();
        } else if (this == ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get()) {
            newBlock = ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get();
        } else {
            throw new IllegalStateException("Unexpected block: " + this);
        }
        level.setBlockAndUpdate(pos, newBlock.defaultBlockState());
    }

    public static boolean canClusterGrowAtState(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().getAmount() == 8;
    }
}
