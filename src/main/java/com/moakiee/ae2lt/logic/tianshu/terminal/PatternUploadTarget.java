package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record PatternUploadTarget(
        ResourceKey<Level> dimension,
        BlockPos pos,
        PatternContainerGroup group,
        int freeSlots,
        int totalSlots) {
    public PatternUploadTarget {
        pos = pos.immutable();
    }
}
