package com.moakiee.ae2lt.block;

import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Block-state properties shared by compute units that can belong to either multiblock host. */
public final class MultiblockStateProperties {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    private MultiblockStateProperties() {
    }
}
