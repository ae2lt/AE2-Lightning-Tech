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

    private WirelessIoPerformanceProbe() {
    }

    public static boolean shouldMeasure() {
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
        if (samples == SAMPLE_TICKS) {
            writeReport(server, false);
        }
    }

    public static void finish(MinecraftServer server) {
        if (ENABLED && workloadSeen && !reportWritten && samples > 0) {
            writeReport(server, true);
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
                + "  \"schema\": 1,\n"
                + "  \"scenario\": \"" + escapeJson(SCENARIO) + "\",\n"
                + "  \"commit\": \"" + escapeJson(COMMIT) + "\",\n"
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
                + "  \"ticksOver50Ms\": " + over50ms + ",\n"
                + "  \"ticksOver100Ms\": " + over100ms + ",\n"
                + "  \"ticksOver50MsRatio\": " + decimal(ratio(over50ms, count)) + ",\n"
                + "  \"interfaceCalls\": " + calls + ",\n"
                + "  \"fastInterfaceCalls\": " + fastCalls + ",\n"
                + "  \"configuredConnectionVisits\": " + visits + ",\n"
                + "  \"gcCollections\": " + gcCollections + ",\n"
                + "  \"gcMillis\": " + gcMillis + ",\n"
                + "  \"peakUsedHeapBytes\": " + peakUsedHeap + "\n"
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
