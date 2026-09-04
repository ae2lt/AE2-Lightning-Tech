package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.DirectionMode;
import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.LoadShape;
import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.Result;
import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.Scenario;

/**
 * Behavior contracts for a future demand-driven wireless-I/O wakeup path.
 *
 * <p>These tests intentionally do not require a particular wakeup
 * implementation. They make the existing watchdog, ownership, and burst
 * behavior explicit so a future dirty/wake optimization cannot trade away
 * correctness or service fairness.</p>
 */
@Tag("wireless-interface-io-wake")
class WirelessInterfaceDemandWakeContractTest {
    private static final double EPSILON = 1e-12;

    @Test
    void idleDemandAndUnsignaledRecoveryRemainBounded() {
        for (String id : List.of(
                "pressure-import-single-tick-pulse-synchronized-machine-then-io",
                "pressure-import-single-tick-pulse-synchronized-io-then-machine",
                "pressure-import-hot-restart-slot-64",
                "pressure-import-target-flap",
                "pressure-import-target-outage",
                "pressure-import-scheduler-rebuild")) {
            var scenario = scenario(id);
            var result = WirelessInterfaceSchedulingPressureModel.run(scenario);

            assertCorrectAndConserved(result);
            assertTrue(result.acceptanceFailures().isEmpty(),
                    () -> id + " acceptance failures: " + result.acceptanceFailures());
            assertTrue(result.maximumDemandWait() <= scenario.maximumDemandWait(),
                    () -> id + " demand wait " + result.maximumDemandWait()
                            + " > " + scenario.maximumDemandWait());
        }
    }

    @Test
    void pulseAndBurstOrdersKeepThroughputAndFairness() {
        var scenarios = WirelessInterfaceSchedulingPressureModel.scenarios("full").stream()
                .filter(s -> s.direction() == DirectionMode.IMPORT
                        && (s.loadShape() == LoadShape.SINGLE_TICK_PULSE
                        || s.loadShape() == LoadShape.FOUR_TICK_BURST))
                .toList();
        assertEquals(12, scenarios.size(), "pulse/burst matrix coverage changed");

        for (var scenario : scenarios) {
            var result = WirelessInterfaceSchedulingPressureModel.run(scenario);
            String id = scenario.id();

            assertCorrectAndConserved(result);
            assertTrue(result.acceptanceFailures().isEmpty(),
                    () -> id + " acceptance failures: " + result.acceptanceFailures());
            assertTrue(result.minimumWindowThroughput()
                            + EPSILON >= scenario.minimumThroughput(),
                    () -> id + " window throughput=" + result.minimumWindowThroughput());
            assertTrue(result.minimumMachineThroughput()
                            + EPSILON >= scenario.minimumThroughput(),
                    () -> id + " machine throughput=" + result.minimumMachineThroughput());
            assertEquals(0, result.pressureEvents(), id + " introduced pressure");
            assertTrue(result.p99ImportLatency() <= scenario.maximumP99ImportLatency(),
                    () -> id + " p99 latency=" + result.p99ImportLatency());
            assertTrue(result.maximumDemandWait() <= scenario.maximumDemandWait(),
                    () -> id + " demand wait=" + result.maximumDemandWait());
            assertTrue(result.p99ToMeanWork() <= scenario.maximumP99ToMeanWork()
                            + EPSILON,
                    () -> id + " p99/mean work=" + result.p99ToMeanWork());
            assertTrue(result.idleVisits() > 0,
                    () -> id + " did not exercise the idle scheduling path");
        }
    }

    @Test
    void recoveryAndRebuildNeverDuplicateOrLoseItems() {
        for (String id : List.of(
                "pressure-bidirectional-target-flap",
                "pressure-bidirectional-target-outage",
                "pressure-bidirectional-scheduler-rebuild")) {
            var result = WirelessInterfaceSchedulingPressureModel.run(scenario(id));
            assertCorrectAndConserved(result);
            assertTrue(result.acceptanceFailures().isEmpty(),
                    () -> id + " acceptance failures: " + result.acceptanceFailures());
        }
    }

    private static Scenario scenario(String id) {
        return WirelessInterfaceSchedulingPressureModel.scenarios("full").stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing wake contract scenario: " + id));
    }

    private static void assertCorrectAndConserved(Result result) {
        String id = result.scenario().id();
        assertTrue(result.correctnessFailures().isEmpty(),
                () -> id + " correctness failures: " + result.correctnessFailures());
        assertEquals(result.producedOutput(),
                result.extractedOutput() + result.finalOutput(),
                id + " output ownership changed");
        assertEquals(result.dispatchedInput(),
                result.consumedInput() + result.finalInput(),
                id + " input ownership changed");
        assertTrue(result.finalOutput() >= 0 && result.finalInput() >= 0,
                () -> id + " negative final inventory state");
    }
}
