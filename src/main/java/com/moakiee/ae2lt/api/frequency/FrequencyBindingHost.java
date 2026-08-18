package com.moakiee.ae2lt.api.frequency;

import appeng.api.networking.IManagedGridNode;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Receiver-side binding contract for AE-networked block entities that join a
 * wireless controller through the AE2LT frequency system.
 *
 * <p>Implementing block entities must be AE-networked block entities with a
 * main {@link IManagedGridNode}, hold a {@link FrequencyBindingAccess} obtained
 * from {@link FrequencyApi#createBinding(FrequencyBindingHost)}, and forward
 * the relevant lifecycle calls to that access object.
 */
public interface FrequencyBindingHost {
    /** The block entity that owns the main grid node to be connected. */
    BlockEntity getFrequencyBindingBlockEntity();

    /** The main managed grid node that should be virtually connected to the transmitter. */
    IManagedGridNode getFrequencyBindingMainNode();

    /** Persist pending NBT changes, typically {@code saveChanges()} or {@code setChanged()} on the BE. */
    void saveFrequencyBindingChanges();

    /** Trigger a block update / sync packet, typically a custom data sync helper. */
    void markFrequencyBindingForUpdate();

    /**
     * The binding access handle this host owns. Implementations usually store
     * the value returned by {@link FrequencyApi#createBinding(FrequencyBindingHost)}
     * once in a final field and return it here.
     */
    FrequencyBindingAccess getFrequencyBindingAccess();

    /** Translation key displayed in the bound-devices list. Defaults to the block's description id. */
    default String getFrequencyBindingDeviceName() {
        return getFrequencyBindingBlockEntity().getBlockState().getBlock().getDescriptionId();
    }

    /** Bound frequency id ({@code -1} when unbound). */
    default int getFrequencyId() {
        return getFrequencyBindingAccess().getFrequencyId();
    }

    /** Bind to a frequency. */
    default void setFrequency(int frequencyId) {
        getFrequencyBindingAccess().setFrequency(frequencyId);
    }

    /** Clear the binding. */
    default void clearFrequency() {
        getFrequencyBindingAccess().clearFrequency();
    }

    /** True when the virtual grid connection to the transmitter is live. */
    default boolean isFrequencyConnected() {
        return getFrequencyBindingAccess().isConnected();
    }

    /** Channels used by the grid the host is part of. */
    default int getGridUsedChannels() {
        return getFrequencyBindingAccess().getGridUsedChannels();
    }

    /** Max channels of the grid the host is part of. */
    default int getGridMaxChannels() {
        return getFrequencyBindingAccess().getGridMaxChannels();
    }
}
