package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;

import net.minecraft.world.level.block.state.BlockState;

import appeng.block.AEBaseEntityBlock;

public class MatrixPortBlock extends AEBaseEntityBlock<MatrixPortBlockEntity>
        implements MatrixMultiblockComponentBlock {
    public MatrixPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MatrixMultiblockComponent matrixComponent(BlockState state) {
        return MatrixMultiblockComponent.MATRIX_PORT;
    }
}
