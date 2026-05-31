package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WirelessConnectorRendererTest {

    private static final String HOST_PROVIDER = "provider";
    private static final String HOST_INTERFACE = "interface";

    @Test
    void rendersEveryHostWhenNoHostIsSelected() {
        assertTrue(WirelessConnectorRenderFilter.shouldRenderHost(
                false,
                false,
                0L,
                null,
                HOST_PROVIDER,
                1L));
    }

    @Test
    void rendersOnlyTheSelectedHostWhenAHostIsSelected() {
        long selectedPos = 1L;

        assertTrue(WirelessConnectorRenderFilter.shouldRenderHost(
                true,
                true,
                selectedPos,
                HOST_PROVIDER,
                HOST_PROVIDER,
                selectedPos));
        assertFalse(WirelessConnectorRenderFilter.shouldRenderHost(
                true,
                true,
                selectedPos,
                HOST_PROVIDER,
                HOST_PROVIDER,
                2L));
        assertFalse(WirelessConnectorRenderFilter.shouldRenderHost(
                true,
                true,
                selectedPos,
                HOST_PROVIDER,
                HOST_INTERFACE,
                selectedPos));
    }

    @Test
    void rendersNoCurrentDimensionHostsWhenSelectionIsInAnotherDimension() {
        assertFalse(WirelessConnectorRenderFilter.shouldRenderHost(
                true,
                false,
                1L,
                HOST_PROVIDER,
                HOST_PROVIDER,
                1L));
    }
}
