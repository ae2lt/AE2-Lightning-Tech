package com.moakiee.ae2lt.api.frequency;

import appeng.api.networking.IGridNodeListener;
import net.minecraft.nbt.CompoundTag;

/**
 * Opaque handle returned by {@link FrequencyApi#createBinding(FrequencyBindingHost)}.
 * Its lifetime is bound to the owning block entity; forward the lifecycle
 * methods below from the matching BE callbacks.
 *
 * <p>All methods must be called from the server thread.
 */
public interface FrequencyBindingAccess {
    /** {@code -1} when unbound, {@code > 0} when bound to a valid frequency id. */
    int getFrequencyId();

    /** Bind to a new frequency, or pass {@code -1} to clear the binding. */
    void setFrequency(int frequencyId);

    /** Equivalent to {@code setFrequency(-1)}. */
    void clearFrequency();

    /** True when the virtual grid connection to the transmitter is live. */
    boolean isConnected();

    /** Call from the BE's server tick. */
    void serverTick();

    /** Call from the BE's {@code onReady()} override. */
    void onReady();

    /** Call from the BE's {@code setRemoved()} override. */
    void setRemoved();

    /** Call from the BE's {@code clearRemoved()} override. */
    void clearRemoved();

    /**
     * Call from the BE's main-node listener when the grid state changes. This
     * triggers a reconnection attempt after events such as {@code GRID_BOOT}.
     */
    void onMainNodeStateChanged(IGridNodeListener.State reason);

    /** Append the bound frequency id to the BE's NBT using key {@code "FrequencyId"}. */
    void save(CompoundTag tag);

    /** Restore the bound frequency id from the BE's NBT. */
    void load(CompoundTag tag);

    /** Channels currently allocated across the grid the host is part of. {@code 0} when not connected. */
    int getGridUsedChannels();

    /**
     * Maximum channels the grid could support. {@code 0} when not connected;
     * {@code -1} when the grid is in INFINITE channel mode.
     */
    int getGridMaxChannels();
}
