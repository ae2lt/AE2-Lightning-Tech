package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;

class ProviderWirelessDispatchRecoveryTest {

    @Test
    void deadTargetReappearingInSameTopologyIsRequeued() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        var dispatch = new ProviderWirelessDispatch();
        var connection = new WirelessConnection(
                Level.OVERWORLD,
                new BlockPos(0, 64, 0),
                Direction.NORTH);
        var unchangedTopology = List.of(connection);
        dispatch.prepare(
                unchangedTopology,
                200L,
                false,
                WirelessDispatchMode.EVEN_DISTRIBUTION);

        boolean acceptedWhileOffline = dispatch.dispatchSingleCopy(
                WirelessDispatchMode.EVEN_DISTRIBUTION,
                null,
                200L,
                false,
                1,
                ignored -> WirelessPushOutcome.HARD_FAIL,
                ignored -> false,
                ignored -> {
                });

        assertFalse(acceptedWhileOffline);
        assertNull(dispatch.existingState(connection));

        // Validation can observe the target alive again before ever publishing
        // an empty topology. The same list must still rebuild the removed state.
        dispatch.prepare(
                unchangedTopology,
                201L,
                false,
                WirelessDispatchMode.EVEN_DISTRIBUTION);
        boolean acceptedAfterReload = dispatch.dispatchSingleCopy(
                WirelessDispatchMode.EVEN_DISTRIBUTION,
                null,
                201L,
                false,
                1,
                ignored -> WirelessPushOutcome.SUCCESS,
                ignored -> true,
                ignored -> {
                });

        assertTrue(acceptedAfterReload);
    }
}
