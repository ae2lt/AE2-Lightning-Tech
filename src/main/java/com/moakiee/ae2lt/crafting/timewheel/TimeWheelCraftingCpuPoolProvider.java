package com.moakiee.ae2lt.crafting.timewheel;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.crafting.runtime.ExtendedCraftingCpuCluster;
import com.moakiee.ae2lt.crafting.runtime.ExtendedCraftingCpuClusterProvider;

/**
 * Compatibility provider API for grid nodes that dynamically publish a time-wheel CPU pool.
 */
public interface TimeWheelCraftingCpuPoolProvider extends ExtendedCraftingCpuClusterProvider {
    @Nullable
    TimeWheelCraftingCpuPool getTimeWheelCraftingCpuPool();

    @Override
    default ExtendedCraftingCpuCluster getExtendedCraftingCpuCluster() {
        return getTimeWheelCraftingCpuPool();
    }
}
