package com.moakiee.ae2lt.logic.craft;

import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class MatrixMultiblockUpdateScheduler {
    private MatrixMultiblockUpdateScheduler() {
    }

    public static void scheduleNear(Level level, BlockPos changedPos) {
        if (level == null || level.isClientSide || changedPos == null) {
            return;
        }
        for (var controllerPos : MatrixMultiblockScanner.candidateControllerPositions(changedPos)) {
            if (level.getBlockEntity(controllerPos) instanceof MatrixControllerBlockEntity controller) {
                controller.scheduleStructureCheck();
            }
        }
    }
}
