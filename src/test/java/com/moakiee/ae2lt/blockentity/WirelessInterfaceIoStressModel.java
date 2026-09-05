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
 * Deterministic workload model for overloaded-interface wireless item I/O.
 *
 * <p>The model deliberately calls the production cooldown, probe and
 * rescheduling state machine. Minecraft world/capability calls are represented
 * by counted operations so results remain reproducible and suitable for
 * comparing scheduler revisions.</p>
 */
final class WirelessInterfaceIoStressModel {
    static final int REPORT_SCHEMA = 1;

    private static final int IMPORT_FLUSH_INTERVAL = 5;
    private static final int IMPORT_FLUSH_MAX_KEYS = 16_384;
    private static final int STOP_IMPORT_TTL = 20;
    private static final int FIXED_STACK_LIMIT = 64;
    private static final AEKeyType ITEM_TYPE = AEKeyType.items();

    enum Profile {
        CONTINUOUS,
        MIXED_PERIODS,
        BURST_20,
        HOT_IDLE_RESTART,
        NETWORK_OUTAGE,
        HALF_RATE_NETWORK,
        TARGET_OUTAGE,
        SOURCE_OUTAGE,
        COMBINED,
        ZERO_WORK
    }

    enum GateClass {
        IMPORT_CONTINUOUS,
        IMPORT_BURST,
        IMPORT_IDLE_RESTART,
        IMPORT_OUTAGE,
        EXPORT_CONTINUOUS,
        EXPORT_SPARSE,
        EXPORT_IDLE_RESTART,
        EXPORT_OUTAGE,
        COMBINED,
        RESILIENCE,
        ZERO
    }

    record Scenario(
            String id,
            Profile profile,
            GateClass gate,
            int machines,
            int ticks,
            int acceptanceStart,
            boolean importEnabled,
            boolean exportEnabled,
            int outputSlots,
            int outputKeys,
            boolean dynamicOutputs,
            boolean exactImportFilter,
            int inputKeys,
            int activeInputKeys,
            int inputCapacityPerKey,
            int consumptionPerKey,
            int basePeriod,
            int phaseStart,
            int phaseEnd,
            double maxPeakToMeanWork) {
        Scenario {
            if (machines < 0 || ticks <= 0 || acceptanceStart < 0
                    || acceptanceStart > ticks || outputSlots < 0
                    || outputKeys < 0 || inputKeys < 0
                    || activeInputKeys < 0 || activeInputKeys > inputKeys
                    || inputCapacityPerKey < 0 || consumptionPerKey < 0
                    || basePeriod <= 0) {
                throw new IllegalArgumentException("invalid stress scenario: " + id);
            }
        }

        int periodFor(int machine) {
            if (profile != Profile.MIXED_PERIODS) {
                return basePeriod;
            }
            return switch (machine % 3) {
                case 0 -> 1;
                case 1 -> 5;
                default -> 20;
            };
        }

        boolean workloadActive(int tick) {
            return switch (profile) {
                case HOT_IDLE_RESTART -> tick < phaseStart || tick >= phaseEnd;
                case ZERO_WORK -> false;
                default -> true;
            };
        }

        boolean targetReachable(int tick) {
            return profile != Profile.TARGET_OUTAGE
                    || tick < phaseStart || tick >= phaseEnd;
        }

        int networkAcceptancePercent(int tick) {
            if (profile == Profile.NETWORK_OUTAGE
                    && tick >= phaseStart && tick < phaseEnd) {
                return 0;
            }
            return profile == Profile.HALF_RATE_NETWORK ? 50 : 100;
        }

        boolean sourceAvailable(int tick) {
            return profile != Profile.SOURCE_OUTAGE
                    || tick < phaseStart || tick >= phaseEnd;
        }

        boolean isOpportunity(int tick, int machine) {
            if (!workloadActive(tick)) {
                return false;
            }
            int period = profile == Profile.BURST_20 ? 20 : periodFor(machine);
            return tick % period == 0;
        }

        boolean hasIdleWindow() {
            return profile == Profile.HOT_IDLE_RESTART;
        }

        boolean hasRestartBoundary() {
            return profile == Profile.HOT_IDLE_RESTART
                    || profile == Profile.NETWORK_OUTAGE
                    || profile == Profile.TARGET_OUTAGE
                    || profile == Profile.SOURCE_OUTAGE;
        }
    }

