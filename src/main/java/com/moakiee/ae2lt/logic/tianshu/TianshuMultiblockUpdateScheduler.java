package com.moakiee.ae2lt.logic.tianshu;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class TianshuMultiblockUpdateScheduler {
    public static void scheduleNear(Level level, BlockPos changedPos) {
        if (level == null || level.isClientSide || changedPos == null) {
            return;
        }
        // The controller sits on the bottom layer, so it can only be at the
        // changed block's Y level or one of the six levels below it.
        for (BlockPos pos : BlockPos.betweenClosed(
                changedPos.offset(-6, -6, -6),
                changedPos.offset(6, 0, 6))) {
            if (level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof TianshuSupercomputerControllerBlockEntity controller) {
                controller.scheduleStructureCheck();
            }
        }
    }

    private TianshuMultiblockUpdateScheduler() {
    }
}
