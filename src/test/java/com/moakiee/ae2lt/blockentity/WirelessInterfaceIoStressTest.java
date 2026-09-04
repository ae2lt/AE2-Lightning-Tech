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
        writeReports(results, acceptanceFailures);

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
            ArrayList<String> acceptanceFailures) throws IOException {
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

        var summary = new StringBuilder("# Wireless interface I/O model report\n\n");
        summary.append("This report validates workload semantics and scheduler cadence. ")
                .append("It is not a server MSPT measurement; use the live benchmark run ")
                .append("defined in the benchmark specification for TPS acceptance.\n\n")
                .append("## Scenarios\n\n```text\n");
        for (var result : results) {
            summary.append(result.summaryLine()).append('\n');
        }
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
