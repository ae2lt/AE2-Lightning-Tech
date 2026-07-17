package com.moakiee.ae2lt.blockentity;

import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

final class MultiblockPortBinding {
    private MultiblockPortBinding() {
    }

    static boolean changes(boolean formed,
                           @Nullable BlockPos currentControllerPos,
                           @Nullable UUID currentMachineId,
                           @Nullable BlockPos nextControllerPos,
                           @Nullable UUID nextMachineId) {
        boolean nextFormed = nextControllerPos != null && nextMachineId != null;
        return formed != nextFormed
                || !Objects.equals(currentControllerPos, nextControllerPos)
                || !Objects.equals(currentMachineId, nextMachineId);
    }

    static boolean changes(boolean formed,
                           @Nullable BlockPos currentControllerPos,
                           @Nullable UUID currentMachineId,
                           @Nullable Object currentRuntime,
                           @Nullable BlockPos nextControllerPos,
                           @Nullable UUID nextMachineId,
                           @Nullable Object nextRuntime) {
        boolean nextFormed = nextControllerPos != null
                && nextMachineId != null
                && nextRuntime != null;
        return formed != nextFormed
                || !Objects.equals(currentControllerPos, nextControllerPos)
                || !Objects.equals(currentMachineId, nextMachineId)
                || currentRuntime != nextRuntime;
    }
}
