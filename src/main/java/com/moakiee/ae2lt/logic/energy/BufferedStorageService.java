package com.moakiee.ae2lt.logic.energy;

import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;

/**
 * Storage-service wrapper that swaps the visible inventory for a buffered FE
 * proxy while forwarding all other storage-service behavior to the real grid.
 */
public class BufferedStorageService implements IStorageService {

    private final IStorageService delegate;
    private final BufferedMEStorage bufferedStorage;

    public BufferedStorageService(IStorageService delegate, BufferedMEStorage bufferedStorage) {
        this.delegate = delegate;
        this.bufferedStorage = bufferedStorage;
    }

    @Override
    public MEStorage getInventory() {
        return bufferedStorage;
    }

    @Override
    public KeyCounter getCachedInventory() {
        return delegate.getCachedInventory();
    }

    @Override
    public void addGlobalStorageProvider(IStorageProvider provider) {
        delegate.addGlobalStorageProvider(provider);
    }

    @Override
    public void removeGlobalStorageProvider(IStorageProvider provider) {
        delegate.removeGlobalStorageProvider(provider);
    }

    @Override
    public void refreshNodeStorageProvider(IGridNode node) {
        delegate.refreshNodeStorageProvider(node);
    }

    @Override
    public void refreshGlobalStorageProvider(IStorageProvider provider) {
        delegate.refreshGlobalStorageProvider(provider);
    }

    @Override
    public void invalidateCache() {
        delegate.invalidateCache();
    }

    public BufferedMEStorage getBufferedStorage() {
        return bufferedStorage;
    }
}
