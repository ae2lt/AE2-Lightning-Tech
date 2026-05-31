package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class WirelessConnectionBatchEditTest {

    private static final String DIM = "dim";

    @Test
    void singleFacePlanUpdatesExistingTargetsAndConnectsMissingTargets() {
        var targetA = "a";
        var targetB = "b";
        var targetC = "c";

        var plan = WirelessConnectionBatchEdit.planSingleFacePerTarget(
                List.of(targetA, targetB, targetC),
                DIM,
                List.of(
                        new TestConnection(DIM, targetA, "north"),
                        new TestConnection(DIM, targetB, "south")),
                "south",
                TestConnection::dimension,
                TestConnection::pos,
                TestConnection::boundFace);

        assertEquals(List.of(), plan.disconnect());
        assertEquals(List.of(targetA), plan.update());
        assertEquals(List.of(targetC), plan.connect());
    }

    @Test
    void singleFacePlanDisconnectsAllTargetsWhenWholeBatchAlreadyMatches() {
        var targetA = "a";
        var targetB = "b";

        var plan = WirelessConnectionBatchEdit.planSingleFacePerTarget(
                List.of(targetA, targetB),
                DIM,
                List.of(
                        new TestConnection(DIM, targetA, "south"),
                        new TestConnection(DIM, targetB, "south")),
                "south",
                TestConnection::dimension,
                TestConnection::pos,
                TestConnection::boundFace);

        assertEquals(List.of(targetA, targetB), plan.disconnect());
        assertEquals(List.of(), plan.update());
        assertEquals(List.of(), plan.connect());
    }

    @Test
    void multiFacePlanOnlyDisconnectsMatchingEndpointAndAllowsOtherFaces() {
        var target = "target";

        var plan = WirelessConnectionBatchEdit.planMultiFacePerTarget(
                List.of(target),
                DIM,
                List.of(new TestConnection(DIM, target, "north")),
                "south",
                TestConnection::dimension,
                TestConnection::pos,
                TestConnection::boundFace);

        assertEquals(List.of(), plan.disconnect());
        assertEquals(List.of(target), plan.connect());

        var removePlan = WirelessConnectionBatchEdit.planMultiFacePerTarget(
                List.of(target),
                DIM,
                List.of(new TestConnection(DIM, target, "south")),
                "south",
                TestConnection::dimension,
                TestConnection::pos,
                TestConnection::boundFace);

        assertEquals(List.of(target), removePlan.disconnect());
        assertEquals(List.of(), removePlan.connect());
    }

    private record TestConnection(String dimension, String pos, String boundFace) {
    }
}
