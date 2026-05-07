package com.moakiee.ae2lt.grid;

import appeng.blockentity.grid.AENetworkedBlockEntity;

/**
 * Receiver-side wireless frequency binding for block entities that already own
 * a main AE2 grid node. The helper stores the frequency id and creates the
 * virtual grid connection; the block entity supplies lifecycle callbacks.
 */
public interface FrequencyBindingHost {
    FrequencyBindingHelper getFrequencyBinding();

    AENetworkedBlockEntity getFrequencyBindingBlockEntity();

    void saveFrequencyBindingChanges();

    void markFrequencyBindingForUpdate();

    default String getFrequencyBindingDeviceName() {
        return getFrequencyBindingBlockEntity().getBlockState().getBlock().getDescriptionId();
    }

    default int getFrequencyId() {
        return getFrequencyBinding().getFrequencyId();
    }

    default void setFrequency(int frequencyId) {
        getFrequencyBinding().setFrequency(frequencyId);
    }

    default void clearFrequency() {
        getFrequencyBinding().clearFrequency();
    }

    default boolean isFrequencyConnected() {
        return getFrequencyBinding().isConnected();
    }

    default int getGridUsedChannels() {
        return getFrequencyBinding().getGridUsedChannels();
    }

    default int getGridMaxChannels() {
        return getFrequencyBinding().getGridMaxChannels();
    }
}
