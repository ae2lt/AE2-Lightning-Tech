package com.moakiee.ae2lt.debug;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;

/**
 * Opt-in, server-thread-only timing probe for wireless overloaded-interface I/O.
 * Disabled runs pay only one predictable boolean branch at the measured call
 * site. Enable it with {@code -Dae2lt.wirelessIoBenchmark=true}.
 */
public final class WirelessIoPerformanceProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("ae2lt-wireless-io-benchmark");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private static final boolean ENABLED = Boolean.getBoolean("ae2lt.wirelessIoBenchmark");
    private static final int WARMUP_TICKS = positiveProperty(
            "ae2lt.wirelessIoBenchmark.warmupTicks", 200);
    private static final int SAMPLE_TICKS = positiveProperty(
            "ae2lt.wirelessIoBenchmark.sampleTicks", 1_200);
    private static final String SCENARIO = System.getProperty(
            "ae2lt.wirelessIoBenchmark.scenario", "manual");
    private static final String COMMIT = System.getProperty(
            "ae2lt.wirelessIoBenchmark.commit", "working-tree");
    private static final String GIT_HEAD = System.getProperty(
            "ae2lt.wirelessIoBenchmark.gitHead", COMMIT);
    private static final boolean WORKTREE_DIRTY = Boolean.parseBoolean(System.getProperty(
            "ae2lt.wirelessIoBenchmark.worktreeDirty", "false"));
    private static final boolean DIAGNOSTICS = Boolean.parseBoolean(System.getProperty(
            "ae2lt.wirelessIoBenchmark.diagnostics", "false"));
    private static final Path OUTPUT_DIRECTORY = Path.of(System.getProperty(
            "ae2lt.wirelessIoBenchmark.output", "benchmark-reports/wireless-interface-io"));

    private static final long[] tickNanos = new long[SAMPLE_TICKS];
    private static final long[] wirelessIoNanos = new long[SAMPLE_TICKS];
    private static final int[] interfaceCalls = new int[SAMPLE_TICKS];
    private static final int[] fastInterfaceCalls = new int[SAMPLE_TICKS];
    private static final int[] connectionVisits = new int[SAMPLE_TICKS];

    private static long currentTickStarted;
    private static long currentWirelessIoNanos;
    private static int currentInterfaceCalls;
    private static int currentFastInterfaceCalls;
    private static int currentConnectionVisits;
    private static int warmupSeen;
    private static int samples;
    private static boolean workloadSeen;
    private static boolean reportWritten;
    private static long gcCollectionsAtStart;
    private static long gcMillisAtStart;
    private static long peakUsedHeap;
    private static long workloadProducedItems;
    private static long workloadExtractedItems;
    private static long workloadNetworkImportedItems;
    private static long workloadBufferedItems;
    private static long workloadMaxBufferedItems = -1;
    private static long workloadMaxBufferedKeys = -1;
    private static String workloadBufferLatencyAttribution = "not-recorded";
    private static long workloadBufferLatencySamples = -1;
    private static long workloadBufferLatencyP50 = -1;
    private static long workloadBufferLatencyP95 = -1;
    private static long workloadBufferLatencyP99 = -1;
    private static long workloadBufferLatencyMax = -1;
    private static long workloadBufferPendingBatches = -1;
    private static long workloadBufferMaxPendingWait = -1;
    private static String workloadNetworkLatencyAttribution = "not-recorded";
    private static long workloadNetworkLatencySamples = -1;
    private static long workloadNetworkLatencyP50 = -1;
    private static long workloadNetworkLatencyP95 = -1;
    private static long workloadNetworkLatencyP99 = -1;
    private static long workloadNetworkLatencyMax = -1;
    private static long workloadNetworkPendingBatches = -1;
    private static long workloadNetworkMaxPendingWait = -1;
    private static long workloadPlannedProductionItems = -1;
    private static long workloadActualProductionItems = -1;
    private static long workloadBlockedProductionEvents = -1;
    private static long workloadProductionOpportunities = -1;
    private static double workloadMinimumWindowThroughput = -1.0;
    private static double workloadMinimumTargetThroughput = -1.0;
    private static long workloadRecoveryTick = -1;
    private static long workloadBufferDrainTick = -1;
    private static long workloadFinalRemainingItems = -1;

    private WirelessIoPerformanceProbe() {
    }

    public static boolean shouldMeasure() {
        return ENABLED && !reportWritten && samples < SAMPLE_TICKS;
    }

    private static boolean acceptsWorkloadObservation() {
        return ENABLED && !reportWritten;
    }

    public static void beginServerTick() {
        if (!shouldMeasure()) {
            return;
        }
        currentTickStarted = System.nanoTime();
        currentWirelessIoNanos = 0;
        currentInterfaceCalls = 0;
        currentFastInterfaceCalls = 0;
        currentConnectionVisits = 0;
    }

    public static void recordWirelessInterfaceIo(
            long elapsedNanos, int connections, boolean fastMode) {
        if (!shouldMeasure()) {
            return;
        }
        currentWirelessIoNanos += Math.max(0L, elapsedNanos);
        currentInterfaceCalls++;
        if (fastMode) {
            currentFastInterfaceCalls++;
        }
        currentConnectionVisits += Math.max(0, connections);
        if (!workloadSeen) {
            workloadSeen = true;
            gcCollectionsAtStart = gcCollections();
            gcMillisAtStart = gcMillis();
            LOGGER.info(
                    "Wireless I/O benchmark workload detected: scenario={}, warmup={} ticks, sample={} ticks",
                    SCENARIO, WARMUP_TICKS, SAMPLE_TICKS);
        }
    }

    /**
     * Records counters measured by a real GameTest fixture without adding
     * storage scans to the server tick probe.  The fixture derives extracted
     * and network-imported ownership from its production-path observations;
     * the timing probe persists the latest counters beside MSPT and GC data.
     */
    public static void recordImportWorkload(
            long producedItems,
            long extractedItems,
            long networkImportedItems,
            long bufferedItems,
            long maxBufferedItems,
            long maxBufferedKeys,
            String bufferLatencyAttribution,
            long bufferLatencySamples,
            long bufferLatencyP50,
            long bufferLatencyP95,
            long bufferLatencyP99,
            long bufferLatencyMax,
            long bufferPendingBatches,
            long bufferMaxPendingWait,
            String networkLatencyAttribution,
            long networkLatencySamples,
            long networkLatencyP50,
            long networkLatencyP95,
            long networkLatencyP99,
            long networkLatencyMax,
            long networkPendingBatches,
            long networkMaxPendingWait) {
        if (!acceptsWorkloadObservation()) {
            return;
        }
        workloadProducedItems = Math.max(0L, producedItems);
        workloadExtractedItems = Math.max(0L, extractedItems);
        workloadNetworkImportedItems = Math.max(0L, networkImportedItems);
        workloadBufferedItems = Math.max(0L, bufferedItems);
        if (maxBufferedItems >= 0) {
            workloadMaxBufferedItems = Math.max(workloadMaxBufferedItems, maxBufferedItems);
        }
        if (maxBufferedKeys >= 0) {
            workloadMaxBufferedKeys = Math.max(workloadMaxBufferedKeys, maxBufferedKeys);
        }
        workloadBufferLatencyAttribution = safeAttribution(bufferLatencyAttribution);
        workloadBufferLatencySamples = bufferLatencySamples;
        workloadBufferLatencyP50 = bufferLatencyP50;
        workloadBufferLatencyP95 = bufferLatencyP95;
        workloadBufferLatencyP99 = bufferLatencyP99;
        workloadBufferLatencyMax = bufferLatencyMax;
        workloadBufferPendingBatches = bufferPendingBatches;
        workloadBufferMaxPendingWait = bufferMaxPendingWait;
        workloadNetworkLatencyAttribution = safeAttribution(networkLatencyAttribution);
        workloadNetworkLatencySamples = networkLatencySamples;
        workloadNetworkLatencyP50 = networkLatencyP50;
        workloadNetworkLatencyP95 = networkLatencyP95;
        workloadNetworkLatencyP99 = networkLatencyP99;
        workloadNetworkLatencyMax = networkLatencyMax;
        workloadNetworkPendingBatches = networkPendingBatches;
        workloadNetworkMaxPendingWait = networkMaxPendingWait;
    }

    /** Records a fixed producer plan without making it part of tick timing. */
    public static void recordProductionPlan(
            long plannedItems,
            long actualItems,
            long blockedEvents,
            long opportunities,
            double minimumWindowThroughput,
            double minimumTargetThroughput,
            long recoveryTick,
            long bufferDrainTick,
            long finalRemainingItems) {
        if (!acceptsWorkloadObservation()) {
            return;
        }
        workloadPlannedProductionItems = plannedItems;
        workloadActualProductionItems = actualItems;
        workloadBlockedProductionEvents = blockedEvents;
        workloadProductionOpportunities = opportunities;
        workloadMinimumWindowThroughput = minimumWindowThroughput;
        workloadMinimumTargetThroughput = minimumTargetThroughput;
        workloadRecoveryTick = recoveryTick;
        workloadBufferDrainTick = bufferDrainTick;
        workloadFinalRemainingItems = finalRemainingItems;
    }

    public static void endServerTick(MinecraftServer server) {
        if (!shouldMeasure() || currentTickStarted == 0L || !workloadSeen) {
            return;
        }
        long elapsed = Math.max(0L, System.nanoTime() - currentTickStarted);
        peakUsedHeap = Math.max(peakUsedHeap,
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        if (warmupSeen < WARMUP_TICKS) {
            warmupSeen++;
            return;
        }
        if (samples >= SAMPLE_TICKS) {
            return;
        }

        tickNanos[samples] = elapsed;
        wirelessIoNanos[samples] = currentWirelessIoNanos;
        interfaceCalls[samples] = currentInterfaceCalls;
        fastInterfaceCalls[samples] = currentFastInterfaceCalls;
        connectionVisits[samples] = currentConnectionVisits;
        samples++;
    }

    public static void finish(MinecraftServer server) {
        if (ENABLED && workloadSeen && !reportWritten && samples > 0) {
            writeReport(server, samples < SAMPLE_TICKS);
        }
    }

    private static void writeReport(MinecraftServer server, boolean partial) {
        if (reportWritten) {
            return;
        }
        reportWritten = true;
        int count = samples;
        var tickStats = Stats.of(tickNanos, count);
        var ioStats = Stats.of(wirelessIoNanos, count);
        long calls = sum(interfaceCalls, count);
        long fastCalls = sum(fastInterfaceCalls, count);
        long visits = sum(connectionVisits, count);
        long over50ms = countAbove(tickNanos, count, 50_000_000L);
        long over100ms = countAbove(tickNanos, count, 100_000_000L);
        long gcCollections = Math.max(0L, gcCollections() - gcCollectionsAtStart);
        long gcMillis = Math.max(0L, gcMillis() - gcMillisAtStart);
        double capacityTps = tickStats.meanNanos <= 0
                ? 20.0
                : Math.min(20.0, 1_000_000_000.0 / tickStats.meanNanos);

        String stamp = FILE_TIME.format(Instant.now());
        Path directory = OUTPUT_DIRECTORY.toAbsolutePath().normalize();
        Path summary = directory.resolve(stamp + "-" + safeFilePart(SCENARIO) + ".json");
        Path samplesFile = directory.resolve(stamp + "-" + safeFilePart(SCENARIO) + "-ticks.csv");
        try {
            Files.createDirectories(directory);
            Files.writeString(summary, json(server, partial, count, tickStats,
                    ioStats, calls, fastCalls, visits, over50ms, over100ms,
                    capacityTps, gcCollections, gcMillis), StandardCharsets.UTF_8);
            Files.writeString(samplesFile, csv(count), StandardCharsets.UTF_8);
            LOGGER.info(
                    "Wireless I/O benchmark complete: scenario={}, ticks={}, MSPT mean/p95/p99/max={}/{}/{}/{} ms, wireless I/O p99={} ms, >50ms={} ({}%), capacityTPS={}, report={}",
                    SCENARIO, count, formatMs(tickStats.meanNanos),
                    formatMs(tickStats.p95Nanos), formatMs(tickStats.p99Nanos),
                    formatMs(tickStats.maxNanos), formatMs(ioStats.p99Nanos),
                    over50ms, formatPercent(over50ms, count),
                    String.format(Locale.ROOT, "%.3f", capacityTps), summary);
        } catch (IOException e) {
            LOGGER.error("Failed to write wireless I/O benchmark report to {}", directory, e);
        }
    }

    private static String json(
            MinecraftServer server, boolean partial, int count,
            Stats tick, Stats io, long calls, long fastCalls, long visits,
            long over50ms, long over100ms, double capacityTps,
            long gcCollections, long gcMillis) {
        return "{\n"
                + "  \"schema\": 2,\n"
                + "  \"scenario\": \"" + escapeJson(SCENARIO) + "\",\n"
                + "  \"commit\": \"" + escapeJson(COMMIT) + "\",\n"
                + "  \"gitHead\": \"" + escapeJson(GIT_HEAD) + "\",\n"
                + "  \"workingTreeDirty\": " + WORKTREE_DIRTY + ",\n"
                + "  \"partial\": " + partial + ",\n"
                + "  \"samples\": " + count + ",\n"
                + "  \"warmupTicks\": " + warmupSeen + ",\n"
                + "  \"serverVersion\": \"" + escapeJson(server.getServerVersion()) + "\",\n"
                + "  \"javaVersion\": \"" + escapeJson(System.getProperty("java.version")) + "\",\n"
                + "  \"jvmArguments\": \"" + escapeJson(String.join(" ",
                        ManagementFactory.getRuntimeMXBean().getInputArguments())) + "\",\n"
                + "  \"os\": \"" + escapeJson(System.getProperty("os.name") + " "
                        + System.getProperty("os.arch")) + "\",\n"
                + "  \"availableProcessors\": " + Runtime.getRuntime().availableProcessors() + ",\n"
                + "  \"maxHeapBytes\": " + Runtime.getRuntime().maxMemory() + ",\n"
                + "  \"tickMs\": " + tick.json() + ",\n"
                + "  \"wirelessIoMs\": " + io.json() + ",\n"
                + "  \"capacityTps\": " + decimal(capacityTps) + ",\n"
                + "  \"capacityTpsKind\": \"derived_from_mean_mspt_not_measured_tps\",\n"
                + "  \"workloadObservationMode\": \""
                + (DIAGNOSTICS ? "diagnostic_target_key" : "formal_aggregate_only") + "\",\n"
                + "  \"ticksOver50Ms\": " + over50ms + ",\n"
                + "  \"ticksOver100Ms\": " + over100ms + ",\n"
                + "  \"ticksOver50MsRatio\": " + decimal(ratio(over50ms, count)) + ",\n"
                + "  \"interfaceCalls\": " + calls + ",\n"
                + "  \"fastInterfaceCalls\": " + fastCalls + ",\n"
                + "  \"configuredConnectionVisits\": " + visits + ",\n"
                + "  \"gcCollections\": " + gcCollections + ",\n"
                + "  \"gcMillis\": " + gcMillis + ",\n"
                + "  \"peakUsedHeapBytes\": " + peakUsedHeap + ",\n"
                + "  \"workloadProducedItems\": " + workloadProducedItems + ",\n"
                + "  \"workloadExtractedItems\": " + workloadExtractedItems + ",\n"
                + "  \"workloadNetworkImportedItems\": " + workloadNetworkImportedItems + ",\n"
                + "  \"workloadBufferedItems\": " + workloadBufferedItems + ",\n"
                + "  \"workloadMaxBufferedItems\": " + workloadMaxBufferedItems + ",\n"
                + "  \"workloadMaxBufferedKeys\": " + workloadMaxBufferedKeys + ",\n"
                + "  \"workloadPlannedProductionItems\": " + workloadPlannedProductionItems + ",\n"
                + "  \"workloadActualProductionItems\": " + workloadActualProductionItems + ",\n"
                + "  \"workloadBlockedProductionEvents\": " + workloadBlockedProductionEvents + ",\n"
                + "  \"workloadProductionOpportunities\": " + workloadProductionOpportunities + ",\n"
                + "  \"workloadMinimumWindowThroughput\": "
                + decimal(workloadMinimumWindowThroughput) + ",\n"
                + "  \"workloadMinimumTargetThroughput\": "
                + decimal(workloadMinimumTargetThroughput) + ",\n"
                + "  \"workloadRecoveryTick\": " + workloadRecoveryTick + ",\n"
                + "  \"workloadBufferDrainTick\": " + workloadBufferDrainTick + ",\n"
                + "  \"workloadBufferDrainTickMeaning\": \"first_tick_buffer_keys_zero_not_me_network_empty\",\n"
                + "  \"workloadSameKeyBatchConvention\": \"earliest_active_batch_owns_merged_amount\",\n"
                + "  \"workloadFinalRemainingItems\": " + workloadFinalRemainingItems + ",\n"
                + "  \"workloadOutputToBufferLatencyTicks\": {\"attribution\": \""
                + escapeJson(workloadBufferLatencyAttribution) + "\", \"samples\": "
                + workloadBufferLatencySamples + ", \"p50\": " + workloadBufferLatencyP50
                + ", \"p95\": " + workloadBufferLatencyP95 + ", \"p99\": "
                + workloadBufferLatencyP99 + ", \"max\": " + workloadBufferLatencyMax
                + ", \"pendingBatches\": " + workloadBufferPendingBatches
                + ", \"maxPendingWait\": " + workloadBufferMaxPendingWait + "},\n"
                + "  \"workloadOutputToNetworkLatencyTicks\": {\"attribution\": \""
                + escapeJson(workloadNetworkLatencyAttribution) + "\", \"samples\": "
                + workloadNetworkLatencySamples + ", \"p50\": "
                + workloadNetworkLatencyP50 + ", \"p95\": " + workloadNetworkLatencyP95
                + ", \"p99\": " + workloadNetworkLatencyP99 + ", \"max\": "
                + workloadNetworkLatencyMax + ", \"pendingBatches\": "
                + workloadNetworkPendingBatches + ", \"maxPendingWait\": "
                + workloadNetworkMaxPendingWait + "}\n"
                + "}\n";
    }

    private static String csv(int count) {
        var out = new StringBuilder(count * 48 + 96);
        out.append("sample,tick_nanos,wireless_io_nanos,interface_calls,fast_interface_calls,configured_connections\n");
        for (int i = 0; i < count; i++) {
            out.append(i).append(',')
                    .append(tickNanos[i]).append(',')
                    .append(wirelessIoNanos[i]).append(',')
                    .append(interfaceCalls[i]).append(',')
                    .append(fastInterfaceCalls[i]).append(',')
                    .append(connectionVisits[i]).append('\n');
        }
        return out.toString();
    }

    private static int positiveProperty(String key, int fallback) {
        int value = Integer.getInteger(key, fallback);
        return value > 0 ? value : fallback;
    }

    private static long gcCollections() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = bean.getCollectionCount();
            if (value > 0) {
                total += value;
            }
        }
        return total;
    }

    private static long gcMillis() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = bean.getCollectionTime();
            if (value > 0) {
                total += value;
            }
        }
        return total;
    }

    private static long sum(int[] values, int count) {
        long total = 0;
        for (int i = 0; i < count; i++) {
            total += values[i];
        }
        return total;
    }

    private static long countAbove(long[] values, int count, long threshold) {
        long result = 0;
        for (int i = 0; i < count; i++) {
            if (values[i] > threshold) {
                result++;
            }
        }
        return result;
    }

    private static String safeFilePart(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]+", "-");
        return safe.isBlank() ? "manual" : safe;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String safeAttribution(String value) {
        return value == null || value.isBlank() ? "not-recorded" : value;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String formatMs(double nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static String formatPercent(long numerator, long denominator) {
        return String.format(Locale.ROOT, "%.3f", ratio(numerator, denominator) * 100.0);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : (double) numerator / denominator;
    }

    private record Stats(
            double meanNanos,
            long p50Nanos,
            long p95Nanos,
            long p99Nanos,
            long maxNanos) {
        static Stats of(long[] source, int count) {
            if (count <= 0) {
                return new Stats(0, 0, 0, 0, 0);
            }
            long total = 0;
            long[] sorted = Arrays.copyOf(source, count);
            for (long value : sorted) {
                total += value;
            }
            Arrays.sort(sorted);
            return new Stats((double) total / count,
                    percentile(sorted, 0.50), percentile(sorted, 0.95),
                    percentile(sorted, 0.99), sorted[count - 1]);
        }

        private static long percentile(long[] sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.length) - 1;
            return sorted[Math.clamp(index, 0, sorted.length - 1)];
        }

        String json() {
            return "{\"mean\":" + decimal(meanNanos / 1_000_000.0)
                    + ",\"p50\":" + decimal(p50Nanos / 1_000_000.0)
                    + ",\"p95\":" + decimal(p95Nanos / 1_000_000.0)
                    + ",\"p99\":" + decimal(p99Nanos / 1_000_000.0)
                    + ",\"max\":" + decimal(maxNanos / 1_000_000.0) + "}";
        }
    }
}
