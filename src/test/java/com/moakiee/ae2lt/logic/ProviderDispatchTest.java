package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;

class ProviderDispatchTest {
    @Test
    void normalBatchVisitsAnotherTargetAfterOneRejects() {
        var dispatch = new ProviderNormalDispatch();
        var first = target(0);
        var second = target(1);
        var attempts = new AtomicInteger();

        long remaining = dispatch.dispatchBatch(
                null,
                List.of(first, second),
                1L,
                100L,
                (target, maxCopies) -> attempts.getAndIncrement() == 0
                        ? new ProviderNormalDispatch.BatchAttemptResult(
                                0L, false, false)
                        : new ProviderNormalDispatch.BatchAttemptResult(
                                maxCopies, false, false));

        assertEquals(0L, remaining);
        assertEquals(2, attempts.get());
    }

    @Test
    void wirelessEvenModeRotatesPastAliveHardFailure() {
        var dispatch = new ProviderWirelessDispatch();
        var first = connection(0);
        var second = connection(1);
        dispatch.prepare(
                List.of(first, second),
                200L,
                false,
                WirelessDispatchMode.EVEN_DISTRIBUTION);
        var visited = new ArrayList<WirelessConnection>();

        boolean accepted = dispatch.dispatchSingleCopy(
                WirelessDispatchMode.EVEN_DISTRIBUTION,
                null,
                200L,
                false,
                2,
                target -> {
                    visited.add(target);
                    return target.equals(first)
                            ? WirelessPushOutcome.HARD_FAIL
                            : WirelessPushOutcome.SUCCESS;
                },
                target -> true,
                target -> {
                    throw new AssertionError("Alive target must not be removed");
                });

        assertTrue(accepted);
        assertEquals(List.of(first, second), visited);
    }

    @Test
    void wirelessSingleModeSoftFailureLeavesTargetCoolingDown() {
        var dispatch = new ProviderWirelessDispatch();
        var connection = connection(0);
        dispatch.prepare(
                List.of(connection),
                300L,
                false,
                WirelessDispatchMode.SINGLE_TARGET);

        boolean accepted = dispatch.dispatchSingleCopy(
                WirelessDispatchMode.SINGLE_TARGET,
                null,
                300L,
                false,
                2,
                target -> WirelessPushOutcome.SOFT_FAIL,
                target -> true,
                target -> {
                });

        assertFalse(accepted);
        assertFalse(dispatch.existingState(connection).ready);
        assertTrue(dispatch.existingState(connection).cooldownUntil > 300L);
    }

    private static ProviderTarget target(int x) {
        return new ProviderTarget(
                Level.OVERWORLD,
                new BlockPos(x, 64, 0),
                Direction.NORTH);
    }

    private static WirelessConnection connection(int x) {
        return new WirelessConnection(
                Level.OVERWORLD,
                new BlockPos(x, 64, 0),
                Direction.NORTH);
    }
}
