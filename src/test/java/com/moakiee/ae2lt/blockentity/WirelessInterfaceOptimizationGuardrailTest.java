package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * User-visible outcomes frozen before the next optimization, independently of
 * scheduler implementation and operation counts. See the optimization handoff.
 */
@Tag("wireless-interface-io-wake")
class WirelessInterfaceOptimizationGuardrailTest {
    // Baseline ratios are serialized to eight decimal places.
    private static final double ROUNDING_EPSILON = 1e-8;

    @Test
    void optimizationPreservesThroughputLatencyAndBacklog() throws Exception {
        var baseline = readBaseline();
        var scenarios = WirelessInterfaceDemandWakeOptimizationTest.focusedScenarios();
        assertEquals(scenarios.stream().map(s -> s.id()).collect(java.util.stream.Collectors.toSet()),
                baseline.keySet(), "baseline and diagnostic scenario coverage must match");

        for (var scenario : scenarios) {
            var before = baseline.get(scenario.id());
            var after = WirelessInterfaceSchedulingPressureModel.run(scenario);
            assertAll(scenario.id(),
                    () -> assertTrue(after.correctnessFailures().isEmpty(),
                            after.correctnessFailures().toString()),
                    () -> assertEquals(after.producedOutput(),
                            after.extractedOutput() + after.finalOutput(), "output conservation"),
                    () -> assertEquals(after.dispatchedInput(),
                            after.consumedInput() + after.finalInput(), "input conservation"),
                    () -> assertTrue(after.finalOutput() >= 0 && after.finalInput() >= 0,
                            "nonnegative inventories"),
                    () -> assertTrue(after.minimumWindowThroughput() + ROUNDING_EPSILON
                            >= before.window(), "window throughput regressed"),
                    () -> assertTrue(after.minimumMachineThroughput() + ROUNDING_EPSILON
                            >= before.machine(), "worst-machine throughput regressed"),
                    () -> assertTrue(after.pressureEvents() <= before.pressure(),
                            "production pressure increased"),
                    () -> assertTrue(after.p99ImportLatency() <= before.p99(),
                            "P99 transfer latency increased"),
                    () -> assertTrue(after.maximumDemandWait() <= before.waitTicks(),
                            "maximum demand wait increased"),
                    () -> assertTrue(after.backlogItemTicks() <= before.backlog(),
                            "integrated output backlog increased"),
                    () -> assertTrue(after.finalOutput() <= before.finalOutput(),
                            "unserved output at end of unchanged window increased"),
                    () -> assertTrue(after.producedOutput() >= before.produced(),
                            "less production must not masquerade as faster service"),
                    () -> assertTrue(after.extractedOutput() >= before.extracted(),
                            "less transfer must not masquerade as faster service"));
        }
    }

    private static Map<String, Baseline> readBaseline() throws Exception {
        var stream = WirelessInterfaceOptimizationGuardrailTest.class.getResourceAsStream(
                "/wireless-io/optimization-outcomes-c42fcbf4.csv");
        assertNotNull(stream, "checked-in outcome baseline is required");
        var result = new LinkedHashMap<String, Baseline>();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            assertEquals("scenario,min_window,min_machine,pressure_events,p99_latency,max_demand_wait,"
                    + "backlog_item_ticks,final_output,produced_output,extracted_output", reader.readLine());
            for (String line; (line = reader.readLine()) != null;) {
                var c = line.split(",", -1);
                assertEquals(10, c.length, "invalid baseline row: " + line);
                var previous = result.put(c[0], new Baseline(
                        Double.parseDouble(c[1]), Double.parseDouble(c[2]), Long.parseLong(c[3]),
                        Integer.parseInt(c[4]), Integer.parseInt(c[5]), Long.parseLong(c[6]),
                        Long.parseLong(c[7]), Long.parseLong(c[8]), Long.parseLong(c[9])));
                assertTrue(previous == null, "duplicate baseline scenario: " + c[0]);
            }
        }
        assertEquals(9, result.size(), "do not silently remove baseline scenarios");
        return result;
    }

    private record Baseline(double window, double machine, long pressure, int p99,
                            int waitTicks, long backlog, long finalOutput,
                            long produced, long extracted) {
    }
}
