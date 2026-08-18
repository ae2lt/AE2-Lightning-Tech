package com.moakiee.ae2lt.logic.craft;

import net.minecraft.core.BlockPos;

public record MatrixMultiblockMember(
        BlockPos worldPos,
        BlockPos localPos,
        MatrixMultiblockRole role,
        MatrixMultiblockComponent component) {
}
