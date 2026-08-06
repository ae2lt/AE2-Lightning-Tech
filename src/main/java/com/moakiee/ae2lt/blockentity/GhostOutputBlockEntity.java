package com.moakiee.ae2lt.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

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

    public GhostOutputBlockEntity(BlockPos pos) {
        super(ModBlockEntities.GHOST_OUTPUT.get(), pos, Blocks.AIR.defaultBlockState());
    }
}
