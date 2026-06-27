package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class MatrixMultiblockSimpleBlock extends Block implements MatrixMultiblockComponentBlock {
    private final MatrixMultiblockComponent component;

    public MatrixMultiblockSimpleBlock(Properties properties, MatrixMultiblockComponent component) {
        super(properties);
        this.component = component;
    }

    @Override
    public MatrixMultiblockComponent matrixComponent(BlockState state) {
        return component;
    }
}
