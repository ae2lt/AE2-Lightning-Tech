package com.moakiee.ae2lt.grid;

/**
 * Internal receiver-side host contract. Extends the public
 * {@link com.moakiee.ae2lt.api.frequency.FrequencyBindingHost} and adds the
 * helper-typed accessor required by intra-mod code (the helper is internal).
 *
 * <p>All public default forwards (id, set/clear, isConnected, channels) come
 * from the api supertype and route through {@link #getFrequencyBindingAccess()}.
 */
public interface FrequencyBindingHost extends com.moakiee.ae2lt.api.frequency.FrequencyBindingHost {
    FrequencyBindingHelper getFrequencyBinding();

    @Override
    default com.moakiee.ae2lt.api.frequency.FrequencyBindingAccess getFrequencyBindingAccess() {
        return getFrequencyBinding();
    }
}
