package com.moakiee.ae2lt.grid.wirelesslink;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KnownInternalGridNodesTest {
    @Test
    void matchesOnlyExactAe2csBroadcasterChannelOwner() {
        assertTrue(KnownInternalGridNodes.isSupplementalEntranceExcluded(
                KnownInternalGridNodes.AE2CS_ENDER_BROADCASTER,
                true));
        assertFalse(KnownInternalGridNodes.isSupplementalEntranceExcluded(
                KnownInternalGridNodes.AE2CS_ENDER_BROADCASTER,
                false));
        assertFalse(KnownInternalGridNodes.isSupplementalEntranceExcluded(
                "appeng.blockentity.networking.SecurityStationBlockEntity",
                true));
    }
}
