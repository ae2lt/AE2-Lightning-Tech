package com.moakiee.ae2lt.api.frequency;

import java.util.function.Consumer;

import appeng.api.networking.IGridNodeListener;
import appeng.util.SettingsFrom;
import net.minecraft.core.component.DataComponentMap;
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
     * Call from the BE's main-node listener when the grid state changes
     * (e.g. {@code GRID_BOOT}); this triggers a reconnection attempt.
     */
    void onMainNodeStateChanged(IGridNodeListener.State reason);

    /** Append the bound frequency id to the BE's NBT (key {@code "FrequencyId"}). */
    void save(CompoundTag tag);

    /** Restore the bound frequency id from the BE's NBT. */
    void load(CompoundTag tag);

    /**
     * Export the frequency and host-supplied fields through AE2LT's stable
     * memory-card data component.
     *
     * <p>The callback is invoked only for a memory-card export. Keeping the
     * component itself behind this public API lets addon hosts preserve cards
     * written by older AE2LT-backed implementations without importing AE2LT
     * registry or memory-card internals.
     */
    default void exportMemorySettings(
            SettingsFrom mode,
            DataComponentMap.Builder builder,
            Consumer<CompoundTag> additionalWriter) {
    }

    /**
     * Import the frequency and host-supplied fields from AE2LT's stable
     * memory-card data component.
     *
     * <p>The callback is invoked only when compatible exported machine data is
     * present on a memory card.
     */
    default void importMemorySettings(
            SettingsFrom mode,
            DataComponentMap input,
            Consumer<CompoundTag> additionalReader) {
    }

    /** Channels currently allocated across the grid the host is part of. {@code 0} when not connected. */
    int getGridUsedChannels();

    /**
     * Maximum channels the grid could support. {@code 0} when not connected;
     * {@code -1} sentinel when the grid is in INFINITE channel mode.
     */
    int getGridMaxChannels();
}
