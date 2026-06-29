package com.moakiee.ae2lt.client.ctm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides, for a connected-texture block, whether the CTM appearance is active
 * and whether a given neighbour direction "connects" (border removed). Pluggable
 * so different blocks can supply their own rules (e.g. formed-only glass).
 */
public interface ConnectionPredicate {

    /** When false the block renders its plain {@code base} texture (no CTM). */
    boolean isActive(BlockAndTintGetter level, BlockPos pos, BlockState self);

    /** Whether the neighbour in {@code dir} connects to this block. */
    boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, Direction dir);

    /** Whether the block at an arbitrary neighbour position connects to this block. */
    default boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, BlockPos neighbourPos) {
        for (Direction dir : Direction.values()) {
            if (pos.relative(dir).equals(neighbourPos)) {
                return connects(level, pos, self, dir);
            }
        }
        return false;
    }
}
