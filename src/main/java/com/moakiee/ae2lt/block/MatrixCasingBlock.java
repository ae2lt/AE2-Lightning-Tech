package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;

import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Matrix casing block. Carries a FORMED state so its connected texture can
 * switch to the assembled shell appearance with the rest of the multiblock.
 */
public class MatrixCasingBlock extends MatrixFormedBlock {
    public static final BooleanProperty FORMED = MatrixFormedBlock.FORMED;

    public MatrixCasingBlock(Properties properties, MatrixMultiblockComponent component) {
        super(properties, component);
    }
}
