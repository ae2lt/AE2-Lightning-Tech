package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.DirectionMode;
import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.Expectation;
import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.LoadShape;
import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.OutputMode;
import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.PhaseMode;
import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.ProductionMode;
import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.Result;
import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.Scenario;
import com.moakiee.ae2lt.blockentity.WirelessInterfaceSchedulingPressureModel.TickOrder;

/**
 * Focused telemetry for evaluating demand-driven wireless-I/O wakeup changes.
 *
 * <p>This is deliberately separate from the 178-scenario acceptance matrix.
 * It sweeps connection scale and isolates a one-pulse idle window so an
 * optimization can be compared using the same rows before and after the
 * production change.</p>
 */
@Tag("wireless-interface-io-wake")
class WirelessInterfaceDemandWakeOptimizationTest {
    private static final int ONE_PULSE_TICKS = 80;
    private static final int ACCEPTANCE_START = 40;
    private static final double EPSILON = 1e-12;

    @Test
    void writesDemandWakeOptimizationTelemetry() throws IOException {
        var scenarios = focusedScenarios();
        var rows = new ArrayList<TelemetryRow>();
        for (var scenario : scenarios) {
            var result = WirelessInterfaceSchedulingPressureModel.run(scenario);
            assertNoSemanticLoss(result);
            assertTrue(result.meanWork() >= 0 && result.p99Work() >= 0
                            && result.maximumWork() >= result.p99Work()
                            && result.maximumWork() >= result.meanWork(),
                    () -> scenario.id() + " produced invalid work distribution");
            if (scenario.loadShape() == LoadShape.FOUR_TICK_BURST) {
                assertTrue(result.minimumWindowThroughput()
                                + EPSILON >= 0.995,
                        () -> scenario.id() + " burst window throughput="
                                + result.minimumWindowThroughput());
                assertTrue(result.minimumMachineThroughput()
                                + EPSILON >= 0.995,
                        () -> scenario.id() + " burst machine throughput="
                                + result.minimumMachineThroughput());
                assertEquals(0, result.pressureEvents(),
                        scenario.id() + " introduced burst pressure");
            }
            rows.add(TelemetryRow.from(result));
            System.out.println(result.summaryLine());
        }

        var reportDirectory = Path.of(System.getProperty(
                "ae2lt.wirelessIo.wakeReportDir",
                "build/reports/wireless-interface-io-wake"))
                .toAbsolutePath().normalize();
        Files.createDirectories(reportDirectory);
        Files.writeString(reportDirectory.resolve("demand-wake-optimization.csv"),
                csv(rows), StandardCharsets.UTF_8);
        Files.writeString(reportDirectory.resolve("demand-wake-optimization.md"),
                markdown(rows), StandardCharsets.UTF_8);

        assertEquals(9, rows.size(), "focused demand-wake matrix changed");
        var onePulse1024 = rows.stream()
                .filter(row -> row.scenario().equals("wake-opt-one-pulse-1024-sync"))
                .findFirst().orElseThrow();
        assertTrue(onePulse1024.productiveVisits() > 0,
                "one-pulse workload did not record productive service");
        assertTrue(onePulse1024.p99MeanWork() > 0,
                "one-pulse workload did not record concentrated work");
    }

