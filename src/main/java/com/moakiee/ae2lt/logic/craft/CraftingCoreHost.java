package com.moakiee.ae2lt.logic.craft;

import appeng.api.stacks.AEKey;

/**
 * World/grid access the {@link CraftingCore} needs to deliver assembled outputs. Capacity and
 * energy are deliberately absent: those are decided by the rate limiter above the core.
 */
public interface CraftingCoreHost {
    long getGameTime();

    boolean isRemoved();

    boolean isConnected();

    long insertToNetwork(AEKey key, long amount);

    void spawnToWorld(AEKey key, long amount);
}
