package com.moakiee.ae2lt.logic.tianshu.maintenance;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;

import com.moakiee.ae2lt.logic.tianshu.TianshuFunctionProfile;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPool;

import net.minecraft.world.level.Level;

/** Controller-owned services needed by inventory maintenance. */
public interface TianshuInventoryMaintenanceHost {
    boolean isFormed();

    boolean isCpuActive();

    TianshuFunctionProfile getFunctionProfile();

    TimeWheelCraftingCpuPool getTimeWheelCraftingCpuPool();

    @Nullable
    Level getLevel();

    @Nullable
    IGrid getGrid();

    IActionSource getActionSource();

    @Nullable
    IGridNode getActionableNode();

    void maintenanceStateChanged();
}
