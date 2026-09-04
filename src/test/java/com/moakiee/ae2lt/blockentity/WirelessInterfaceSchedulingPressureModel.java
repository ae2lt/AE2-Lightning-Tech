package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEKeyType;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ConnectionState;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ExportRejectState;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IOSpeedMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IoDirection;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IoPhase;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IoScheduledEntry;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.WirelessConnection;

/**
 * Pairwise scheduling-pressure matrix for wireless FAST item I/O.
 *
 * <p>This model complements {@link WirelessInterfaceIoStressModel}: that model
 * validates transfer semantics, buffering and outage recovery, while this one
 * deliberately varies machine slot pressure, producer phase, tick order and
 * interface topology. It calls the production cooldown and next-deadline
 * policy for every modeled connection.</p>
 */
final class WirelessInterfaceSchedulingPressureModel {
    static final int REPORT_SCHEMA = 2;

    private static final AEKeyType ITEM_TYPE = AEKeyType.items();
    private static final int WINDOW_TICKS = 100;

    enum DirectionMode {
        IMPORT,
        EXPORT,
        BIDIRECTIONAL
    }

    enum TickOrder {
        MACHINE_THEN_IO,
        IO_THEN_MACHINE
    }

    enum PhaseMode {
        SYNCHRONIZED,
        STAGGERED,
        HASHED
    }

    enum OutputMode {
        UNIQUE_EACH_CYCLE,
        STACKABLE_FIXED
    }

    enum ProductionMode {
        ATOMIC,
        PARTIAL
    }

    enum LoadShape {
        CONTINUOUS,
        PERIODIC,
        COLD_START,
        HOT_IDLE_RESTART,
        CYCLIC,
        SINGLE_TICK_PULSE,
        FOUR_TICK_BURST,
        JITTER_10,
        JITTER_20,
        RATE_SWITCH,
        SUCCESS_STREAK_31,
        SUCCESS_STREAK_32,
        SUCCESS_STREAK_33,
        TARGET_FLAP,
        TARGET_OUTAGE,
        SCHEDULER_REBUILD,
        ZERO
    }

    enum Expectation {
        SUSTAINABLE,
        EXPECT_BACKPRESSURE,
        OBSERVATION_ONLY
    }

    record Scenario(
            String id,
            DirectionMode direction,
            int interfaces,
            int machines,
            int ticks,
            int acceptanceStart,
            TickOrder tickOrder,
            PhaseMode phaseMode,
            LoadShape loadShape,
            int period,
            int outputKeys,
            int outputSlots,
            long outputAmountPerKey,
            long outputStackCapacityPerKey,
            OutputMode outputMode,
            ProductionMode productionMode,
            int exportKeys,
            int activeExportKeys,
            long inputCapacityPerKey,
            long consumptionPerKey,
            Expectation expectation,
            double minimumThroughput,
            double maximumBlockedRatio,
            int maximumP99ImportLatency,
            int maximumDemandWait,
            double maximumP99ToMeanWork) {
        Scenario {
            if (id == null || id.isBlank() || interfaces <= 0 || machines < 0
                    || machines > interfaces * OverloadedInterfaceBlockEntity.MAX_WIRELESS_CONNECTIONS
                    || ticks <= 0 || acceptanceStart < 0 || acceptanceStart >= ticks
                    || period <= 0 || outputKeys < 0 || outputSlots < 0
                    || outputAmountPerKey < 0 || outputStackCapacityPerKey < 0
                    || exportKeys < 0 || activeExportKeys < 0
                    || activeExportKeys > exportKeys || inputCapacityPerKey < 0
                    || consumptionPerKey < 0
                    || minimumThroughput < 0 || minimumThroughput > 1
                    || maximumBlockedRatio < 0 || maximumBlockedRatio > 1) {
                throw new IllegalArgumentException("invalid pressure scenario: " + id);
            }
            if (outputMode == OutputMode.UNIQUE_EACH_CYCLE && outputKeys > 0
                    && outputAmountPerKey > outputStackCapacityPerKey) {
                throw new IllegalArgumentException(
                        "unique output amount exceeds one-slot capacity: " + id);
            }
        }

        boolean importEnabled() {
            return direction != DirectionMode.EXPORT;
        }

        boolean exportEnabled() {
            return direction != DirectionMode.IMPORT;
        }

        boolean activeAt(int tick) {
            return switch (loadShape) {
                case CONTINUOUS, PERIODIC, JITTER_10, JITTER_20, RATE_SWITCH,
                        TARGET_FLAP, TARGET_OUTAGE, SCHEDULER_REBUILD -> true;
                case COLD_START -> tick >= 160;
                case HOT_IDLE_RESTART -> tick < 40 || tick >= 160;
                case CYCLIC -> tick % 60 < 20;
                case SINGLE_TICK_PULSE, FOUR_TICK_BURST -> tick >= 40;
                case SUCCESS_STREAK_31 -> tick < 31 || tick >= 71;
                case SUCCESS_STREAK_32 -> tick < 32 || tick >= 72;
                case SUCCESS_STREAK_33 -> tick < 33 || tick >= 73;
                case ZERO -> false;
            };
        }

        boolean opportunityAt(int tick, int machine) {
            if (!activeAt(tick)) {
                return false;
            }
            if (loadShape == LoadShape.SINGLE_TICK_PULSE) {
                return Math.floorMod(tick - phaseFor(machine, 40), 40) == 0;
            }
            if (loadShape == LoadShape.FOUR_TICK_BURST) {
                return Math.floorMod(tick - phaseFor(machine, 40), 40) < 4;
            }
            if (loadShape == LoadShape.JITTER_10) {
                int relative = Math.floorMod(tick - phaseFor(machine, 30), 30);
                return relative == 0 || relative == 9 || relative == 19;
            }
            if (loadShape == LoadShape.JITTER_20) {
                int relative = Math.floorMod(tick - phaseFor(machine, 60), 60);
                return relative == 0 || relative == 19 || relative == 39;
            }
            int dynamicPeriod = loadShape == LoadShape.RATE_SWITCH
                    ? (tick < 160 || tick >= 320 ? 20 : 1) : period;
            int phase = phaseFor(machine, dynamicPeriod);
            return Math.floorMod(tick - phase, dynamicPeriod) == 0;
        }

        boolean targetAvailableAt(int tick) {
            return switch (loadShape) {
                case TARGET_FLAP -> tick % 10 != 5;
                case TARGET_OUTAGE -> tick < 160 || tick >= 200;
                default -> true;
            };
        }

        boolean rebuildSchedulerAt(int tick) {
            return loadShape == LoadShape.SCHEDULER_REBUILD
                    && tick > 0 && tick % 40 == 0;
        }

        private int phaseFor(int machine, int modulus) {
            return switch (phaseMode) {
                case SYNCHRONIZED -> 0;
                case STAGGERED -> machine % modulus;
                case HASHED -> Math.floorMod(mix(machine), modulus);
            };
        }

        private static int mix(int value) {
            int x = value * 0x45d9f3b;
            x ^= x >>> 16;
            x *= 0x45d9f3b;
            return x ^ (x >>> 16);
        }
    }

    static List<Scenario> scenarios(String suite) {
        var result = new ArrayList<Scenario>();
        addScaleMatrix(result);
        addOutputSlotMatrix(result);
        addCardinalityMatrix(result);
        addLargeStackMatrix(result);
        addPhaseAndOrderMatrix(result);
        addInterfaceTopologyMatrix(result);
        addExportMatrix(result);
        addBidirectionalMatrix(result);
        addAdversarialCadenceMatrix(result);
        addRecoveryAndRebuildMatrix(result);
        addExactLargeStackBoundaries(result);
        addHarnessBoundaries(result);
        if ("endurance".equalsIgnoreCase(suite)) {
            addEnduranceMatrix(result);
        }
        if ("quick".equalsIgnoreCase(suite)) {
            return result.stream()
                    .filter(s -> s.id().contains("scale-1-")
                            || s.id().contains("scale-1024-")
                            || s.id().contains("slot-32-")
                            || s.id().contains("slot-64-")
                            || s.id().contains("cold-start")
                            || s.id().contains("hot-restart")
                            || s.id().contains("single-tick-pulse")
                            || s.id().contains("jitter-20")
                            || s.id().contains("rate-switch")
                            || s.id().contains("target-flap")
                            || s.id().contains("scheduler-rebuild")
                            || s.id().contains("stack-boundary-10000")
                            || s.id().contains("large-stack")
                            || s.id().contains("export-36-")
                            || s.id().contains("bidirectional-1024-")
                            || s.id().contains("impossible"))
                    .toList();
        }
        return List.copyOf(result);
    }