    static List<Scenario> focusedScenarios() {
        return List.of(
                importScenario("wake-opt-zero-1024", 1024,
                        TickOrder.MACHINE_THEN_IO, PhaseMode.HASHED,
                        LoadShape.ZERO, 80, Expectation.OBSERVATION_ONLY),
                importScenario("wake-opt-one-pulse-1", 1,
                        TickOrder.MACHINE_THEN_IO, PhaseMode.SYNCHRONIZED,
                        LoadShape.SINGLE_TICK_PULSE, ONE_PULSE_TICKS,
                        Expectation.OBSERVATION_ONLY),
                importScenario("wake-opt-one-pulse-64", 64,
                        TickOrder.MACHINE_THEN_IO, PhaseMode.SYNCHRONIZED,
                        LoadShape.SINGLE_TICK_PULSE, ONE_PULSE_TICKS,
                        Expectation.OBSERVATION_ONLY),
                importScenario("wake-opt-one-pulse-1024-sync", 1024,
                        TickOrder.MACHINE_THEN_IO, PhaseMode.SYNCHRONIZED,
                        LoadShape.SINGLE_TICK_PULSE, ONE_PULSE_TICKS,
                        Expectation.OBSERVATION_ONLY),
                importScenario("wake-opt-one-pulse-1024-hashed-io-first", 1024,
                        TickOrder.IO_THEN_MACHINE, PhaseMode.HASHED,
                        LoadShape.SINGLE_TICK_PULSE, ONE_PULSE_TICKS,
                        Expectation.OBSERVATION_ONLY),
                importScenario("wake-opt-four-burst-1024-sync-io-first", 1024,
                        TickOrder.IO_THEN_MACHINE, PhaseMode.SYNCHRONIZED,
                        LoadShape.FOUR_TICK_BURST, 640, Expectation.OBSERVATION_ONLY,
                        80),
                importScenario("wake-opt-four-burst-1024-hashed", 1024,
                        TickOrder.MACHINE_THEN_IO, PhaseMode.HASHED,
                        LoadShape.FOUR_TICK_BURST, 640, Expectation.OBSERVATION_ONLY,
                        80),
                importScenario("wake-opt-target-outage-1024", 1024,
                        TickOrder.MACHINE_THEN_IO, PhaseMode.HASHED,
                        LoadShape.TARGET_OUTAGE, 420, Expectation.OBSERVATION_ONLY),
                importScenario("wake-opt-hot-restart-1024", 1024,
                        TickOrder.MACHINE_THEN_IO, PhaseMode.HASHED,
                        LoadShape.HOT_IDLE_RESTART, 240, Expectation.OBSERVATION_ONLY));
    }

    private static Scenario importScenario(
            String id, int machines, TickOrder order, PhaseMode phase,
            LoadShape shape, int ticks, Expectation expectation) {
        return importScenario(id, machines, order, phase, shape, ticks, expectation,
                ACCEPTANCE_START);
    }

    private static Scenario importScenario(
            String id, int machines, TickOrder order, PhaseMode phase,
            LoadShape shape, int ticks, Expectation expectation, int acceptanceStart) {
        return new Scenario(id, DirectionMode.IMPORT, 1, machines, ticks,
                acceptanceStart, order, phase, shape, 1,
                32, 64, 1, 64, OutputMode.UNIQUE_EACH_CYCLE,
                ProductionMode.ATOMIC, 0, 0, 0, 0,
                expectation, 0.0, 1.0, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Double.MAX_VALUE);
    }

