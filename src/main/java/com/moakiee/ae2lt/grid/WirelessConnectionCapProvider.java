package com.moakiee.ae2lt.grid;

import appeng.api.networking.pathing.ChannelMode;

/**
 * Interface for grid node owners that participate in wireless connections and
 * need to specify per-link channel capacity in the max-flow algorithm.
 * <p>
 * Implemented by wireless controller block entities. The max-flow solver
 * checks connection endpoints for this interface to determine edge capacity
 * for virtual (direction-less) connections.
 */
public interface WirelessConnectionCapProvider {

    /**
     * @return the maximum channel capacity for a single wireless link from this
     *         controller to one receiver, given the current channel mode.
     *         Return {@link Integer#MAX_VALUE} / 2 for unlimited.
     */
    int getWirelessChannelCap(ChannelMode mode);
}