    static void verifyCoverage(List<Scenario> scenarios, String suite) {
        long uniqueIds = scenarios.stream().map(Scenario::id).distinct().count();
        require(uniqueIds == scenarios.size(), "pressure scenario IDs must be unique");
        if ("quick".equalsIgnoreCase(suite)) {
            require(scenarios.size() >= 10, "quick matrix lost its core scenarios");
            return;
        }

        require(scenarios.size() >= 160,
                "full pressure matrix must contain at least 160 scenarios");
        requireAllInts(scenarios.stream().map(Scenario::machines).toList(),
                "connection scale", 0, 1, 64, 256, 1023, 1024);
        requireAllInts(scenarios.stream().map(Scenario::outputSlots).toList(),
                "output-slot boundary", 0, 1, 31, 32, 33, 63, 64, 65);
        requireAllInts(scenarios.stream().map(Scenario::outputKeys).toList(),
                "output cardinality", 0, 1, 8, 31, 32, 35, 36, 255, 256, 257);
        requireAllInts(scenarios.stream().map(Scenario::interfaces).toList(),
                "interface topology", 1, 2, 4, 16, 32, 64);
        requireAllInts(scenarios.stream().map(Scenario::period).toList(),
                "cadence boundary", 1, 4, 5, 6, 9, 10, 11, 19, 20, 21, 39, 40, 41);
        requireAllLongs(scenarios.stream().map(Scenario::outputAmountPerKey).toList(),
                "output amount per key", 1, 64, 999, 1_000, 1_001, 1024,
                9_999, 10_000, 10_001);
        requireAllLongs(scenarios.stream().map(Scenario::outputStackCapacityPerKey).toList(),
                "output stack capacity", 64, 999, 1_000, 1_001, 1024,
                9_999, 10_000, 10_001, 65_536);
        requireAllLongs(scenarios.stream().map(Scenario::inputCapacityPerKey).toList(),
                "input stack capacity", 1, 64, 999, 1_000, 1_001, 1024,
                9_999, 10_000, 10_001, 65_536);
        requireAllLongs(scenarios.stream().map(Scenario::consumptionPerKey).toList(),
                "consumption per key", 1, 64, 999, 1_000, 1_001, 1024,
                9_999, 10_000, 10_001);
        requireAllEnums(scenarios.stream().map(Scenario::direction).toList(),
                "direction", DirectionMode.values());
        requireAllEnums(scenarios.stream().map(Scenario::tickOrder).toList(),
                "tick order", TickOrder.values());
        requireAllEnums(scenarios.stream().map(Scenario::phaseMode).toList(),
                "producer phase", PhaseMode.values());
        requireAllEnums(scenarios.stream().map(Scenario::outputMode).toList(),
                "output mode", OutputMode.values());
        requireAllEnums(scenarios.stream().map(Scenario::productionMode).toList(),
                "production mode", ProductionMode.values());
        requireAllEnums(scenarios.stream().map(Scenario::loadShape).toList(),
                "load shape", LoadShape.values());
        requireAllEnums(scenarios.stream().map(Scenario::expectation).toList(),
                "expectation", Expectation.values());
        if ("endurance".equalsIgnoreCase(suite)) {
            require(scenarios.stream().filter(s -> s.ticks() >= 20_000).count() >= 3,
                    "endurance matrix must retain import, export and bidirectional long runs");
        }
    }

    private static void requireAllInts(
            List<Integer> actual, String dimension, int... expected) {
        for (int value : expected) {
            require(actual.contains(value), dimension + " is missing " + value);
        }
    }

    private static void requireAllLongs(
            List<Long> actual, String dimension, long... expected) {
        for (long value : expected) {
            require(actual.contains(value), dimension + " is missing " + value);
        }
    }

