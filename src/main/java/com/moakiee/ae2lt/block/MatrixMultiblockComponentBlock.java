package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;

import net.minecraft.world.level.block.state.BlockState;

public interface MatrixMultiblockComponentBlock {
    MatrixMultiblockComponent matrixComponent(BlockState state);
}
