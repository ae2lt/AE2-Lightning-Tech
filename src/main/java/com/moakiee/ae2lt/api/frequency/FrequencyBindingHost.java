package com.moakiee.ae2lt.api.frequency;

import appeng.blockentity.grid.AENetworkedBlockEntity;

/**
 * Receiver-side binding contract for AE-networked block entities that join a
 * wireless controller through the AE2LT frequency system.
 *
 * <p>Implementing block entities must:
 * <ul>
 *   <li>extend {@link AENetworkedBlockEntity} (AE2 requirement: a main grid
 *       node is needed for the virtual connection to be built),</li>
 *   <li>hold a {@link FrequencyBindingAccess} obtained from
 *       {@link FrequencyApi#createBinding(FrequencyBindingHost)},</li>
 *   <li>forward the lifecycle calls (onReady / setRemoved / clearRemoved /
 *       saveAdditional / loadAdditional / serverTick / main-node state
 *       change) to the access object.</li>
 * </ul>
 */
public interface FrequencyBindingHost {
    /** The AE-networked block entity that owns the main grid node to be connected. */
    AENetworkedBlockEntity getFrequencyBindingBlockEntity();

    /** Persist pending NBT changes — typically {@code setChanged()} on the BE. */
    void saveFrequencyBindingChanges();

    /** Trigger a block update / sync packet — typically a custom data sync helper. */
    void markFrequencyBindingForUpdate();

    /**
     * The binding access handle this host owns. Implementations typically
     * store the value returned by
     * {@link FrequencyApi#createBinding(FrequencyBindingHost)} once in a final
     * field and return it here.
     */
    FrequencyBindingAccess getFrequencyBindingAccess();

    /** Translation key displayed in the bound-devices list. Defaults to the block's description id. */
    default String getFrequencyBindingDeviceName() {
        return getFrequencyBindingBlockEntity().getBlockState().getBlock().getDescriptionId();
    }

    /** Bound frequency id ({@code -1} when unbound). Shortcut for {@code getFrequencyBindingAccess().getFrequencyId()}. */
    default int getFrequencyId() {
        return getFrequencyBindingAccess().getFrequencyId();
    }

    /** Bind to a frequency. Shortcut. */
    default void setFrequency(int frequencyId) {
        getFrequencyBindingAccess().setFrequency(frequencyId);
    }

    /** Clear the binding. Shortcut. */
    default void clearFrequency() {
        getFrequencyBindingAccess().clearFrequency();
    }

    /** True when the virtual grid connection to the transmitter is live. Shortcut. */
    default boolean isFrequencyConnected() {
        return getFrequencyBindingAccess().isConnected();
    }

    /** Channels used by the grid the host is part of. Shortcut. */
    default int getGridUsedChannels() {
        return getFrequencyBindingAccess().getGridUsedChannels();
    }

    /** Max channels of the grid the host is part of. Shortcut. */
    default int getGridMaxChannels() {
        return getFrequencyBindingAccess().getGridMaxChannels();
    }
}
