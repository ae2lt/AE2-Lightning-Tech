package com.moakiee.ae2lt.logic.tianshu;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class TianshuMultiblockUpdateScheduler {
    public static void scheduleNear(Level level, BlockPos changedPos) {
        if (level.isClientSide) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(changedPos.offset(-6, -6, -6), changedPos.offset(6, 6, 6))) {
            if (level.getBlockEntity(pos) instanceof TianshuSupercomputerControllerBlockEntity controller) {
                controller.scheduleStructureCheck();
            }
        }
    }

    private TianshuMultiblockUpdateScheduler() {
    }
}
