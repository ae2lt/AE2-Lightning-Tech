package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Executable companion to docs/wireless-interface-io-benchmark.md. */
@Tag("wireless-interface-io-stress")
class WirelessInterfaceIoStressTest {

    @Test
    void deterministicWorkloadMatrix() throws IOException {
        WirelessInterfaceIoStressModel.verifyProductionCadenceBoundaries();

        var results = new ArrayList<WirelessInterfaceIoStressModel.Result>();
        var pressureResults = new ArrayList<WirelessInterfaceSchedulingPressureModel.Result>();
        var correctnessFailures = new ArrayList<String>();
        var acceptanceFailures = new ArrayList<String>();
        for (var scenario : WirelessInterfaceIoStressModel.scenarios()) {
            var result = WirelessInterfaceIoStressModel.run(scenario);
            results.add(result);
            System.out.println(result.summaryLine());
            for (var failure : result.correctnessFailures()) {
                correctnessFailures.add(scenario.id() + ": " + failure);
            }
            for (var failure : result.acceptanceFailures()) {
                acceptanceFailures.add(scenario.id() + ": " + failure);
            }
        }
        String suite = System.getProperty("ae2lt.wirelessIo.suite", "full");
        var pressureScenarios = WirelessInterfaceSchedulingPressureModel.scenarios(suite);
        WirelessInterfaceSchedulingPressureModel.verifyCoverage(pressureScenarios, suite);
        for (var scenario : pressureScenarios) {
            var result = WirelessInterfaceSchedulingPressureModel.run(scenario);
            pressureResults.add(result);
            System.out.println(result.summaryLine());
            for (var failure : result.correctnessFailures()) {
                correctnessFailures.add(scenario.id() + ": " + failure);
            }
            for (var failure : result.acceptanceFailures()) {
                acceptanceFailures.add(scenario.id() + ": " + failure);
            }
        }
        writeReports(results, pressureResults, acceptanceFailures, suite);

        assertTrue(correctnessFailures.isEmpty(), () ->
                "wireless interface I/O correctness failures:\n"
                        + String.join("\n", correctnessFailures));
        if (Boolean.getBoolean("ae2lt.wirelessIo.enforce")) {
            assertTrue(acceptanceFailures.isEmpty(), () ->
                    "wireless interface I/O acceptance failures:\n"
                            + String.join("\n", acceptanceFailures));
        } else if (!acceptanceFailures.isEmpty()) {
            System.out.println("Acceptance gates are report-only in baseline mode:\n"
                    + String.join("\n", acceptanceFailures));
        }
    }

