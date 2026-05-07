package com.moakiee.ae2lt.me;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;

import com.moakiee.ae2lt.api.lightning.ILightningEnergyHandler;
import com.moakiee.ae2lt.api.lightning.LightningTier;
import com.moakiee.ae2lt.me.key.LightningKey;

/**
 * Adapter that exposes the lightning-energy slice of an AE2 grid through the
 * public {@link ILightningEnergyHandler} API.
 *
 * <p>The handler is stateless aside from its {@link IActionHost} reference, so it
 * is safe (and intended) to construct fresh on every capability lookup. All
 * read/write paths go through {@code grid.getStorageService().getInventory()}
 * exactly the same way the mod's own block entities do, so any external user of
 * this capability participates in normal AE2 storage semantics (channels,
 * security, partitioning, etc.).
 */
public final class GridLightningEnergyHandler implements ILightningEnergyHandler {

    private final IActionHost host;

    public GridLightningEnergyHandler(IActionHost host) {
        this.host = host;
    }

    private @Nullable IGrid getGrid() {
        var node = host.getActionableNode();
        return node == null ? null : node.getGrid();
    }

    @Override
    public long getStored(LightningTier tier) {
        IGrid grid = getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getCachedInventory().get(LightningKey.of(tier));
    }

    @Override
    public long getCapacity(LightningTier tier) {
        // AE2 grid storage capacity is the sum of attached cells; not exposed cheaply
        // at this API surface. Document it as unbounded.
        return Long.MAX_VALUE;
    }

    @Override
    public long insert(LightningTier tier, long amount, boolean simulate) {
        if (amount <= 0L) {
            return 0L;
        }
        IGrid grid = getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getInventory().insert(
                LightningKey.of(tier),
                amount,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                IActionSource.ofMachine(host));
    }

    @Override
    public long extract(LightningTier tier, long amount, boolean simulate) {
        if (amount <= 0L) {
            return 0L;
        }
        IGrid grid = getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getInventory().extract(
                LightningKey.of(tier),
                amount,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                IActionSource.ofMachine(host));
    }

    @Override
    public boolean canInsert(LightningTier tier) {
        return getGrid() != null;
    }

    @Override
    public boolean canExtract(LightningTier tier) {
        return getGrid() != null;
    }
}