    static List<Scenario> scenarios() {
        return List.of(
                importScenario("import-fast-1x32-continuous", Profile.CONTINUOUS,
                        GateClass.IMPORT_CONTINUOUS, 1, 400, 20, 64, 32, true,
                        false, 1, -1, -1, 3.5),
                importScenario("import-fast-64x32-continuous", Profile.CONTINUOUS,
                        GateClass.IMPORT_CONTINUOUS, 64, 400, 20, 64, 32, true,
                        false, 1, -1, -1, 3.5),
                importScenario("import-fast-1024x32-continuous", Profile.CONTINUOUS,
                        GateClass.IMPORT_CONTINUOUS, 1024, 300, 20, 64, 32, true,
                        false, 1, -1, -1, 3.5),
                importScenario("import-fast-1024x255-cache-boundary", Profile.CONTINUOUS,
                        GateClass.IMPORT_CONTINUOUS, 1024, 180, 20, 510, 255, true,
                        false, 1, -1, -1, 3.5),
                importScenario("import-fast-1024x256-cache-boundary", Profile.CONTINUOUS,
                        GateClass.IMPORT_CONTINUOUS, 1024, 180, 20, 512, 256, true,
                        false, 1, -1, -1, 3.5),
                importScenario("import-fast-1024x257-cache-boundary", Profile.CONTINUOUS,
                        GateClass.IMPORT_CONTINUOUS, 1024, 180, 20, 514, 257, true,
                        false, 1, -1, -1, 3.5),
                importScenario("import-fast-1024-mixed-1-5-20t", Profile.MIXED_PERIODS,
                        GateClass.IMPORT_BURST, 1024, 500, 100, 64, 8, true,
                        false, 1, -1, -1, 4.0),
                importScenario("import-fast-1024-burst-20t", Profile.BURST_20,
                        GateClass.IMPORT_BURST, 1024, 500, 100, 64, 32, true,
                        false, 20, -1, -1, 4.0),
                importScenario("import-fast-1024-hot-idle-restart", Profile.HOT_IDLE_RESTART,
                        GateClass.IMPORT_IDLE_RESTART, 1024, 400, 180, 64, 8, true,
                        false, 1, 40, 160, 4.0),
                importScenario("import-fast-256-network-outage", Profile.NETWORK_OUTAGE,
                        GateClass.IMPORT_OUTAGE, 256, 400, 190, 64, 8, true,
                        false, 1, 80, 160, 4.0),
                importScenario("import-fast-256-target-outage", Profile.TARGET_OUTAGE,
                        GateClass.IMPORT_OUTAGE, 256, 420, 210, 64, 8, true,
                        false, 1, 80, 160, 4.0),
                importScenario("import-fast-1024x36-exact-filter", Profile.CONTINUOUS,
                        GateClass.IMPORT_CONTINUOUS, 1024, 300, 20, 36, 36, false,
                        true, 1, -1, -1, 3.5),
                importScenario("import-fast-256-half-rate-network", Profile.HALF_RATE_NETWORK,
                        GateClass.RESILIENCE, 256, 300, 20, 64, 8, true,
                        false, 1, -1, -1, 5.0),
                exportScenario("export-fast-1024x36-continuous", Profile.CONTINUOUS,
                        GateClass.EXPORT_CONTINUOUS, 1024, 300, 20, 36, 36,
                        64, 1, 1, -1, -1),
                exportScenario("export-fast-boundary-config-0", Profile.CONTINUOUS,
                        GateClass.ZERO, 1, 200, 20, 0, 0,
                        0, 0, 1, -1, -1),
                exportScenario("export-fast-boundary-config-1", Profile.CONTINUOUS,
                        GateClass.EXPORT_CONTINUOUS, 256, 300, 20, 1, 1,
                        64, 1, 1, -1, -1),
                exportScenario("export-fast-boundary-config-35", Profile.CONTINUOUS,
                        GateClass.EXPORT_CONTINUOUS, 256, 300, 20, 35, 35,
                        64, 1, 1, -1, -1),
                exportScenario("export-fast-1024x36-sparse", Profile.CONTINUOUS,
                        GateClass.EXPORT_SPARSE, 1024, 300, 20, 36, 1,
                        64, 1, 1, -1, -1),
                exportScenario("export-fast-1024-capacity-one", Profile.CONTINUOUS,
                        GateClass.EXPORT_CONTINUOUS, 1024, 300, 20, 36, 36,
                        1, 1, 1, -1, -1),
                exportScenario("export-fast-1024-full-idle-restart", Profile.HOT_IDLE_RESTART,
                        GateClass.EXPORT_IDLE_RESTART, 1024, 400, 180, 36, 36,
                        64, 1, 1, 40, 160),
                exportScenario("export-fast-256-source-outage", Profile.SOURCE_OUTAGE,
                        GateClass.EXPORT_OUTAGE, 256, 420, 210, 36, 36,
                        64, 1, 1, 80, 160),
                exportScenario("export-fast-256-target-outage", Profile.TARGET_OUTAGE,
                        GateClass.EXPORT_OUTAGE, 256, 420, 210, 36, 36,
                        64, 1, 1, 80, 160),
                new Scenario("combined-fast-1024-transform-1-to-32", Profile.COMBINED,
                        GateClass.COMBINED, 1024, 500, 100, true, true,
                        64, 32, true, false, 1, 1, 64, 1, 1,
                        -1, -1, 4.0),
                new Scenario("combined-fast-256-endurance-5000t", Profile.COMBINED,
                        GateClass.COMBINED, 256, 5_000, 100, true, true,
                        32, 8, true, false, 1, 1, 64, 1, 1,
                        -1, -1, 4.0),
                new Scenario("boundary-zero-target-zero-work", Profile.ZERO_WORK,
                        GateClass.ZERO, 0, 200, 0, true, true,
                        0, 0, true, false, 0, 0, 0, 0, 1,
                        -1, -1, 1.0));
    }

    private static Scenario importScenario(
            String id, Profile profile, GateClass gate, int machines,
            int ticks, int acceptanceStart, int outputSlots, int outputKeys,
            boolean dynamicOutputs, boolean exactFilter, int period,
            int phaseStart, int phaseEnd, double peakToMean) {
        return new Scenario(id, profile, gate, machines, ticks, acceptanceStart,
                true, false, outputSlots, outputKeys, dynamicOutputs,
                exactFilter, 0, 0, 0, 0, period, phaseStart, phaseEnd,
                peakToMean);
    }

    private static Scenario exportScenario(
            String id, Profile profile, GateClass gate, int machines,
            int ticks, int acceptanceStart, int inputKeys, int activeInputKeys,
            int capacity, int consumption, int period,
            int phaseStart, int phaseEnd) {
        return new Scenario(id, profile, gate, machines, ticks, acceptanceStart,
                false, true, 0, 0, true, false, inputKeys, activeInputKeys,
                capacity, consumption, period, phaseStart, phaseEnd, 4.0);
    }

    static Result run(Scenario scenario) {
        long started = System.nanoTime();
        var simulation = new Simulation(scenario);
        simulation.run();
        return simulation.result(System.nanoTime() - started);
    }