    private static <E extends Enum<E>> void requireAllEnums(
            List<E> actual, String dimension, E[] expected) {
        for (var value : expected) {
            require(actual.contains(value), dimension + " is missing " + value);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void addScaleMatrix(List<Scenario> out) {
        for (int machines : new int[] { 1, 64, 256, 1023, 1024 }) {
            out.add(importScenario("pressure-import-scale-" + machines + "-unique32",
                    1, machines, 320, 20, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.CONTINUOUS, 1,
                    32, 64, OutputMode.UNIQUE_EACH_CYCLE,
                    ProductionMode.ATOMIC, Expectation.SUSTAINABLE,
                    0.995, 0.0, 2, 2, 3.5));
        }
    }

    private static void addOutputSlotMatrix(List<Scenario> out) {
        for (int slots : new int[] { 31, 32, 33, 63, 64, 65 }) {
            Expectation expectation = slots < 32
                    ? Expectation.EXPECT_BACKPRESSURE : Expectation.SUSTAINABLE;
            double throughput = slots < 32 ? 0.0 : 0.995;
            double blocked = slots < 32 ? 1.0 : 0.0;
            out.add(importScenario("pressure-import-slot-" + slots + "-atomic",
                    1, 1024, 320, 20, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.CONTINUOUS, 1,
                    32, slots, OutputMode.UNIQUE_EACH_CYCLE,
                    ProductionMode.ATOMIC, expectation,
                    throughput, blocked, 2, 2, 3.5));
        }
        for (int slots : new int[] { 1, 31, 32, 33, 63, 64, 65 }) {
            Expectation expectation = slots < 32
                    ? Expectation.EXPECT_BACKPRESSURE : Expectation.SUSTAINABLE;
            out.add(importScenario("pressure-import-slot-" + slots + "-partial",
                    1, 256, 320, 20, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.CONTINUOUS, 1,
                    32, slots, OutputMode.UNIQUE_EACH_CYCLE,
                    ProductionMode.PARTIAL, expectation,
                    slots < 32 ? 0.0 : 0.995, slots < 32 ? 1.0 : 0.0,
                    2, 2, 3.5));
        }
        for (int slots : new int[] { 32, 33, 63, 64, 65 }) {
            out.add(importScenario("pressure-import-hot-restart-slot-" + slots,
                    1, 1024, 360, 20, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.HOT_IDLE_RESTART, 1,
                    32, slots, OutputMode.UNIQUE_EACH_CYCLE,
                    ProductionMode.ATOMIC, Expectation.SUSTAINABLE,
                    0.99, 0.0, 5, 40, 4.0));
            out.add(importScenario("pressure-import-cold-start-slot-" + slots,
                    1, 1024, 360, 20, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.COLD_START, 1,
                    32, slots, OutputMode.UNIQUE_EACH_CYCLE,
                    ProductionMode.ATOMIC, Expectation.SUSTAINABLE,
                    0.99, 0.0, 5, 40, 4.0));
        }
    }

    private static void addCardinalityMatrix(List<Scenario> out) {
        for (int keys : new int[] { 1, 8, 31, 32, 35, 36, 255, 256, 257 }) {
            int machines = keys >= 255 ? 256 : 1024;
            out.add(importScenario("pressure-import-cardinality-" + keys + "-unique",
                    1, machines, 260, 20, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.CONTINUOUS, 1,
                    keys, keys * 2, OutputMode.UNIQUE_EACH_CYCLE,
                    ProductionMode.ATOMIC, Expectation.SUSTAINABLE,
                    0.995, 0.0, 2, 2, 3.5));
        }
        for (int keys : new int[] { 1, 8, 32, 36 }) {
            out.add(importScenario("pressure-import-cardinality-" + keys + "-stackable",
                    1, 1024, 260, 20, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.CONTINUOUS, 1,
                    keys, keys, OutputMode.STACKABLE_FIXED,
                    ProductionMode.ATOMIC, Expectation.SUSTAINABLE,
                    0.995, 0.0, 2, 2, 3.5));
        }
    }

    private static void addLargeStackMatrix(List<Scenario> out) {
        long[][] importCases = {
                { 64, 64 },
                { 1_024, 64 },
                { 10_000, 1_024 },
                { 65_536, 10_000 }
        };
        for (long[] values : importCases) {
            long stackCapacity = values[0];
            long amountPerKey = values[1];
            out.add(importStackScenario("pressure-import-large-stack-cap-"
                            + stackCapacity + "-amount-" + amountPerKey,
                    1024, 320, LoadShape.CONTINUOUS,
                    stackCapacity, amountPerKey));
            out.add(importStackScenario("pressure-import-large-stack-cold-cap-"
                            + stackCapacity + "-amount-" + amountPerKey,
                    1024, 360, LoadShape.COLD_START,
                    stackCapacity, amountPerKey));
        }

        long[][] exportCases = {
                { 64, 64 },
                { 1_024, 64 },
                { 10_000, 1_024 },
                { 65_536, 10_000 }
        };
        for (long[] values : exportCases) {
            long capacity = values[0];
            long consumption = values[1];
            out.add(exportStackScenario("pressure-export-large-stack-cap-"
                            + capacity + "-consume-" + consumption,
                    capacity, consumption));
        }
    }

    private static Scenario importStackScenario(
            String id, int machines, int ticks, LoadShape shape,
            long stackCapacity, long amountPerKey) {
        return new Scenario(id, DirectionMode.IMPORT, 1, machines, ticks, 20,
                TickOrder.MACHINE_THEN_IO, PhaseMode.SYNCHRONIZED, shape, 1,
                36, 36, amountPerKey, stackCapacity,
                OutputMode.STACKABLE_FIXED, ProductionMode.ATOMIC,
                0, 0, 0, 0, Expectation.SUSTAINABLE,
                0.99, 0.0, shape == LoadShape.COLD_START ? 5 : 2,
                40, 4.0);
    }

    private static Scenario exportStackScenario(
            String id, long capacity, long consumption) {
        return new Scenario(id, DirectionMode.EXPORT, 1, 1024, 320, 20,
                TickOrder.MACHINE_THEN_IO, PhaseMode.SYNCHRONIZED,
                LoadShape.CONTINUOUS, 1, 0, 0, 0, 0,
                OutputMode.UNIQUE_EACH_CYCLE, ProductionMode.ATOMIC,
                36, 36, capacity, consumption, Expectation.SUSTAINABLE,
                0.95, 0.0, Integer.MAX_VALUE, 2, 4.0);
    }

    private static void addPhaseAndOrderMatrix(List<Scenario> out) {
        for (int period : new int[] { 1, 5, 20 }) {
            for (PhaseMode phase : PhaseMode.values()) {
                for (TickOrder order : TickOrder.values()) {
                    out.add(importScenario("pressure-import-period-" + period + "-"
                                    + lower(phase) + "-" + lower(order),
                            1, 1024, 420, 40, order, phase,
                            period == 1 ? LoadShape.CONTINUOUS : LoadShape.PERIODIC,
                            period, 32, 64, OutputMode.UNIQUE_EACH_CYCLE,
                            ProductionMode.ATOMIC, Expectation.SUSTAINABLE,
                            0.99, 0.0, Math.max(2, period), 40,
                            phase == PhaseMode.SYNCHRONIZED && period > 1 ? 6.0 : 4.0));
                }
            }
        }
        for (PhaseMode phase : PhaseMode.values()) {
            out.add(importScenario("pressure-import-cyclic-" + lower(phase),
                    1, 1024, 600, 80, TickOrder.MACHINE_THEN_IO,
                    phase, LoadShape.CYCLIC, 1, 32, 64,
                    OutputMode.UNIQUE_EACH_CYCLE, ProductionMode.ATOMIC,
                    Expectation.SUSTAINABLE, 0.99, 0.0, 5, 40, 5.0));
        }
    }

    private static void addInterfaceTopologyMatrix(List<Scenario> out) {
        for (int interfaces : new int[] { 1, 2, 4, 16 }) {
            out.add(importScenario("pressure-import-topology-" + interfaces
                            + "x" + (1024 / interfaces),
                    interfaces, 1024, 320, 20, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.CONTINUOUS, 1,
                    32, 64, OutputMode.UNIQUE_EACH_CYCLE,
                    ProductionMode.ATOMIC, Expectation.SUSTAINABLE,
                    0.995, 0.0, 2, 2, 3.5));
        }
    }

    private static void addExportMatrix(List<Scenario> out) {
        for (int keys : new int[] { 0, 1, 35, 36 }) {
            int active = keys == 0 ? 0 : keys;
            out.add(exportScenario("pressure-export-config-" + keys + "-continuous",
                    1, 1024, 320, 20, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.CONTINUOUS, 1,
                    keys, active, 64, keys == 0 ? Expectation.OBSERVATION_ONLY
                            : Expectation.SUSTAINABLE,
                    keys == 0 ? 0.0 : 0.95, keys == 0 ? 1.0 : 0.0, 2, 4.0));
        }
        for (int capacity : new int[] { 1, 2, 64 }) {
            out.add(exportScenario("pressure-export-36-capacity-" + capacity,
                    1, 1024, 320, 20, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.CONTINUOUS, 1,
                    36, 36, capacity, Expectation.SUSTAINABLE,
                    0.95, 0.0, 2, 4.0));
            out.add(exportScenario("pressure-export-hot-restart-capacity-" + capacity,
                    1, 1024, 360, 20, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.HOT_IDLE_RESTART, 1,
                    36, 36, capacity, Expectation.SUSTAINABLE,
                    0.95, 0.0, 40, 4.0));
            out.add(exportScenario("pressure-export-cold-start-capacity-" + capacity,
                    1, 1024, 360, 20, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.COLD_START, 1,
                    36, 36, capacity, Expectation.SUSTAINABLE,
                    0.95, 0.0, 40, 4.0));
        }
        out.add(exportScenario("pressure-export-36-sparse-one",
                1, 1024, 320, 20, TickOrder.MACHINE_THEN_IO,
                PhaseMode.SYNCHRONIZED, LoadShape.CONTINUOUS, 1,
                36, 1, 64, Expectation.SUSTAINABLE,
                0.95, 0.0, 2, 4.0));
        out.add(exportScenario("pressure-export-period20-staggered",
                1, 1024, 500, 40, TickOrder.MACHINE_THEN_IO,
                PhaseMode.STAGGERED, LoadShape.PERIODIC, 20,
                36, 36, 1, Expectation.SUSTAINABLE,
                0.95, 0.0, 40, 5.0));
    }

    private static void addBidirectionalMatrix(List<Scenario> out) {
        for (int interfaces : new int[] { 1, 4 }) {
            out.add(new Scenario("pressure-bidirectional-1024-" + interfaces
                            + "-interfaces", DirectionMode.BIDIRECTIONAL,
                    interfaces, 1024, 420, 40, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, LoadShape.CONTINUOUS, 1,
                    32, 64, 1, 64, OutputMode.UNIQUE_EACH_CYCLE,
                    ProductionMode.ATOMIC, 36, 1, 64, 1,
                    Expectation.SUSTAINABLE, 0.95, 0.0,
                    2, 2, 4.0));
        }
        out.add(new Scenario("pressure-bidirectional-hot-restart",
                DirectionMode.BIDIRECTIONAL, 1, 1024, 420, 20,
                TickOrder.MACHINE_THEN_IO, PhaseMode.SYNCHRONIZED,
                LoadShape.HOT_IDLE_RESTART, 1, 32, 64, 1, 64,
                OutputMode.UNIQUE_EACH_CYCLE, ProductionMode.ATOMIC,
                36, 1, 2, 1, Expectation.SUSTAINABLE,
                0.95, 0.0, 5, 40, 5.0));
    }

    private static void addAdversarialCadenceMatrix(List<Scenario> out) {
        // Exercise both sides of every important scheduler cadence instead of
        // testing only the exact 5/20-tick values used by the implementation.
        for (int period : new int[] { 4, 6, 9, 10, 11, 19, 21, 39, 40, 41 }) {
            for (TickOrder order : TickOrder.values()) {
                out.add(importScenario("pressure-import-boundary-period-" + period
                                + "-hashed-" + lower(order),
                        1, 1024, 620, 80, order, PhaseMode.HASHED,
                        LoadShape.PERIODIC, period, 32, 64,
                        OutputMode.UNIQUE_EACH_CYCLE, ProductionMode.ATOMIC,
                        Expectation.SUSTAINABLE, 0.995, 0.0,
                        Math.min(20, period), 20, 6.0));
            }
        }

        for (LoadShape shape : new LoadShape[] {
                LoadShape.SINGLE_TICK_PULSE, LoadShape.FOUR_TICK_BURST }) {
            for (PhaseMode phase : PhaseMode.values()) {
                for (TickOrder order : TickOrder.values()) {
                    out.add(importScenario("pressure-import-" + lower(shape) + "-"
                                    + lower(phase) + "-" + lower(order),
                            1, 1024, 640, 80, order, phase, shape, 1,
                            32, 64, OutputMode.UNIQUE_EACH_CYCLE,
                            ProductionMode.ATOMIC, Expectation.SUSTAINABLE,
                            0.995, 0.0, 5, 5, 8.0));
                }
            }
        }

        for (LoadShape shape : new LoadShape[] {
                LoadShape.JITTER_10, LoadShape.JITTER_20 }) {
            int latency = shape == LoadShape.JITTER_10 ? 10 : 20;
            for (PhaseMode phase : PhaseMode.values()) {
                for (TickOrder order : TickOrder.values()) {
                    out.add(importScenario("pressure-import-" + lower(shape) + "-"
                                    + lower(phase) + "-" + lower(order),
                            1, 1024, 720, 100, order, phase, shape,
                            shape == LoadShape.JITTER_10 ? 10 : 20,
                            32, 64, OutputMode.UNIQUE_EACH_CYCLE,
                            ProductionMode.ATOMIC, Expectation.SUSTAINABLE,
                            0.995, 0.0, latency, latency, 6.0));
                }
            }
        }

        for (PhaseMode phase : PhaseMode.values()) {
            for (TickOrder order : TickOrder.values()) {
                out.add(importScenario("pressure-import-rate-switch-"
                                + lower(phase) + "-" + lower(order),
                        1, 1024, 560, 80, order, phase,
                        LoadShape.RATE_SWITCH, 20, 32, 64,
                        OutputMode.UNIQUE_EACH_CYCLE, ProductionMode.ATOMIC,
                        Expectation.SUSTAINABLE, 0.99, 0.0,
                        20, 20, 7.0));
            }
        }

        for (LoadShape shape : new LoadShape[] {
                LoadShape.SUCCESS_STREAK_31,
                LoadShape.SUCCESS_STREAK_32,
                LoadShape.SUCCESS_STREAK_33 }) {
            out.add(importScenario("pressure-import-" + lower(shape),
                    1, 1024, 280, 80, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, shape, 1, 32, 64,
                    OutputMode.UNIQUE_EACH_CYCLE, ProductionMode.ATOMIC,
                    Expectation.SUSTAINABLE, 0.995, 0.0, 5, 5, 5.0));
            out.add(exportScenario("pressure-export-" + lower(shape),
                    1, 1024, 280, 80, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.SYNCHRONIZED, shape, 1, 36, 36, 2,
                    Expectation.SUSTAINABLE, 0.995, 0.0, 5, 5.0));
        }
    }

    private static void addRecoveryAndRebuildMatrix(List<Scenario> out) {
        for (LoadShape shape : new LoadShape[] {
                LoadShape.TARGET_FLAP,
                LoadShape.TARGET_OUTAGE,
                LoadShape.SCHEDULER_REBUILD }) {
            int acceptanceStart = shape == LoadShape.TARGET_OUTAGE ? 220 : 80;
            int latency = shape == LoadShape.TARGET_OUTAGE ? 45 : 5;
            out.add(importScenario("pressure-import-" + lower(shape),
                    1, 1024, 420, acceptanceStart,
                    TickOrder.MACHINE_THEN_IO, PhaseMode.HASHED, shape, 1,
                    32, 64, OutputMode.UNIQUE_EACH_CYCLE,
                    ProductionMode.ATOMIC, Expectation.SUSTAINABLE,
                    0.995, 0.0, latency, latency, 6.0));
            out.add(exportScenario("pressure-export-" + lower(shape),
                    1, 1024, 420, acceptanceStart,
                    TickOrder.MACHINE_THEN_IO, PhaseMode.HASHED, shape, 1,
                    36, 36, 2, Expectation.SUSTAINABLE,
                    0.995, 0.0, latency, 6.0));
            out.add(new Scenario("pressure-bidirectional-" + lower(shape),
                    DirectionMode.BIDIRECTIONAL, 4, 1024, 420,
                    acceptanceStart, TickOrder.MACHINE_THEN_IO,
                    PhaseMode.HASHED, shape, 1, 32, 64, 1, 64,
                    OutputMode.UNIQUE_EACH_CYCLE, ProductionMode.ATOMIC,
                    36, 1, 2, 1, Expectation.SUSTAINABLE,
                    0.99, 0.0, latency, latency, 7.0));
        }

        for (int interfaces : new int[] { 32, 64 }) {
            out.add(importScenario("pressure-import-topology-" + interfaces
                            + "x" + (1024 / interfaces),
                    interfaces, 1024, 360, 40,
                    TickOrder.MACHINE_THEN_IO, PhaseMode.HASHED,
                    LoadShape.CONTINUOUS, 1, 32, 64,
                    OutputMode.UNIQUE_EACH_CYCLE, ProductionMode.ATOMIC,
                    Expectation.SUSTAINABLE, 0.995, 0.0, 2, 2, 4.0));
        }
    }

    private static void addExactLargeStackBoundaries(List<Scenario> out) {
        for (long amount : new long[] { 999, 1_000, 1_001, 9_999, 10_000, 10_001 }) {
            out.add(new Scenario("pressure-import-stack-boundary-" + amount,
                    DirectionMode.IMPORT, 1, 1024, 360, 40,
                    TickOrder.MACHINE_THEN_IO, PhaseMode.HASHED,
                    LoadShape.CONTINUOUS, 1, 36, 36, amount, amount,
                    OutputMode.STACKABLE_FIXED, ProductionMode.ATOMIC,
                    0, 0, 0, 0, Expectation.SUSTAINABLE,
                    0.995, 0.0, 2, 2, 4.0));
            out.add(new Scenario("pressure-export-stack-boundary-" + amount,
                    DirectionMode.EXPORT, 1, 1024, 360, 40,
                    TickOrder.MACHINE_THEN_IO, PhaseMode.HASHED,
                    LoadShape.CONTINUOUS, 1, 0, 0, 0, 0,
                    OutputMode.UNIQUE_EACH_CYCLE, ProductionMode.ATOMIC,
                    36, 36, amount, amount, Expectation.SUSTAINABLE,
                    0.995, 0.0, Integer.MAX_VALUE, 2, 4.0));
        }
    }

    private static void addHarnessBoundaries(List<Scenario> out) {
        out.add(importScenario("pressure-import-impossible-zero-slots",
                1, 64, 220, 20, TickOrder.MACHINE_THEN_IO,
                PhaseMode.SYNCHRONIZED, LoadShape.CONTINUOUS, 1,
                1, 0, OutputMode.UNIQUE_EACH_CYCLE,
                ProductionMode.ATOMIC, Expectation.EXPECT_BACKPRESSURE,
                0.0, 1.0, Integer.MAX_VALUE, 40, 10.0));
        out.add(importScenario("pressure-import-impossible-31-for-32",
                1, 64, 220, 20, TickOrder.MACHINE_THEN_IO,
                PhaseMode.SYNCHRONIZED, LoadShape.CONTINUOUS, 1,
                32, 31, OutputMode.UNIQUE_EACH_CYCLE,
                ProductionMode.ATOMIC, Expectation.EXPECT_BACKPRESSURE,
                0.0, 1.0, Integer.MAX_VALUE, 40, 10.0));
        out.add(importScenario("pressure-import-zero-work",
                1, 0, 220, 20, TickOrder.MACHINE_THEN_IO,
                PhaseMode.SYNCHRONIZED, LoadShape.ZERO, 1,
                0, 0, OutputMode.UNIQUE_EACH_CYCLE,
                ProductionMode.ATOMIC, Expectation.OBSERVATION_ONLY,
                0.0, 1.0, Integer.MAX_VALUE, 40, 10.0));
    }

    private static void addEnduranceMatrix(List<Scenario> out) {
        out.add(importScenario("pressure-endurance-import-1024-20000t",
                1, 1024, 20_000, 100, TickOrder.MACHINE_THEN_IO,
                PhaseMode.HASHED, LoadShape.CYCLIC, 1,
                32, 64, OutputMode.UNIQUE_EACH_CYCLE,
                ProductionMode.ATOMIC, Expectation.SUSTAINABLE,
                0.99, 0.0, 5, 40, 5.0));
        out.add(exportScenario("pressure-endurance-export-1024-20000t",
                1, 1024, 20_000, 100, TickOrder.MACHINE_THEN_IO,
                PhaseMode.HASHED, LoadShape.CYCLIC, 1,
                36, 1, 2, Expectation.SUSTAINABLE,
                0.95, 0.0, 40, 5.0));
        out.add(new Scenario("pressure-endurance-bidirectional-4x256-20000t",
                DirectionMode.BIDIRECTIONAL, 4, 1024, 20_000, 100,
                TickOrder.MACHINE_THEN_IO, PhaseMode.HASHED, LoadShape.CYCLIC,
                1, 32, 64, 1, 64, OutputMode.UNIQUE_EACH_CYCLE,
                ProductionMode.ATOMIC, 36, 1, 2, 1,
                Expectation.SUSTAINABLE, 0.95, 0.0,
                5, 40, 5.0));
    }

    private static Scenario importScenario(
            String id, int interfaces, int machines, int ticks, int acceptanceStart,
            TickOrder order, PhaseMode phase, LoadShape shape, int period,
            int outputKeys, int outputSlots, OutputMode outputMode,
            ProductionMode productionMode, Expectation expectation,
            double minimumThroughput, double maximumBlockedRatio,
            int maximumP99Latency, int maximumServiceGap,
            double maximumP99ToMeanWork) {
        return new Scenario(id, DirectionMode.IMPORT, interfaces, machines, ticks,
                acceptanceStart, order, phase, shape, period, outputKeys,
                outputSlots, 1, 64, outputMode, productionMode,
                0, 0, 0, 0, expectation,
                minimumThroughput, maximumBlockedRatio, maximumP99Latency,
                maximumServiceGap, maximumP99ToMeanWork);
    }

    private static Scenario exportScenario(
            String id, int interfaces, int machines, int ticks, int acceptanceStart,
            TickOrder order, PhaseMode phase, LoadShape shape, int period,
            int exportKeys, int activeExportKeys, long inputCapacity,
            Expectation expectation, double minimumThroughput,
            double maximumStarvedRatio, int maximumServiceGap,
            double maximumP99ToMeanWork) {
        return new Scenario(id, DirectionMode.EXPORT, interfaces, machines, ticks,
                acceptanceStart, order, phase, shape, period, 0, 0, 0, 0,
                OutputMode.UNIQUE_EACH_CYCLE, ProductionMode.ATOMIC,
                exportKeys, activeExportKeys, inputCapacity, 1, expectation,
                minimumThroughput, maximumStarvedRatio, Integer.MAX_VALUE,
                maximumServiceGap, maximumP99ToMeanWork);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static Result run(Scenario scenario) {
        long started = System.nanoTime();
        var simulation = new Simulation(scenario);
        simulation.run();
        return simulation.result(System.nanoTime() - started);
    }

    record Result(
            Scenario scenario,
            long elapsedNanos,
            long theoreticalItems,
            long completedItems,
            double minimumWindowThroughput,
            double minimumMachineThroughput,
            long pressureEvents,
            long pressureShortfall,
            double pressureEventRatio,
            int maximumPressureStreak,
            int p50ImportLatency,
            int p95ImportLatency,
            int p99ImportLatency,
            int maximumImportLatency,
            int maximumServiceGap,
            int maximumDemandWait,
            double p99ToMeanWork,
            long meanWork,
            long p99Work,
            long maximumWork,
            long schedulerVisits,
            long productiveVisits,
            long idleVisits,
            double idleVisitRatio,
            int maximumOutputOccupancyBps,
            double outputNonemptyRatio,
            double outputFullRatio,
            long backlogItemTicks,
            long finalOutput,
            long finalInput,
            long producedOutput,
            long extractedOutput,
            long dispatchedInput,
            long consumedInput,
            List<String> correctnessFailures,
            List<String> acceptanceFailures) {

        String summaryLine() {
            return String.format(Locale.ROOT,
                    "%s dir=%s topo=%dx%d order=%s phase=%s load=%s "
                            + "throughput(window/machine)=%.2f%%/%.2f%% "
                            + "pressure(events/ratio/streak/shortfall)=%d/%.3f%%/%d/%d "
                            + "latency(p50/p95/p99/max)=%d/%d/%d/%d gap=%d demandWait=%d "
                            + "work(mean/p99/max/ratio)=%d/%d/%d/%.2f "
                            + "visits(total/productive/idle/idleRatio)=%d/%d/%d/%.2f%% "
                            + "output(nonempty/full/peak)=%.2f%%/%.2f%%/%.2f%% acceptance=%s",
                    scenario.id(), scenario.direction(), scenario.interfaces(),
                    scenario.machines(), scenario.tickOrder(), scenario.phaseMode(),
                    scenario.loadShape(), minimumWindowThroughput * 100.0,
                    minimumMachineThroughput * 100.0, pressureEvents,
                    pressureEventRatio * 100.0, maximumPressureStreak,
                    pressureShortfall, p50ImportLatency, p95ImportLatency,
                    p99ImportLatency, maximumImportLatency, maximumServiceGap,
                    maximumDemandWait,
                    meanWork, p99Work, maximumWork, p99ToMeanWork,
                    schedulerVisits, productiveVisits, idleVisits,
                    idleVisitRatio * 100.0, outputNonemptyRatio * 100.0,
                    outputFullRatio * 100.0,
                    maximumOutputOccupancyBps / 100.0,
                    acceptanceFailures.isEmpty() ? "PASS" : "FAIL");
        }

        static String csvHeader() {
            return "schema,scenario,direction,interfaces,machines,ticks,tick_order,phase_mode,"
                    + "load_shape,period,output_keys,output_slots,output_amount_per_key,"
                    + "output_stack_capacity,output_mode,production_mode,export_keys,"
                    + "active_export_keys,input_capacity,consumption_per_key,expectation,theoretical,"
                    + "completed,min_window,min_machine,pressure_events,pressure_shortfall,"
                    + "pressure_ratio,max_pressure_streak,latency_p50,latency_p95,latency_p99,"
                    + "latency_max,max_service_gap,max_demand_wait,mean_work,p99_work,max_work,"
                    + "p99_mean_ratio,scheduler_visits,productive_visits,idle_visits,idle_visit_ratio,"
                    + "max_output_occupancy,output_nonempty_ratio,output_full_ratio,backlog_item_ticks,"
                    + "final_output,final_input,produced_output,extracted_output,dispatched_input,"
                    + "consumed_input,elapsed_nanos,acceptance";
        }

        String csvRow() {
            return String.join(",",
                    Integer.toString(REPORT_SCHEMA), scenario.id(), scenario.direction().name(),
                    Integer.toString(scenario.interfaces()), Integer.toString(scenario.machines()),
                    Integer.toString(scenario.ticks()), scenario.tickOrder().name(),
                    scenario.phaseMode().name(), scenario.loadShape().name(),
                    Integer.toString(scenario.period()), Integer.toString(scenario.outputKeys()),
                    Integer.toString(scenario.outputSlots()),
                    Long.toString(scenario.outputAmountPerKey()),
                    Long.toString(scenario.outputStackCapacityPerKey()),
                    scenario.outputMode().name(), scenario.productionMode().name(),
                    Integer.toString(scenario.exportKeys()),
                    Integer.toString(scenario.activeExportKeys()),
                    Long.toString(scenario.inputCapacityPerKey()),
                    Long.toString(scenario.consumptionPerKey()), scenario.expectation().name(),
                    Long.toString(theoreticalItems), Long.toString(completedItems),
                    decimal(minimumWindowThroughput), decimal(minimumMachineThroughput),
                    Long.toString(pressureEvents), Long.toString(pressureShortfall),
                    decimal(pressureEventRatio), Integer.toString(maximumPressureStreak),
                    Integer.toString(p50ImportLatency), Integer.toString(p95ImportLatency),
                    Integer.toString(p99ImportLatency), Integer.toString(maximumImportLatency),
                    Integer.toString(maximumServiceGap), Integer.toString(maximumDemandWait),
                    Long.toString(meanWork), Long.toString(p99Work), Long.toString(maximumWork),
                    decimal(p99ToMeanWork), Long.toString(schedulerVisits),
                    Long.toString(productiveVisits), Long.toString(idleVisits),
                    decimal(idleVisitRatio), decimal(maximumOutputOccupancyBps / 10000.0),
                    decimal(outputNonemptyRatio), decimal(outputFullRatio),
                    Long.toString(backlogItemTicks), Long.toString(finalOutput),
                    Long.toString(finalInput), Long.toString(producedOutput),
                    Long.toString(extractedOutput), Long.toString(dispatchedInput),
                    Long.toString(consumedInput), Long.toString(elapsedNanos),
                    acceptanceFailures.isEmpty() ? "PASS" : "FAIL");
        }

        private static String decimal(double value) {
            return String.format(Locale.ROOT, "%.8f", value);
        }
    }

    private static final class Machine {
        final ConnectionState state = new ConnectionState();
        final IoScheduledEntry importEntry;
        final IoScheduledEntry exportEntry;
        final long[] input;
        final ExportRejectState[] exportRejects;
        final int[] batchTicks;
        final long[] batchAmounts;

        long importDue = 1;
        long exportDue = 1;
        long outputAmount;
        int outputSlotsUsed;
        int batchHead;
        int batchTail;
        long theoreticalAfterAcceptance;
        long completedAfterAcceptance;
        int pressureStreak;
        int lastImportVisit = -1;
        int lastExportVisit = -1;
        int importDemandSince = -1;
        int exportDemandSince = -1;

        Machine(Scenario scenario, int index) {
            var connection = new WirelessConnection(Level.OVERWORLD,
                    new BlockPos(index, 64, index / 1024), Direction.NORTH);
            importEntry = scenario.importEnabled()
                    ? new IoScheduledEntry(connection, state, ITEM_TYPE,
                            IoDirection.IMPORT, 1) : null;
            exportEntry = scenario.exportEnabled()
                    ? new IoScheduledEntry(connection, state, ITEM_TYPE,
                            IoDirection.EXPORT, 1) : null;
            if (importEntry != null) {
                state.cdFor(ITEM_TYPE, IoDirection.IMPORT).reset(IOSpeedMode.FAST);
            }
            if (exportEntry != null) {
                state.cdFor(ITEM_TYPE, IoDirection.EXPORT).reset(IOSpeedMode.FAST);
            }
            input = new long[scenario.exportKeys()];
            exportRejects = new ExportRejectState[scenario.exportKeys()];
            batchTicks = new int[scenario.ticks() + 1];
            batchAmounts = new long[scenario.ticks() + 1];
        }

        void enqueue(int tick, long amount) {
            batchTicks[batchTail] = tick;
            batchAmounts[batchTail] = amount;
            batchTail++;
        }
    }

    private static final class Simulation {
        final Scenario scenario;
        final Machine[] machines;
        final long[] theoreticalAt;
        final long[] completedAt;
        final long[] workAt;
        final long[] latencyHistogram;

        long theoreticalItems;
        long completedItems;
        long pressureEvents;
        long pressureOpportunities;
        long pressureShortfall;
        int maximumPressureStreak;
        int maximumServiceGap;
        int maximumDemandWait;
        int maximumOutputOccupancyBps;
        long outputNonemptyMachineTicks;
        long outputFullMachineTicks;
        long backlogItemTicks;
        long producedOutput;
        long extractedOutput;
        long dispatchedInput;
        long consumedInput;
        long schedulerVisits;
        long productiveVisits;
        long idleVisits;

        Simulation(Scenario scenario) {
            this.scenario = scenario;
            machines = new Machine[scenario.machines()];
            for (int i = 0; i < machines.length; i++) {
                machines[i] = new Machine(scenario, i);
            }
            theoreticalAt = new long[scenario.ticks()];
            completedAt = new long[scenario.ticks()];
            workAt = new long[scenario.ticks()];
            latencyHistogram = new long[scenario.ticks() + 1];
        }

        void run() {
            for (int tick = 0; tick < scenario.ticks(); tick++) {
                if (scenario.rebuildSchedulerAt(tick)) {
                    rebuildScheduler(tick);
                }
                if (scenario.tickOrder() == TickOrder.MACHINE_THEN_IO) {
                    runMachines(tick);
                    runIo(tick);
                } else {
                    runIo(tick);
                    runMachines(tick);
                }
                observeOutputBacklog();
            }
        }

        private void rebuildScheduler(int tick) {
            for (var machine : machines) {
                machine.state.resetWirelessIo(IOSpeedMode.FAST);
                if (machine.importEntry != null) {
                    machine.importEntry.phase = IoPhase.EXTRACT;
                    machine.importEntry.clearPacing();
                    machine.importDue = tick + 1L;
                }
                if (machine.exportEntry != null) {
                    machine.exportEntry.phase = IoPhase.EXTRACT;
                    machine.exportEntry.clearPacing();
                    machine.exportDue = tick + 1L;
                }
                Arrays.fill(machine.exportRejects, null);
            }
        }

        private void observeOutputBacklog() {
            for (var machine : machines) {
                if (machine.outputAmount > 0) {
                    outputNonemptyMachineTicks++;
                    backlogItemTicks = Math.addExact(backlogItemTicks,
                            machine.outputAmount);
                }
                if (scenario.outputSlots() > 0
                        && machine.outputSlotsUsed >= scenario.outputSlots()) {
                    outputFullMachineTicks++;
                }
            }
        }

        private void runMachines(int tick) {
            for (int index = 0; index < machines.length; index++) {
                if (!scenario.opportunityAt(tick, index)) {
                    continue;
                }
                var machine = machines[index];
                if (scenario.importEnabled()) {
                    produce(machine, tick);
                }
                if (scenario.exportEnabled()) {
                    consume(machine, tick);
                }
            }
        }

        private void produce(Machine machine, int tick) {
            long requested = Math.multiplyExact((long) scenario.outputKeys(),
                    scenario.outputAmountPerKey());
            recordTheory(machine, tick, requested);
            if (requested == 0) {
                recordCompleted(machine, tick, 0);
                return;
            }

            long accepted;
            if (scenario.outputMode() == OutputMode.UNIQUE_EACH_CYCLE) {
                int free = Math.max(0, scenario.outputSlots() - machine.outputSlotsUsed);
                int acceptedKeys = scenario.productionMode() == ProductionMode.ATOMIC
                        ? (free >= scenario.outputKeys() ? scenario.outputKeys() : 0)
                        : Math.min(free, scenario.outputKeys());
                accepted = Math.multiplyExact((long) acceptedKeys,
                        scenario.outputAmountPerKey());
                if (accepted > 0) {
                    machine.outputSlotsUsed += acceptedKeys;
                }
            } else {
                long capacity = scenario.outputSlots() < scenario.outputKeys()
                        ? 0 : Math.multiplyExact((long) scenario.outputKeys(),
                                scenario.outputStackCapacityPerKey());
                long free = Math.max(0, capacity - machine.outputAmount);
                accepted = scenario.productionMode() == ProductionMode.ATOMIC
                        ? (free >= requested ? requested : 0)
                        : Math.min(free, requested);
            }

            if (accepted > 0) {
                if (machine.outputAmount == 0) {
                    machine.importDemandSince = tick;
                }
                if (scenario.outputMode() == OutputMode.STACKABLE_FIXED
                        && machine.outputAmount == 0) {
                    machine.outputSlotsUsed = scenario.outputKeys();
                }
                machine.outputAmount += accepted;
                machine.enqueue(tick, accepted);
                producedOutput += accepted;
                recordCompleted(machine, tick, accepted);
            }
            recordPressure(machine, tick, requested, accepted);
            updateOccupancy(machine);
        }

        private void consume(Machine machine, int tick) {
            long requested = Math.multiplyExact((long) scenario.activeExportKeys(),
                    scenario.consumptionPerKey());
            recordTheory(machine, tick, requested);
            long consumed = 0;
            for (int key = 0; key < scenario.activeExportKeys(); key++) {
                long amount = Math.min(machine.input[key],
                        scenario.consumptionPerKey());
                machine.input[key] -= amount;
                consumed += amount;
            }
            consumedInput += consumed;
            recordCompleted(machine, tick, consumed);
            recordPressure(machine, tick, requested, consumed);
            if (requested > 0 && machine.exportDemandSince < 0) {
                machine.exportDemandSince = tick;
            }
        }

        private void recordTheory(Machine machine, int tick, long amount) {
            theoreticalItems += amount;
            theoreticalAt[tick] += amount;
            if (tick >= scenario.acceptanceStart()) {
                machine.theoreticalAfterAcceptance += amount;
            }
        }

        private void recordCompleted(Machine machine, int tick, long amount) {
            completedItems += amount;
            completedAt[tick] += amount;
            if (tick >= scenario.acceptanceStart()) {
                machine.completedAfterAcceptance += amount;
            }
        }

        private void recordPressure(Machine machine, int tick, long requested, long actual) {
            if (requested <= 0 || tick < scenario.acceptanceStart()) {
                return;
            }
            pressureOpportunities++;
            if (actual < requested) {
                pressureEvents++;
                pressureShortfall += requested - actual;
                machine.pressureStreak++;
                maximumPressureStreak = Math.max(maximumPressureStreak,
                        machine.pressureStreak);
            } else {
                machine.pressureStreak = 0;
            }
        }

        private void updateOccupancy(Machine machine) {
            if (scenario.outputSlots() <= 0) {
                return;
            }
            int basisPoints = (int) Math.min(10_000L,
                    (long) machine.outputSlotsUsed * 10_000 / scenario.outputSlots());
            maximumOutputOccupancyBps = Math.max(maximumOutputOccupancyBps,
                    basisPoints);
        }

        private void runIo(int tick) {
            workAt[tick] += 4L * scenario.interfaces();
            for (var machine : machines) {
                if (scenario.importEnabled() && machine.importDue <= tick) {
                    runImport(machine, tick);
                }
                if (scenario.exportEnabled() && machine.exportDue <= tick) {
                    runExport(machine, tick);
                }
            }
        }

        private void runImport(Machine machine, int tick) {
            schedulerVisits++;
            workAt[tick] += 8L + machine.outputSlotsUsed;
            observeGap(machine.lastImportVisit, tick);
            machine.lastImportVisit = tick;
            if (!scenario.targetAvailableAt(tick)) {
                idleVisits++;
                machine.state.cdFor(ITEM_TYPE, IoDirection.IMPORT)
                        .onFail(tick, IOSpeedMode.FAST);
                machine.importDue = OverloadedInterfaceBlockEntity.nextIoSchedule(
                        machine.importEntry, tick);
                return;
            }
            var model = machine.state.modelFor(ITEM_TYPE);
            var cooldown = machine.state.cdFor(ITEM_TYPE, IoDirection.IMPORT);
            if (machine.importEntry.phase == IoPhase.PROBE) {
                model.onProbe(machine.outputAmount, tick);
                if (machine.outputAmount == 0) {
                    idleVisits++;
                }
            } else {
                long available = machine.outputAmount;
                if (available > 0) {
                    observeDemandWait(machine.importDemandSince, tick);
                    productiveVisits++;
                    extractedOutput += available;
                    workAt[tick] += 3L * machine.outputSlotsUsed;
                    while (machine.batchHead < machine.batchTail) {
                        int latency = tick - machine.batchTicks[machine.batchHead];
                        latencyHistogram[Math.clamp(latency, 0,
                                latencyHistogram.length - 1)]
                                += machine.batchAmounts[machine.batchHead];
                        machine.batchHead++;
                    }
                    machine.outputAmount = 0;
                    machine.outputSlotsUsed = 0;
                    machine.importDemandSince = -1;
                    model.onExtract(available, available, tick);
                    cooldown.onSuccess(tick, IOSpeedMode.FAST, model);
                } else {
                    idleVisits++;
                    model.onExtract(0, 0, tick);
                    cooldown.onFail(tick, IOSpeedMode.FAST);
                }
            }
            machine.importDue = OverloadedInterfaceBlockEntity.nextIoSchedule(
                    machine.importEntry, tick);
        }

        private void runExport(Machine machine, int tick) {
            schedulerVisits++;
            workAt[tick]++;
            observeGap(machine.lastExportVisit, tick);
            machine.lastExportVisit = tick;
            var cooldown = machine.state.cdFor(ITEM_TYPE, IoDirection.EXPORT);
            if (!scenario.targetAvailableAt(tick)) {
                idleVisits++;
                cooldown.onFail(tick, IOSpeedMode.FAST);
                machine.exportDue = OverloadedInterfaceBlockEntity.nextIoSchedule(
                        machine.exportEntry, tick);
                return;
            }
            boolean fastRejectRetry = cooldown.consecutiveFailures()
                    >= OverloadedInterfaceBlockEntity.EXPORT_REJECT_FAST_RETRY_THRESHOLD;
            long moved = 0;
            for (int key = 0; key < scenario.exportKeys(); key++) {
                workAt[tick]++;
                var reject = machine.exportRejects[key];
                if (reject != null && tick < reject.untilTick) {
                    continue;
                }
                long free = scenario.inputCapacityPerKey() - machine.input[key];
                workAt[tick] += 2;
                if (free <= 0) {
                    var state = new ExportRejectState();
                    state.reject(tick, fastRejectRetry);
                    machine.exportRejects[key] = state;
                    continue;
                }
                machine.exportRejects[key] = null;
                machine.input[key] += free;
                dispatchedInput += free;
                moved += free;
                workAt[tick] += 4;
            }
            if (moved > 0) {
                observeDemandWait(machine.exportDemandSince, tick);
                machine.exportDemandSince = -1;
                productiveVisits++;
                cooldown.onSuccess(tick, IOSpeedMode.FAST, null);
            } else {
                idleVisits++;
                cooldown.onFail(tick, IOSpeedMode.FAST);
            }
            machine.exportDue = OverloadedInterfaceBlockEntity.nextIoSchedule(
                    machine.exportEntry, tick);
        }

        private void observeGap(int previous, int tick) {
            if (previous >= 0 && tick >= scenario.acceptanceStart()) {
                maximumServiceGap = Math.max(maximumServiceGap, tick - previous);
            }
        }

        private void observeDemandWait(int demandSince, int tick) {
            if (demandSince >= 0) {
                maximumDemandWait = Math.max(maximumDemandWait, tick - demandSince);
            }
        }

        Result result(long elapsedNanos) {
            long finalOutput = 0;
            long finalInput = 0;
            for (var machine : machines) {
                // Do not let a demand that remains starved at the end of the
                // scenario disappear merely because no successful visit
                // occurred to close and record its wait interval.
                observeDemandWait(machine.importDemandSince, scenario.ticks() - 1);
                observeDemandWait(machine.exportDemandSince, scenario.ticks() - 1);
                finalOutput += machine.outputAmount;
                for (long amount : machine.input) {
                    finalInput += amount;
                }
            }

            var correctness = new ArrayList<String>();
            if (producedOutput != extractedOutput + finalOutput) {
                correctness.add("output ownership mismatch: produced=" + producedOutput
                        + ", extracted=" + extractedOutput + ", final=" + finalOutput);
            }
            if (dispatchedInput != consumedInput + finalInput) {
                correctness.add("input ownership mismatch: dispatched=" + dispatchedInput
                        + ", consumed=" + consumedInput + ", final=" + finalInput);
            }
            for (int i = 0; i < machines.length; i++) {
                var machine = machines[i];
                if (machine.outputSlotsUsed > scenario.outputSlots()) {
                    correctness.add("machine " + i + " exceeded output slots");
                    break;
                }
                if (machine.outputAmount < 0 || machine.outputSlotsUsed < 0) {
                    correctness.add("machine " + i + " has negative output state");
                    break;
                }
            }

            double minWindow = minimumWindowThroughput();
            double minMachine = minimumMachineThroughput();
            double pressureRatio = pressureOpportunities == 0 ? 0.0
                    : (double) pressureEvents / pressureOpportunities;
            int p50Latency = histogramPercentile(latencyHistogram, 0.50);
            int p95Latency = histogramPercentile(latencyHistogram, 0.95);
            int p99Latency = histogramPercentile(latencyHistogram, 0.99);
            int maxLatency = histogramMaximum(latencyHistogram);
            long workTotal = Arrays.stream(workAt).sum();
            long meanWork = workAt.length == 0 ? 0 : workTotal / workAt.length;
            long p99Work = percentile(workAt, 0.99);
            long maxWork = Arrays.stream(workAt).max().orElse(0);
            double peakRatio = meanWork == 0 ? 0.0 : (double) p99Work / meanWork;
            double idleRatio = schedulerVisits == 0 ? 0.0
                    : (double) idleVisits / schedulerVisits;
            long machineTicks = (long) scenario.machines() * scenario.ticks();
            double outputNonemptyRatio = machineTicks == 0 ? 0.0
                    : (double) outputNonemptyMachineTicks / machineTicks;
            double outputFullRatio = machineTicks == 0 ? 0.0
                    : (double) outputFullMachineTicks / machineTicks;
            var acceptance = evaluateAcceptance(minWindow, minMachine,
                    pressureRatio, p99Latency, peakRatio);

            return new Result(scenario, elapsedNanos, theoreticalItems,
                    completedItems, minWindow, minMachine, pressureEvents,
                    pressureShortfall, pressureRatio, maximumPressureStreak,
                    p50Latency, p95Latency, p99Latency, maxLatency,
                    maximumServiceGap, maximumDemandWait, peakRatio, meanWork,
                    p99Work, maxWork, schedulerVisits, productiveVisits, idleVisits,
                    idleRatio, maximumOutputOccupancyBps, outputNonemptyRatio,
                    outputFullRatio, backlogItemTicks, finalOutput, finalInput,
                    producedOutput, extractedOutput, dispatchedInput,
                    consumedInput, List.copyOf(correctness),
                    List.copyOf(acceptance));
        }

        private double minimumWindowThroughput() {
            double minimum = Double.POSITIVE_INFINITY;
            int window = Math.min(WINDOW_TICKS,
                    scenario.ticks() - scenario.acceptanceStart());
            for (int start = scenario.acceptanceStart();
                    start + window <= scenario.ticks(); start++) {
                long theory = sum(theoreticalAt, start, start + window);
                if (theory > 0) {
                    minimum = Math.min(minimum,
                            (double) sum(completedAt, start, start + window) / theory);
                }
            }
            return Double.isFinite(minimum) ? minimum : 1.0;
        }

        private double minimumMachineThroughput() {
            double minimum = Double.POSITIVE_INFINITY;
            for (var machine : machines) {
                if (machine.theoreticalAfterAcceptance > 0) {
                    minimum = Math.min(minimum,
                            (double) machine.completedAfterAcceptance
                                    / machine.theoreticalAfterAcceptance);
                }
            }
            return Double.isFinite(minimum) ? minimum : 1.0;
        }

        private List<String> evaluateAcceptance(
                double minWindow, double minMachine, double pressureRatio,
                int p99Latency, double peakRatio) {
            var failures = new ArrayList<String>();
            if (scenario.expectation() == Expectation.EXPECT_BACKPRESSURE) {
                if (pressureEvents == 0) {
                    failures.add("harness failed to observe required backpressure");
                }
                return failures;
            }
            if (scenario.expectation() == Expectation.OBSERVATION_ONLY) {
                return failures;
            }
            checkAtLeast(failures, "minimum window throughput", minWindow,
                    scenario.minimumThroughput());
            checkAtLeast(failures, "minimum machine throughput", minMachine,
                    scenario.minimumThroughput());
            checkAtMost(failures, "pressure event ratio", pressureRatio,
                    scenario.maximumBlockedRatio());
            if (scenario.importEnabled() && p99Latency >= 0) {
                checkAtMost(failures, "p99 import latency", p99Latency,
                        scenario.maximumP99ImportLatency());
            }
            checkAtMost(failures, "maximum demand wait", maximumDemandWait,
                    scenario.maximumDemandWait());
            checkAtMost(failures, "p99/mean work ratio", peakRatio,
                    scenario.maximumP99ToMeanWork());
            return failures;
        }
    }

    private static void checkAtLeast(
            List<String> failures, String label, double actual, double minimum) {
        if (actual + 1e-12 < minimum) {
            failures.add(String.format(Locale.ROOT, "%s %.4f < %.4f",
                    label, actual, minimum));
        }
    }

    private static void checkAtMost(
            List<String> failures, String label, double actual, double maximum) {
        if (actual > maximum + 1e-12) {
            failures.add(String.format(Locale.ROOT, "%s %.4f > %.4f",
                    label, actual, maximum));
        }
    }

    private static long sum(long[] values, int start, int end) {
        long total = 0;
        for (int i = Math.max(0, start); i < Math.min(values.length, end); i++) {
            total += values[i];
        }
        return total;
    }

    private static long percentile(long[] values, double percentile) {
        if (values.length == 0) {
            return 0;
        }
        var sorted = values.clone();
        Arrays.sort(sorted);
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.clamp(index, 0, sorted.length - 1)];
    }

    private static int histogramPercentile(long[] histogram, double percentile) {
        long total = Arrays.stream(histogram).sum();
        if (total == 0) {
            return -1;
        }
        long threshold = Math.max(1, (long) Math.ceil(total * percentile));
        long seen = 0;
        for (int i = 0; i < histogram.length; i++) {
            seen += histogram[i];
            if (seen >= threshold) {
                return i;
            }
        }
        return histogram.length - 1;
    }

    private static int histogramMaximum(long[] histogram) {
        for (int i = histogram.length - 1; i >= 0; i--) {
            if (histogram[i] > 0) {
                return i;
            }
        }
        return -1;
    }
}
