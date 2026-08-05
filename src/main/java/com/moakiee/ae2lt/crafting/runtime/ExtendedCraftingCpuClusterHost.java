package com.moakiee.ae2lt.crafting.runtime;

/**
 * Grid-node owner that exposes a non-vanilla crafting CPU cluster to Thunderbolt.
 *
 * <p>For a multiblock, only its actionable/core node owner should implement this interface. Exposing
 * the same cluster from every member node would make node-removal notifications ambiguous.
 */
public interface ExtendedCraftingCpuClusterHost extends ExtendedCraftingCpuClusterProvider {
    @Override
    ExtendedCraftingCpuCluster getExtendedCraftingCpuCluster();
}
