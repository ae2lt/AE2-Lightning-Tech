package com.moakiee.ae2lt.crafting.runtime;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGridNodeService;

/**
 * Grid-node service that dynamically exposes a non-vanilla crafting CPU cluster.
 *
 * <p>A provider may temporarily return {@code null}, for example while a multiblock port is waiting
 * for its controller to validate. Thunderbolt keeps the provider node indexed and observes later
 * activation, suspension and cluster replacement during the crafting-service tick.</p>
 */
public interface ExtendedCraftingCpuClusterProvider extends IGridNodeService {
    @Nullable
    ExtendedCraftingCpuCluster getExtendedCraftingCpuCluster();
}