    private static void assertNoSemanticLoss(Result result) {
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

    private static String csv(List<TelemetryRow> rows) {
        var csv = new StringBuilder(TelemetryRow.HEADER).append('\n');
        for (var row : rows) {
            csv.append(row.csvRow()).append('\n');
        }
        return csv.toString();
    }

    private static String markdown(List<TelemetryRow> rows) {
        var markdown = new StringBuilder("# Demand-wake optimization telemetry\n\n")
                .append("This is a diagnostic report, not an acceptance gate. Lower is "
                        + "better for idle visits and work concentration; compare the "
                        + "same rows before and after a production change. Throughput, "
                        + "demand wait, ownership, and pressure must not regress. Values "
                        + "ending in _per_machine_tick are normalized diagnostics, not ms.\n\n")
                .append("| scenario | machines | visits total/productive/idle | idle/conn-tick | "
                        + "work mean/p99/max | work/conn-tick mean/p99 | p99/mean | "
                        + "p99 latency | demand wait | "
                        + "service gap | throughput window/machine | pressure |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var row : rows) {
            markdown.append(String.format(Locale.ROOT,
                    "| %s | %d | %d/%d/%d | %.6f | %d/%d/%d | %.6f/%.6f | %.6f | %d | %d | %d | "
                            + "%.6f/%.6f | %.6f |\n",
                    row.scenario(), row.machines(), row.schedulerVisits(),
                    row.productiveVisits(), row.idleVisits(), row.idleVisitsPerMachineTick(),
                    row.meanWork(), row.p99Work(), row.maxWork(), row.meanWorkPerMachineTick(),
                    row.p99WorkPerMachineTick(), row.p99MeanWork(), row.p99Latency(),
                    row.demandWait(), row.serviceGap(), row.windowThroughput(),
                    row.machineThroughput(), row.pressureRatio()));
        }
        return markdown.toString();
    }

    private record TelemetryRow(
            String scenario,
            int machines,
            TickOrder order,
            PhaseMode phase,
            LoadShape shape,
            int ticks,
            long schedulerVisits,
            long productiveVisits,
            long idleVisits,
            double idleVisitRatio,
            double idleVisitsPerMachineTick,
            long meanWork,
            long p99Work,
            long maxWork,
            double meanWorkPerMachineTick,
            double p99WorkPerMachineTick,
            double p99MeanWork,
            int p99Latency,
            int demandWait,
            int serviceGap,
            double windowThroughput,
            double machineThroughput,
            long pressureEvents,
            double pressureRatio,
            long backlogItemTicks,
            long producedOutput,
            long extractedOutput,
            long finalOutput,
            long finalInput) {
        private static final String HEADER =
                "scenario,machines,tick_order,phase_mode,load_shape,ticks,"
                        + "scheduler_visits,productive_visits,idle_visits,idle_visit_ratio,"
                        + "idle_visits_per_machine_tick,mean_work,p99_work,max_work,"
                        + "mean_work_per_machine_tick,p99_work_per_machine_tick,p99_mean_ratio,p99_latency,"
                        + "max_demand_wait,max_service_gap,min_window,min_machine,"
                        + "pressure_events,pressure_ratio,backlog_item_ticks,produced_output,"
                        + "extracted_output,final_output,final_input";

        private static TelemetryRow from(Result result) {
            var scenario = result.scenario();
            return new TelemetryRow(scenario.id(), scenario.machines(),
                    scenario.tickOrder(), scenario.phaseMode(), scenario.loadShape(),
                    scenario.ticks(), result.schedulerVisits(), result.productiveVisits(),
                    result.idleVisits(), result.idleVisitRatio(),
                    (double) result.idleVisits() / (scenario.machines() * scenario.ticks()),
                    result.meanWork(), result.p99Work(), result.maximumWork(),
                    (double) result.meanWork() / scenario.machines(),
                    (double) result.p99Work() / scenario.machines(), result.p99ToMeanWork(),
                    result.p99ImportLatency(), result.maximumDemandWait(),
                    result.maximumServiceGap(), result.minimumWindowThroughput(),
                    result.minimumMachineThroughput(), result.pressureEvents(),
                    result.pressureEventRatio(), result.backlogItemTicks(),
                    result.producedOutput(), result.extractedOutput(), result.finalOutput(),
                    result.finalInput());
        }

        private String csvRow() {
            return String.format(Locale.ROOT,
                    "%s,%d,%s,%s,%s,%d,%d,%d,%d,%.8f,%.8f,%d,%d,%d,%.8f,%.8f,%.8f,%d,%d,%d,"
                            + "%.8f,%.8f,%d,%.8f,%d,%d,%d,%d,%d",
                    scenario, machines, order, phase, shape, ticks, schedulerVisits,
                    productiveVisits, idleVisits, idleVisitRatio, idleVisitsPerMachineTick,
                    meanWork, p99Work, maxWork, meanWorkPerMachineTick,
                    p99WorkPerMachineTick, p99MeanWork, p99Latency, demandWait, serviceGap,
                    windowThroughput, machineThroughput, pressureEvents, pressureRatio,
                    backlogItemTicks, producedOutput, extractedOutput, finalOutput,
                    finalInput);
        }
    }
}