    static void verifyProductionCadenceBoundaries() {
        var connection = new WirelessConnection(Level.OVERWORLD, BlockPos.ZERO, Direction.NORTH);
        var state = new ConnectionState();
        var entry = new IoScheduledEntry(connection, state, ITEM_TYPE,
                IoDirection.IMPORT, 1);
        var cd = state.cdFor(ITEM_TYPE, IoDirection.IMPORT);
        cd.reset(IOSpeedMode.FAST);

        cd.onFail(0, IOSpeedMode.FAST);
        require(cd.cooldownUntil() == 6, "FAST cold failure must schedule at tick 6");
        cd.onSuccess(6, IOSpeedMode.FAST, state.modelFor(ITEM_TYPE));
        require(cd.cooldownUntil() == 7, "FAST success must schedule one tick later");
        cd.onSuccess(7, IOSpeedMode.FAST, state.modelFor(ITEM_TYPE));
        cd.onFail(8, IOSpeedMode.FAST);
        require(cd.cooldownUntil() == 9,
                "FAST failure after a one-tick success interval must retain one-tick cadence");

        entry.phase = IoPhase.EXTRACT;
        require(OverloadedInterfaceBlockEntity.nextIoSchedule(entry, 8) == 9,
                "production reschedule hook diverged from cooldown state");
        long largeTick = 1L << 60;
        cd.onSuccess(largeTick, IOSpeedMode.FAST, state.modelFor(ITEM_TYPE));
        require(cd.cooldownUntil() == largeTick + 1,
                "large game tick must keep an exact FAST deadline");

        cd.onSuccess(Long.MAX_VALUE, IOSpeedMode.FAST, state.modelFor(ITEM_TYPE));
        require(cd.cooldownUntil() == Long.MAX_VALUE,
                "FAST success at Long.MAX_VALUE must saturate instead of wrapping");
        entry.phase = IoPhase.EXTRACT;
        require(OverloadedInterfaceBlockEntity.nextIoSchedule(entry, Long.MAX_VALUE)
                        == Long.MAX_VALUE,
                "production deadline must not wrap at Long.MAX_VALUE");

        var idleState = new ConnectionState();
        var idleEntry = new IoScheduledEntry(connection, idleState, ITEM_TYPE,
                IoDirection.IMPORT, 2);
        var idleCd = idleState.cdFor(ITEM_TYPE, IoDirection.IMPORT);
        idleCd.reset(IOSpeedMode.FAST);
        idleState.modelFor(ITEM_TYPE).onExtract(0, 0, Long.MAX_VALUE - 3);
        idleCd.onFail(Long.MAX_VALUE - 3, IOSpeedMode.FAST);
        require(OverloadedInterfaceBlockEntity.nextIoSchedule(
                        idleEntry, Long.MAX_VALUE - 2) == Long.MAX_VALUE,
                "aligned idle polling must saturate near Long.MAX_VALUE");

        var reject = new ExportRejectState();
        reject.reject(100, false);
        require(reject.failures == 1 && reject.untilTick == 110,
                "first export rejection must use the 10-tick backoff");
        reject.reject(110, false);
        require(reject.failures == 2 && reject.untilTick == 130,
                "second export rejection must use the 20-tick backoff");
        reject.reject(130, true);
        require(reject.failures == 3 && reject.untilTick == 135,
                "FAST export retry must override accumulated key backoff");
        reject.reject(Long.MAX_VALUE - 2, true);
        require(reject.untilTick == Long.MAX_VALUE,
                "export reject deadline must saturate instead of wrapping");

        require(OverloadedInterfaceBlockEntity.hasWirelessConnectionCapacity(0),
                "zero connections must have capacity");
        require(OverloadedInterfaceBlockEntity.hasWirelessConnectionCapacity(1023),
                "the 1024th connection must be accepted");
        require(!OverloadedInterfaceBlockEntity.hasWirelessConnectionCapacity(1024),
                "the 1025th connection must be rejected");
        require(!OverloadedInterfaceBlockEntity.hasWirelessConnectionCapacity(1025),
                "an already-overfull list must remain rejected");

        var cache = new OverloadedInterfaceBlockEntity.ImportKeyCache();
        cache.update(List.of(), false, 10);
        require(cache.isScanFresh(29), "empty cache TTL must include tick 29");
        require(!cache.isScanFresh(30), "empty cache TTL must expire at tick 30");
        cache.update(java.util.Collections.nCopies(255, null), false, 10);
        require(cache.isScanFresh(49), "255-key cache TTL must include tick 49");
        require(!cache.isScanFresh(50), "255-key cache TTL must expire at tick 50");
        cache.update(java.util.Collections.nCopies(256, null), false, 10);
        require(cache.isScanFresh(49), "256-key cache TTL must include tick 49");
        require(!cache.isScanFresh(50), "256-key cache TTL must expire at tick 50");
        cache.update(java.util.Collections.nCopies(256, null), true, 10);
        require(cache.isScanFresh(14), "truncated cache TTL must include tick 14");
        require(!cache.isScanFresh(15), "truncated cache TTL must expire at tick 15");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    record Result(
            Scenario scenario,
            long elapsedNanos,
            long theoreticalWork,
            long completedWork,
            double minimumWindow100Throughput,
            double minimumTargetThroughput,
            long blockedEventsAfterAcceptance,
            int maximumBlockedStreak,
            long producedOutput,
            long recoveredOutput,
            long importedToNetwork,
            long finalOutput,
            long finalImportBuffer,
            long dispatchedInput,
            long consumedInput,
            long finalInput,
            long importVisits,
            long productiveImportVisits,
            long importProbes,
            long fullStackScans,
            long exactKeyChecks,
            long scannedStackKeys,
            long extractedStackKeys,
            long importBufferFlushes,
            long importBufferInsertCalls,
            long rejectedImportBufferFlushes,
            long maximumImportBufferKeys,
            long exportVisits,
            long productiveExportVisits,
            long exportConfigChecks,
            long exportSourceSimulations,
            long exportTargetSimulations,
            long exportAcceptedKeys,
            long exportRejectedSkips,
            double idleImportVisitRatio,
            double idleExportVisitRatio,
            int maximumRestartLatency,
            int outageOutputDrainLatency,
            double meanWorkUnitsPerTick,
            long p99WorkUnitsPerTick,
            long maximumWorkUnitsPerTick,
            List<String> correctnessFailures,
            List<String> acceptanceFailures) {

        String summaryLine() {
            return String.format(Locale.ROOT,
                    "%s throughput(min100/target)=%.2f%%/%.2f%% blocked=%d streak=%d "
                            + "restart=%d drain=%d import(visits/productive/probes)=%d/%d/%d "
                            + "scan(full/exact/keys/extracted)=%d/%d/%d/%d "
                            + "buffer(flush/insert/reject/maxKeys)=%d/%d/%d/%d "
                            + "export(visits/productive/checks/targetSim/accepted/skips)=%d/%d/%d/%d/%d/%d "
                            + "idle(import/export)=%.2f%%/%.2f%% work(mean/p99/max)=%.1f/%d/%d "
                            + "elapsed=%.2fms acceptance=%s",
                    scenario.id(), minimumWindow100Throughput * 100.0,
                    minimumTargetThroughput * 100.0, blockedEventsAfterAcceptance,
                    maximumBlockedStreak, maximumRestartLatency,
                    outageOutputDrainLatency, importVisits, productiveImportVisits,
                    importProbes, fullStackScans, exactKeyChecks, scannedStackKeys,
                    extractedStackKeys, importBufferFlushes,
                    importBufferInsertCalls, rejectedImportBufferFlushes,
                    maximumImportBufferKeys, exportVisits, productiveExportVisits,
                    exportConfigChecks, exportTargetSimulations,
                    exportAcceptedKeys, exportRejectedSkips,
                    idleImportVisitRatio * 100.0, idleExportVisitRatio * 100.0,
                    meanWorkUnitsPerTick, p99WorkUnitsPerTick,
                    maximumWorkUnitsPerTick, elapsedNanos / 1_000_000.0,
                    acceptanceFailures.isEmpty() ? "PASS" : "FAIL");
        }

        String csvHeader() {
            return "schema,scenario,machines,ticks,gate,elapsed_nanos,theoretical,completed,"
                    + "min_window_100,min_target,blocked_after_acceptance,max_block_streak,"
                    + "restart_latency,drain_latency,produced,recovered,network_imported,"
                    + "final_output,final_import_buffer,dispatched,consumed,final_input,"
                    + "import_visits,productive_import_visits,import_probes,full_scans,"
                    + "exact_checks,scanned_keys,extracted_keys,buffer_flushes,buffer_insert_calls,"
                    + "rejected_buffer_flushes,max_buffer_keys,export_visits,productive_export_visits,"
                    + "export_config_checks,export_source_simulations,export_target_simulations,"
                    + "export_accepted_keys,export_rejected_skips,idle_import_ratio,idle_export_ratio,"
                    + "mean_work,p99_work,max_work,acceptance";
        }

        String csvRow() {
            return String.format(Locale.ROOT,
                    "%d,%s,%d,%d,%s,%d,%d,%d,%.8f,%.8f,%d,%d,%d,%d,%d,%d,%d,%d,%d,"
                            + "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,"
                            + "%d,%d,%.8f,%.8f,%.3f,%d,%d,%s",
                    REPORT_SCHEMA, scenario.id(), scenario.machines(), scenario.ticks(),
                    scenario.gate(), elapsedNanos, theoreticalWork, completedWork,
                    minimumWindow100Throughput, minimumTargetThroughput,
                    blockedEventsAfterAcceptance, maximumBlockedStreak,
                    maximumRestartLatency, outageOutputDrainLatency, producedOutput,
                    recoveredOutput, importedToNetwork, finalOutput,
                    finalImportBuffer, dispatchedInput, consumedInput, finalInput,
                    importVisits, productiveImportVisits, importProbes, fullStackScans,
                    exactKeyChecks, scannedStackKeys, extractedStackKeys,
                    importBufferFlushes, importBufferInsertCalls,
                    rejectedImportBufferFlushes, maximumImportBufferKeys,
                    exportVisits, productiveExportVisits, exportConfigChecks,
                    exportSourceSimulations, exportTargetSimulations,
                    exportAcceptedKeys, exportRejectedSkips, idleImportVisitRatio,
                    idleExportVisitRatio, meanWorkUnitsPerTick,
                    p99WorkUnitsPerTick, maximumWorkUnitsPerTick,
                    acceptanceFailures.isEmpty() ? "PASS" : "FAIL");
        }
    }

    private static final class Machine {
        final WirelessConnection connection;
        final ConnectionState state = new ConnectionState();
        final IoScheduledEntry importEntry;
        final IoScheduledEntry exportEntry;
        final long[] input;
        final ExportRejectState[] exportRejects;

        long importDue = 1;
        long exportDue = 1;
        int outputSlotsUsed;
        long outputAmount;
        long theoryAfterAcceptance;
        long completedAfterAcceptance;
        int blockedStreak;
        int maxBlockedStreak;
        int firstImportAfterRestart = -1;
        int firstExportAfterRestart = -1;

        Machine(Scenario scenario, int index) {
            connection = new WirelessConnection(Level.OVERWORLD,
                    new BlockPos(index, 64, 0), Direction.NORTH);
            importEntry = scenario.importEnabled()
                    ? new IoScheduledEntry(connection, state, ITEM_TYPE,
                            IoDirection.IMPORT, 1)
                    : null;
            exportEntry = scenario.exportEnabled()
                    ? new IoScheduledEntry(connection, state, ITEM_TYPE,
                            IoDirection.EXPORT, 1)
                    : null;
            if (importEntry != null) {
                state.cdFor(ITEM_TYPE, IoDirection.IMPORT).reset(IOSpeedMode.FAST);
            }
            if (exportEntry != null) {
                state.cdFor(ITEM_TYPE, IoDirection.EXPORT).reset(IOSpeedMode.FAST);
            }
            input = new long[scenario.inputKeys()];
            exportRejects = new ExportRejectState[scenario.inputKeys()];
        }
    }

    private static final class Simulation {
        final Scenario scenario;
        final Machine[] machines;
        final boolean[] fixedKeysBuffered;

        final long[] theoreticalAt;
        final long[] completedAt;
        final long[] importVisitsAt;
        final long[] exportVisitsAt;
        final long[] workAt;

        long producedOutput;
        long recoveredOutput;
        long importedToNetwork;
        long dispatchedInput;
        long consumedInput;
        long pendingBufferAmount;
        long pendingBufferKeys;
        long maxBufferKeys;
        long lastBufferFlushTick = Long.MIN_VALUE;
        long remainingBufferKeysAfterFlush;
        boolean bufferFlushLimited;
        long keyTypeLockedUntil;

        long blockedAfterAcceptance;
        int maxBlockedStreak;
        long importVisits;
        long productiveImportVisits;
        long importProbes;
        long fullStackScans;
        long exactKeyChecks;
        long scannedStackKeys;
        long extractedStackKeys;
        long bufferFlushes;
        long bufferInsertCalls;
        long rejectedBufferFlushes;
        long exportVisits;
        long productiveExportVisits;
        long exportConfigChecks;
        long exportSourceSimulations;
        long exportTargetSimulations;
        long exportAcceptedKeys;
        long exportRejectedSkips;
        int outageDrainTick = -1;

        Simulation(Scenario scenario) {
            this.scenario = scenario;
            machines = new Machine[scenario.machines()];
            for (int i = 0; i < machines.length; i++) {
                machines[i] = new Machine(scenario, i);
            }
            fixedKeysBuffered = new boolean[machines.length];
            theoreticalAt = new long[scenario.ticks()];
            completedAt = new long[scenario.ticks()];
            importVisitsAt = new long[scenario.ticks()];
            exportVisitsAt = new long[scenario.ticks()];
            workAt = new long[scenario.ticks()];
        }

        void run() {
            for (int tick = 0; tick < scenario.ticks(); tick++) {
                runMachines(tick);
                flushImportBuffer(tick);
                runImport(tick);
                runExport(tick);
                observeDrain(tick);
            }
        }

        private void runMachines(int tick) {
            for (int index = 0; index < machines.length; index++) {
                var machine = machines[index];
                if (!scenario.isOpportunity(tick, index)) {
                    continue;
                }
                if (scenario.profile() == Profile.COMBINED) {
                    runTransform(machine, tick);
                } else {
                    if (scenario.importEnabled()) {
                        runProduction(machine, tick);
                    }
                    if (scenario.exportEnabled()) {
                        runConsumption(machine, tick);
                    }
                }
            }
        }

        private void runProduction(Machine machine, int tick) {
            long amount = scenario.outputKeys();
            recordTheory(machine, tick, amount);
            if (canAddOutput(machine)) {
                addOutput(machine);
                producedOutput += amount;
                recordCompleted(machine, tick, amount);
                machine.blockedStreak = 0;
            } else {
                recordBlocked(machine, tick);
            }
        }

        private void runConsumption(Machine machine, int tick) {
            long theoretical = (long) scenario.activeInputKeys()
                    * scenario.consumptionPerKey();
            recordTheory(machine, tick, theoretical);
            long consumed = 0;
            for (int key = 0; key < scenario.activeInputKeys(); key++) {
                long amount = Math.min(machine.input[key], scenario.consumptionPerKey());
                machine.input[key] -= amount;
                consumed += amount;
            }
            consumedInput += consumed;
            recordCompleted(machine, tick, consumed);
        }

        private void runTransform(Machine machine, int tick) {
            recordTheory(machine, tick, 1);
            boolean hasInput = scenario.activeInputKeys() > 0;
            for (int key = 0; key < scenario.activeInputKeys() && hasInput; key++) {
                hasInput = machine.input[key] >= scenario.consumptionPerKey();
            }
            if (!hasInput || !canAddOutput(machine)) {
                if (hasInput) {
                    recordBlocked(machine, tick);
                }
                return;
            }
            for (int key = 0; key < scenario.activeInputKeys(); key++) {
                machine.input[key] -= scenario.consumptionPerKey();
                consumedInput += scenario.consumptionPerKey();
            }
            addOutput(machine);
            producedOutput += scenario.outputKeys();
            recordCompleted(machine, tick, 1);
            machine.blockedStreak = 0;
        }

        private void recordTheory(Machine machine, int tick, long amount) {
            theoreticalAt[tick] += amount;
            if (tick >= scenario.acceptanceStart()) {
                machine.theoryAfterAcceptance += amount;
            }
        }

        private void recordCompleted(Machine machine, int tick, long amount) {
            completedAt[tick] += amount;
            if (tick >= scenario.acceptanceStart()) {
                machine.completedAfterAcceptance += amount;
            }
        }

        private void recordBlocked(Machine machine, int tick) {
            machine.blockedStreak++;
            machine.maxBlockedStreak = Math.max(machine.maxBlockedStreak,
                    machine.blockedStreak);
            maxBlockedStreak = Math.max(maxBlockedStreak, machine.blockedStreak);
            if (tick >= scenario.acceptanceStart()) {
                blockedAfterAcceptance++;
            }
        }

        private boolean canAddOutput(Machine machine) {
            if (scenario.outputKeys() == 0) {
                return true;
            }
            if (scenario.dynamicOutputs()) {
                return machine.outputSlotsUsed + scenario.outputKeys()
                        <= scenario.outputSlots();
            }
            long capacity = (long) scenario.outputSlots() * FIXED_STACK_LIMIT;
            return machine.outputAmount + scenario.outputKeys() <= capacity;
        }

        private void addOutput(Machine machine) {
            if (scenario.outputKeys() == 0) {
                return;
            }
            if (scenario.dynamicOutputs()) {
                machine.outputSlotsUsed += scenario.outputKeys();
            } else if (machine.outputAmount == 0) {
                machine.outputSlotsUsed = scenario.outputKeys();
            }
            machine.outputAmount += scenario.outputKeys();
        }

        private void flushImportBuffer(int tick) {
            if (pendingBufferAmount <= 0) {
                return;
            }
            boolean continueLimitedFlush = bufferFlushLimited && pendingBufferKeys > 0;
            if (!continueLimitedFlush && lastBufferFlushTick != Long.MIN_VALUE
                    && tick - lastBufferFlushTick < IMPORT_FLUSH_INTERVAL) {
                return;
            }
            lastBufferFlushTick = tick;
            bufferFlushes++;
            long flushLimit = IMPORT_FLUSH_MAX_KEYS;
            if (bufferFlushLimited) {
                long newKeys = Math.max(0, pendingBufferKeys - remainingBufferKeysAfterFlush);
                flushLimit = newKeys > 0
                        ? Math.max(IMPORT_FLUSH_MAX_KEYS, newKeys)
                        : pendingBufferKeys;
            }
            long attemptedKeys = Math.min(pendingBufferKeys, flushLimit);
            long attemptedAmount = pendingBufferAmount;
            if (pendingBufferKeys > attemptedKeys && pendingBufferKeys > 0) {
                attemptedAmount = Math.max(1L,
                        pendingBufferAmount * attemptedKeys / pendingBufferKeys);
            }
            bufferInsertCalls += attemptedKeys;
            workAt[tick] += 2L * attemptedKeys;

            int acceptance = scenario.networkAcceptancePercent(tick);
            long acceptedKeys = attemptedKeys * acceptance / 100;
            long acceptedAmount = attemptedAmount * acceptance / 100;
            if (acceptance > 0 && attemptedKeys > 0 && acceptedKeys == 0) {
                acceptedKeys = 1;
            }
            if (acceptance > 0 && attemptedAmount > 0 && acceptedAmount == 0) {
                acceptedAmount = 1;
            }
            acceptedKeys = Math.min(acceptedKeys, attemptedKeys);
            acceptedAmount = Math.min(acceptedAmount, attemptedAmount);

            if (acceptedAmount > 0) {
                importedToNetwork += acceptedAmount;
                pendingBufferAmount -= acceptedAmount;
                pendingBufferKeys -= acceptedKeys;
                keyTypeLockedUntil = 0;
                if (pendingBufferAmount == 0) {
                    pendingBufferKeys = 0;
                    Arrays.fill(fixedKeysBuffered, false);
                }
            } else {
                rejectedBufferFlushes++;
                keyTypeLockedUntil = tick + STOP_IMPORT_TTL;
            }
            bufferFlushLimited = acceptedAmount > 0
                    && pendingBufferKeys > 0
                    && attemptedKeys >= flushLimit;
            remainingBufferKeysAfterFlush = bufferFlushLimited ? pendingBufferKeys : 0;
        }

        private void runImport(int tick) {
            if (!scenario.importEnabled()) {
                return;
            }
            for (int index = 0; index < machines.length; index++) {
                var machine = machines[index];
                if (machine.importDue > tick) {
                    continue;
                }
                importVisits++;
                importVisitsAt[tick]++;
                workAt[tick]++;

                var cd = machine.state.cdFor(ITEM_TYPE, IoDirection.IMPORT);
                if (!scenario.targetReachable(tick)) {
                    cd.onFail(tick, IOSpeedMode.FAST, true);
                    machine.importDue = OverloadedInterfaceBlockEntity.nextIoSchedule(
                            machine.importEntry, tick);
                    continue;
                }
                if (keyTypeLockedUntil > tick) {
                    machine.importDue = keyTypeLockedUntil;
                    continue;
                }

                if (machine.importEntry.phase == IoPhase.PROBE) {
                    importProbes++;
                    observeImport(machine, tick);
                    machine.state.modelFor(ITEM_TYPE).onProbe(machine.outputAmount, tick);
                } else {
                    long available = machine.outputAmount;
                    observeImport(machine, tick);
                    long moved = available;
                    int movedKeys = machine.outputSlotsUsed;
                    if (moved > 0) {
                        productiveImportVisits++;
                        recoveredOutput += moved;
                        extractedStackKeys += movedKeys;
                        workAt[tick] += 3L * movedKeys;
                        addToImportBuffer(index, movedKeys, moved);
                        machine.outputAmount = 0;
                        machine.outputSlotsUsed = 0;
                        if (scenario.hasRestartBoundary() && tick >= scenario.phaseEnd()
                                && machine.firstImportAfterRestart < 0) {
                            machine.firstImportAfterRestart = tick;
                        }
                    }
                    machine.state.modelFor(ITEM_TYPE).onExtract(available, moved, tick);
                    if (moved > 0) {
                        cd.onSuccess(tick, IOSpeedMode.FAST,
                                machine.state.modelFor(ITEM_TYPE));
                    } else {
                        cd.onFail(tick, IOSpeedMode.FAST);
                    }
                }
                machine.importDue = OverloadedInterfaceBlockEntity.nextIoSchedule(
                        machine.importEntry, tick);
            }
        }

        private void observeImport(Machine machine, int tick) {
            int keys = machine.outputSlotsUsed;
            if (scenario.exactImportFilter()) {
                exactKeyChecks += scenario.outputKeys();
                workAt[tick] += 2L * scenario.outputKeys();
            } else {
                fullStackScans++;
                scannedStackKeys += keys;
                workAt[tick] += 8L + keys;
            }
        }

        private void addToImportBuffer(int machineIndex, int keys, long amount) {
            pendingBufferAmount += amount;
            if (scenario.dynamicOutputs()) {
                pendingBufferKeys += keys;
            } else if (!fixedKeysBuffered[machineIndex] && keys > 0) {
                fixedKeysBuffered[machineIndex] = true;
                pendingBufferKeys += keys;
            }
            maxBufferKeys = Math.max(maxBufferKeys, pendingBufferKeys);
        }

        private void runExport(int tick) {
            if (!scenario.exportEnabled()) {
                return;
            }
            for (var machine : machines) {
                if (machine.exportDue > tick) {
                    continue;
                }
                exportVisits++;
                exportVisitsAt[tick]++;
                workAt[tick]++;
                var cd = machine.state.cdFor(ITEM_TYPE, IoDirection.EXPORT);
                boolean fastRejectRetry = OverloadedInterfaceBlockEntity
                        .shouldUseFastExportRejectRetry(
                                OverloadedInterfaceBlockEntity.IOSpeedMode.FAST,
                                cd.consecutiveFailures());
                if (!scenario.targetReachable(tick)) {
                    cd.onFail(tick, IOSpeedMode.FAST, true);
                    machine.exportDue = OverloadedInterfaceBlockEntity.nextIoSchedule(
                            machine.exportEntry, tick);
                    continue;
                }

                long moved = 0;
                exportConfigChecks += scenario.inputKeys();
                workAt[tick] += scenario.inputKeys();
                for (int key = 0; key < scenario.inputKeys(); key++) {
                    var reject = machine.exportRejects[key];
                    if (reject != null && tick < reject.untilTick) {
                        exportRejectedSkips++;
                        continue;
                    }
                    if (reject != null) {
                        machine.exportRejects[key] = null;
                    }

                    exportSourceSimulations++;
                    workAt[tick]++;
                    if (!scenario.sourceAvailable(tick)) {
                        continue;
                    }
                    exportTargetSimulations++;
                    workAt[tick] += 2;
                    long free = scenario.inputCapacityPerKey() - machine.input[key];
                    if (free <= 0) {
                        var rejected = new ExportRejectState();
                        rejected.reject(tick, fastRejectRetry);
                        machine.exportRejects[key] = rejected;
                        continue;
                    }

                    long accepted = Math.min(scenario.inputCapacityPerKey(), free);
                    machine.input[key] += accepted;
                    dispatchedInput += accepted;
                    moved += accepted;
                    exportAcceptedKeys++;
                    workAt[tick] += 4;
                    machine.exportRejects[key] = null;
                }

                if (moved > 0) {
                    productiveExportVisits++;
                    cd.onSuccess(tick, IOSpeedMode.FAST, null);
                    if (scenario.hasRestartBoundary() && tick >= scenario.phaseEnd()
                            && machine.firstExportAfterRestart < 0) {
                        machine.firstExportAfterRestart = tick;
                    }
                } else {
                    cd.onFail(tick, IOSpeedMode.FAST);
                }
                machine.exportDue = OverloadedInterfaceBlockEntity.nextIoSchedule(
                        machine.exportEntry, tick);
            }
        }

        private void observeDrain(int tick) {
            if ((scenario.profile() != Profile.NETWORK_OUTAGE
                    && scenario.profile() != Profile.TARGET_OUTAGE)
                    || tick < scenario.phaseEnd() || outageDrainTick >= 0) {
                return;
            }
            for (var machine : machines) {
                if (machine.outputAmount > 0) {
                    return;
                }
            }
            outageDrainTick = tick;
        }

        Result result(long elapsedNanos) {
            long theoretical = sum(theoreticalAt, 0, scenario.ticks());
            long completed = sum(completedAt, 0, scenario.ticks());
            long finalOutput = 0;
            long finalInput = 0;
            for (var machine : machines) {
                finalOutput += machine.outputAmount;
                for (long amount : machine.input) {
                    finalInput += amount;
                }
            }

            var correctness = new ArrayList<String>();
            if (producedOutput != recoveredOutput + finalOutput) {
                correctness.add("output ownership mismatch: produced=" + producedOutput
                        + ", recovered=" + recoveredOutput + ", machine=" + finalOutput);
            }
            if (recoveredOutput != importedToNetwork + pendingBufferAmount) {
                correctness.add("import ownership mismatch: recovered=" + recoveredOutput
                        + ", network=" + importedToNetwork + ", buffer=" + pendingBufferAmount);
            }
            if (dispatchedInput != consumedInput + finalInput) {
                correctness.add("dispatch ownership mismatch: dispatched=" + dispatchedInput
                        + ", consumed=" + consumedInput + ", machine=" + finalInput);
            }
            for (int i = 0; i < machines.length; i++) {
                if (machines[i].outputSlotsUsed > scenario.outputSlots()) {
                    correctness.add("machine " + i + " exceeded output slot capacity");
                    break;
                }
            }

            double minWindow = minimumWindowThroughput();
            double minTarget = minimumTargetThroughput();
            double idleImport = idleRatio(importVisitsAt);
            double idleExport = idleRatio(exportVisitsAt);
            int restartLatency = maximumRestartLatency();
            int drainLatency = outageDrainTick < 0 || scenario.phaseEnd() < 0
                    ? -1 : outageDrainTick - scenario.phaseEnd();
            double meanWork = (double) sum(workAt, 0, workAt.length) / workAt.length;
            long p99Work = percentile(workAt, 0.99);
            long maxWork = Arrays.stream(workAt).max().orElse(0L);

            var acceptance = evaluateAcceptance(minWindow, minTarget,
                    idleImport, idleExport, restartLatency, drainLatency,
                    meanWork, p99Work);

            return new Result(scenario, elapsedNanos, theoretical, completed,
                    minWindow, minTarget, blockedAfterAcceptance, maxBlockedStreak,
                    producedOutput, recoveredOutput, importedToNetwork, finalOutput,
                    pendingBufferAmount, dispatchedInput, consumedInput, finalInput,
                    importVisits, productiveImportVisits, importProbes,
                    fullStackScans, exactKeyChecks, scannedStackKeys,
                    extractedStackKeys, bufferFlushes, bufferInsertCalls,
                    rejectedBufferFlushes, maxBufferKeys, exportVisits,
                    productiveExportVisits, exportConfigChecks,
                    exportSourceSimulations, exportTargetSimulations,
                    exportAcceptedKeys, exportRejectedSkips, idleImport,
                    idleExport, restartLatency, drainLatency, meanWork,
                    p99Work, maxWork, List.copyOf(correctness),
                    List.copyOf(acceptance));
        }

        private double minimumWindowThroughput() {
            double minimum = Double.POSITIVE_INFINITY;
            for (int start = scenario.acceptanceStart();
                    start + 100 <= scenario.ticks(); start++) {
                long theoretical = sum(theoreticalAt, start, start + 100);
                if (theoretical <= 0) {
                    continue;
                }
                minimum = Math.min(minimum,
                        ratio(sum(completedAt, start, start + 100), theoretical));
            }
            return Double.isFinite(minimum) ? minimum : 1.0;
        }

        private double minimumTargetThroughput() {
            double minimum = Double.POSITIVE_INFINITY;
            for (var machine : machines) {
                if (machine.theoryAfterAcceptance <= 0) {
                    continue;
                }
                minimum = Math.min(minimum,
                        ratio(machine.completedAfterAcceptance,
                                machine.theoryAfterAcceptance));
            }
            return Double.isFinite(minimum) ? minimum : 1.0;
        }

        private double idleRatio(long[] visits) {
            if (!scenario.hasIdleWindow() || machines.length == 0) {
                return 0.0;
            }
            long actual = sum(visits, scenario.phaseStart(), scenario.phaseEnd());
            long possible = (long) machines.length
                    * (scenario.phaseEnd() - scenario.phaseStart());
            return ratio(actual, possible);
        }

        private int maximumRestartLatency() {
            if (!scenario.hasRestartBoundary() || machines.length == 0) {
                return -1;
            }
            int maximum = -1;
            for (var machine : machines) {
                int first;
                if (scenario.importEnabled()) {
                    first = machine.firstImportAfterRestart;
                } else {
                    first = machine.firstExportAfterRestart;
                }
                if (first < 0) {
                    return Integer.MAX_VALUE;
                }
                maximum = Math.max(maximum, first - scenario.phaseEnd());
            }
            return maximum;
        }

        private List<String> evaluateAcceptance(
                double minWindow, double minTarget,
                double idleImport, double idleExport,
                int restartLatency, int drainLatency,
                double meanWork, long p99Work) {
            var failures = new ArrayList<String>();
            double throughputFloor = switch (scenario.gate()) {
                case IMPORT_CONTINUOUS, IMPORT_BURST, IMPORT_IDLE_RESTART -> 0.99;
                case IMPORT_OUTAGE, EXPORT_CONTINUOUS, EXPORT_SPARSE,
                        EXPORT_IDLE_RESTART, EXPORT_OUTAGE, COMBINED -> 0.95;
                case RESILIENCE, ZERO -> 0.0;
            };
            if (minWindow + 1e-12 < throughputFloor) {
                failures.add(String.format(Locale.ROOT,
                        "min 100-tick throughput %.2f%% < %.2f%%",
                        minWindow * 100.0, throughputFloor * 100.0));
            }
            if (minTarget + 1e-12 < throughputFloor) {
                failures.add(String.format(Locale.ROOT,
                        "minimum target throughput %.2f%% < %.2f%%",
                        minTarget * 100.0, throughputFloor * 100.0));
            }

            if (scenario.gate() != GateClass.RESILIENCE
                    && scenario.gate() != GateClass.ZERO
                    && blockedAfterAcceptance > 0) {
                failures.add("blocked production events after acceptance="
                        + blockedAfterAcceptance);
            }

            if (scenario.gate() == GateClass.IMPORT_IDLE_RESTART) {
                checkAtMost(failures, "idle import visit ratio", idleImport, 0.25);
                checkLatency(failures, "import restart", restartLatency, 5);
            }
            if (scenario.gate() == GateClass.EXPORT_IDLE_RESTART) {
                checkAtMost(failures, "idle export visit ratio", idleExport, 0.25);
                checkLatency(failures, "export restart", restartLatency, 5);
            }
            if (scenario.gate() == GateClass.IMPORT_OUTAGE) {
                checkLatency(failures, "import restart", restartLatency, 25);
                checkLatency(failures, "output drain", drainLatency, 25);
            }
            if (scenario.gate() == GateClass.EXPORT_OUTAGE) {
                checkLatency(failures, "export restart", restartLatency, 40);
            }
            if (scenario.gate() == GateClass.EXPORT_SPARSE) {
                double probeRatio = ratio(exportTargetSimulations,
                        Math.max(1L, exportAcceptedKeys));
                checkAtMost(failures, "target simulations per accepted key",
                        probeRatio, 5.0);
            }
            if (scenario.gate() != GateClass.ZERO
                    && meanWork > 0 && scenario.maxPeakToMeanWork() > 0) {
                double peakRatio = p99Work / meanWork;
                checkAtMost(failures, "p99/mean work ratio", peakRatio,
                        scenario.maxPeakToMeanWork());
            }
            return failures;
        }

        private static void checkAtMost(
                List<String> failures, String label, double actual, double maximum) {
            if (actual > maximum + 1e-12) {
                failures.add(String.format(Locale.ROOT,
                        "%s %.3f > %.3f", label, actual, maximum));
            }
        }

        private static void checkLatency(
                List<String> failures, String label, int actual, int maximum) {
            if (actual < 0 || actual > maximum) {
                failures.add(label + " latency " + actual + " > " + maximum + " ticks");
            }
        }
    }

    private static long sum(long[] values, int start, int end) {
        long total = 0;
        for (int i = Math.max(0, start); i < Math.min(values.length, end); i++) {
            total += values[i];
        }
        return total;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 1.0 : (double) numerator / denominator;
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
}