    private static void writeReports(
            ArrayList<WirelessInterfaceIoStressModel.Result> results,
            ArrayList<WirelessInterfaceSchedulingPressureModel.Result> pressureResults,
            ArrayList<String> acceptanceFailures,
            String suite) throws IOException {
        String configured = System.getProperty("ae2lt.wirelessIo.reportDir",
                "build/reports/wireless-interface-io");
        Path directory = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(directory);

        var csv = new StringBuilder();
        csv.append(results.getFirst().csvHeader()).append('\n');
        for (var result : results) {
            csv.append(result.csvRow()).append('\n');
        }
        Files.writeString(directory.resolve("model-metrics.csv"), csv,
                StandardCharsets.UTF_8);

        var pressureCsv = new StringBuilder();
        pressureCsv.append(WirelessInterfaceSchedulingPressureModel.Result.csvHeader())
                .append('\n');
        for (var result : pressureResults) {
            pressureCsv.append(result.csvRow()).append('\n');
        }
        Files.writeString(directory.resolve("scheduling-pressure.csv"), pressureCsv,
                StandardCharsets.UTF_8);

        var summary = new StringBuilder("# Wireless interface I/O model report\n\n");
        summary.append("This report validates workload semantics and scheduler cadence. ")
                .append("It is not a server MSPT measurement; use the live benchmark run ")
                .append("defined in the benchmark specification for TPS acceptance.\n\n")
                .append("Suite: `").append(suite).append("`; semantic scenarios: ")
                .append(results.size()).append("; scheduling-pressure scenarios: ")
                .append(pressureResults.size()).append(".\n\n")
                .append("## Transfer semantics and fault scenarios\n\n```text\n");
        for (var result : results) {
            summary.append(result.summaryLine()).append('\n');
        }
        summary.append("```\n\n## Scheduling pressure matrix\n\n```text\n");
        for (var result : pressureResults) {
            summary.append(result.summaryLine()).append('\n');
        }
        summary.append("```\n\n## Optimization hotspots\n\n")
                .append("### Largest pressure shortfall\n\n```text\n");
        pressureResults.stream()
                .filter(result -> result.pressureShortfall() > 0)
                .sorted((left, right) -> Long.compare(
                        right.pressureShortfall(), left.pressureShortfall()))
                .limit(10)
                .forEach(result -> summary.append(result.scenario().id())
                        .append(" shortfall=").append(result.pressureShortfall())
                        .append(" ratio=").append(String.format(java.util.Locale.ROOT,
                                "%.3f%%", result.pressureEventRatio() * 100.0))
                        .append(" streak=").append(result.maximumPressureStreak())
                        .append(" latencyP99=").append(result.p99ImportLatency())
                        .append(" demandWait=").append(result.maximumDemandWait())
                        .append('\n'));
        summary.append("```\n\n### Largest tick-work concentration\n\n```text\n");
        pressureResults.stream()
                .filter(result -> result.meanWork() > 0)
                .sorted((left, right) -> Double.compare(
                        right.p99ToMeanWork(), left.p99ToMeanWork()))
                .limit(10)
                .forEach(result -> summary.append(result.scenario().id())
                        .append(" p99/mean=")
                        .append(String.format(java.util.Locale.ROOT, "%.3f",
                                result.p99ToMeanWork()))
                        .append(" mean=").append(result.meanWork())
                        .append(" p99=").append(result.p99Work())
                        .append(" max=").append(result.maximumWork())
                        .append('\n'));
        summary.append("```\n\n### Largest idle scheduling volume\n\n```text\n");
        pressureResults.stream()
                .filter(result -> result.idleVisits() > 0)
                .sorted((left, right) -> Long.compare(
                        right.idleVisits(), left.idleVisits()))
                .limit(10)
                .forEach(result -> summary.append(result.scenario().id())
                        .append(" idle=").append(result.idleVisits())
                        .append(" total=").append(result.schedulerVisits())
                        .append(" productive=").append(result.productiveVisits())
                        .append(" ratio=").append(String.format(java.util.Locale.ROOT,
                                "%.3f%%", result.idleVisitRatio() * 100.0))
                        .append('\n'));
        summary.append("```\n\n### Largest output backlog exposure\n\n```text\n");
        pressureResults.stream()
                .filter(result -> result.backlogItemTicks() > 0)
                .sorted((left, right) -> Long.compare(
                        right.backlogItemTicks(), left.backlogItemTicks()))
                .limit(10)
                .forEach(result -> summary.append(result.scenario().id())
                        .append(" itemTicks=").append(result.backlogItemTicks())
                        .append(" nonempty=").append(String.format(
                                java.util.Locale.ROOT, "%.3f%%",
                                result.outputNonemptyRatio() * 100.0))
                        .append(" full=").append(String.format(
                                java.util.Locale.ROOT, "%.3f%%",
                                result.outputFullRatio() * 100.0))
                        .append(" demandWait=").append(result.maximumDemandWait())
                        .append('\n'));
        summary.append("```\n\n## Model acceptance\n\n");
        if (acceptanceFailures.isEmpty()) {
            summary.append("PASS\n");
        } else {
            summary.append("FAIL\n\n");
            for (var failure : acceptanceFailures) {
                summary.append("- ").append(failure).append('\n');
            }
        }
        Files.writeString(directory.resolve("model-summary.md"), summary,
                StandardCharsets.UTF_8);
    }
}
