package com.moakiee.ae2lt.grid;

import appeng.api.networking.IManagedGridNode;
import appeng.blockentity.grid.AENetworkBlockEntity;

/**
 * Internal receiver-side host contract. Extends the public API host and adds
 * the helper-typed accessor required by AE2LT's own block entities.
 */
public interface FrequencyBindingHost extends com.moakiee.ae2lt.api.frequency.FrequencyBindingHost {
    FrequencyBindingHelper getFrequencyBinding();

    @Override
    AENetworkBlockEntity getFrequencyBindingBlockEntity();

    @Override
    default IManagedGridNode getFrequencyBindingMainNode() {
        return getFrequencyBindingBlockEntity().getMainNode();
    }

    @Override
    default com.moakiee.ae2lt.api.frequency.FrequencyBindingAccess getFrequencyBindingAccess() {
        return getFrequencyBinding();
    }
}
