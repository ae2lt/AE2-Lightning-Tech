package com.moakiee.ae2lt.logic.tianshu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class TianshuMultiblockScannerTest {
    private static final BlockPos CONTROLLER = new BlockPos(40, 70, -20);

    @Test
    void templateMatchesReferenceDimensionsAndCore() {
        int coreCount = 0;
        int occupiedCount = 0;
        for (int x = 0; x < 7; x++) for (int y = 0; y < 7; y++) for (int z = 0; z < 7; z++) {
            var role = TianshuMultiblockTemplate.roleAt(new BlockPos(x, y, z));
            if (role == TianshuMultiblockRole.CORE_RESERVED) coreCount++;
            if (role != TianshuMultiblockRole.IGNORED) occupiedCount++;
        }
        assertEquals(27, coreCount);
        assertEquals(243, occupiedCount);
        assertEquals(TianshuMultiblockRole.CONTROLLER,
                TianshuMultiblockTemplate.roleAt(new BlockPos(6, 0, 3)));
        assertEquals(TianshuMultiblockRole.PORT_CANDIDATE,
                TianshuMultiblockTemplate.roleAt(new BlockPos(3, 6, 3)));
    }

    @Test
    void completeTemplateFormsInEveryHorizontalDirection() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Map<BlockPos, TianshuMultiblockComponent> blocks = completeStructure(direction);
            var result = TianshuMultiblockScanner.scan(CONTROLLER, direction,
                    pos -> blocks.getOrDefault(pos, TianshuMultiblockComponent.OTHER));
            assertTrue(result.formed(), direction + ": " + result.issues());
            assertEquals(27, result.result().corePositions().size());
        }
    }

    @Test
    void missingShellAndInvalidPortCountsAreReported() {
        var blocks = completeStructure(Direction.WEST);
        blocks.put(TianshuMultiblockScanner.worldPos(CONTROLLER, new BlockPos(0, 0, 0), Direction.WEST),
                TianshuMultiblockComponent.OTHER);
        var missingCasing = TianshuMultiblockScanner.scan(CONTROLLER, Direction.WEST, blocks::get);
        assertFalse(missingCasing.formed());
        assertTrue(missingCasing.issues().contains(TianshuMultiblockScanIssue.MISSING_CASING));

        blocks = completeStructure(Direction.WEST);
        blocks.put(TianshuMultiblockScanner.worldPos(CONTROLLER, TianshuMultiblockTemplate.UPPER_PORT, Direction.WEST),
                TianshuMultiblockComponent.PORT);
        var multiplePorts = TianshuMultiblockScanner.scan(CONTROLLER, Direction.WEST, blocks::get);
        assertFalse(multiplePorts.formed());
        assertTrue(multiplePorts.issues().contains(TianshuMultiblockScanIssue.MULTIPLE_PORTS));
    }

    @Test
    void coreChamberRequiresCenteredMainAndFilledPeriphery() {
        var blocks = completeStructure(Direction.WEST);
        var center = TianshuMultiblockScanner.worldPos(CONTROLLER, new BlockPos(3, 3, 3), Direction.WEST);
        blocks.put(center, TianshuMultiblockComponent.STORAGE_UNIT);
        var missingMain = TianshuMultiblockScanner.scan(CONTROLLER, Direction.WEST, blocks::get);
        assertFalse(missingMain.formed());
        assertTrue(missingMain.issues().contains(TianshuMultiblockScanIssue.MISSING_MAIN_CORE));

        blocks = completeStructure(Direction.WEST);
        var peripheral = TianshuMultiblockScanner.worldPos(CONTROLLER, new BlockPos(4, 4, 4), Direction.WEST);
        blocks.put(peripheral, TianshuMultiblockComponent.OTHER);
        var gap = TianshuMultiblockScanner.scan(CONTROLLER, Direction.WEST, blocks::get);
        assertFalse(gap.formed());
        assertTrue(gap.issues().contains(TianshuMultiblockScanIssue.INVALID_PERIPHERAL_UNIT));
    }

    @Test
    void closedLoopStorageIsRejectedInsideTheCoreChamber() {
        var blocks = completeStructure(Direction.WEST);
        var peripheral = TianshuMultiblockScanner.worldPos(
                CONTROLLER, new BlockPos(4, 4, 4), Direction.WEST);
        blocks.put(peripheral, TianshuMultiblockComponent.CLOSED_LOOP_PATTERN_STORAGE);

        var attempt = TianshuMultiblockScanner.scan(CONTROLLER, Direction.WEST, blocks::get);

        assertFalse(attempt.formed());
        assertTrue(attempt.issues().contains(TianshuMultiblockScanIssue.INVALID_PERIPHERAL_UNIT));
    }

    @Test
    void blankUnitsFillPeripheralCellsWithoutProvidingAttributes() {
        var blocks = completeStructure(Direction.WEST);
        for (int x = 2; x <= 4; x++) {
            for (int y = 2; y <= 4; y++) {
                for (int z = 2; z <= 4; z++) {
                    var local = new BlockPos(x, y, z);
                    if (!local.equals(new BlockPos(3, 3, 3))) {
                        blocks.put(TianshuMultiblockScanner.worldPos(CONTROLLER, local, Direction.WEST),
                                TianshuMultiblockComponent.BLANK_UNIT);
                    }
                }
            }
        }
        blocks.put(TianshuMultiblockScanner.worldPos(CONTROLLER, new BlockPos(2, 2, 2), Direction.WEST),
                TianshuMultiblockComponent.STORAGE_UNIT);
        blocks.put(TianshuMultiblockScanner.worldPos(CONTROLLER, new BlockPos(2, 2, 3), Direction.WEST),
                TianshuMultiblockComponent.PARALLEL_UNIT);

        var attempt = TianshuMultiblockScanner.scan(CONTROLLER, Direction.WEST, blocks::get);

        assertTrue(attempt.formed(), attempt.issues().toString());
        assertEquals(1, attempt.result().coreProfile().storageUnitCount());
        assertEquals(1, attempt.result().coreProfile().parallelUnitCount());
        assertEquals((1L << 20) + CpuInternalCoreCalculator.STORAGE_PER_UNIT,
                attempt.result().coreProfile().storageBytes());
        assertEquals(CpuInternalCoreCalculator.PARALLEL_PER_UNIT, attempt.result().coreProfile().parallelism());
    }

    @Test
    void amplifierUnitsAreCountedAndValidatedByMainCoreTier() {
        var quantum = completeStructure(Direction.WEST);
        quantum.put(
                TianshuMultiblockScanner.worldPos(
                        CONTROLLER, new BlockPos(3, 3, 3), Direction.WEST),
                TianshuMultiblockComponent.MAIN_QUANTUM);
        int amplifiers = 0;
        for (int x = 2; x <= 4 && amplifiers < 15; x++) {
            for (int y = 2; y <= 4 && amplifiers < 15; y++) {
                for (int z = 2; z <= 4 && amplifiers < 15; z++) {
                    var local = new BlockPos(x, y, z);
                    if (local.equals(new BlockPos(3, 3, 3)) || local.equals(new BlockPos(2, 2, 2))) continue;
                    quantum.put(
                            TianshuMultiblockScanner.worldPos(CONTROLLER, local, Direction.WEST),
                            TianshuMultiblockComponent.AMPLIFIER_UNIT);
                    amplifiers++;
                }
            }
        }

        var quantumAttempt = TianshuMultiblockScanner.scan(
                CONTROLLER, Direction.WEST, quantum::get);
        assertTrue(quantumAttempt.formed(), quantumAttempt.issues().toString());
        assertEquals(15, quantumAttempt.result().coreProfile().amplifierUnitCount());
        assertEquals(3_072, quantumAttempt.result().coreProfile().successfulDispatchesPerTick());

        quantum.put(
                TianshuMultiblockScanner.worldPos(
                        CONTROLLER, new BlockPos(3, 3, 3), Direction.WEST),
                TianshuMultiblockComponent.MAIN_BASELINE);
        var baselineAttempt = TianshuMultiblockScanner.scan(
                CONTROLLER, Direction.WEST, quantum::get);
        assertFalse(baselineAttempt.formed());
        assertTrue(baselineAttempt.issues().contains(
                TianshuMultiblockScanIssue.AMPLIFIER_UNIT_NOT_SUPPORTED));
    }

    @Test
    void requiredAirRejectsUnexpectedBlocks() {
        var blocks = completeStructure(Direction.NORTH);
        var airLocal = new BlockPos(0, 1, 1);
        assertEquals(TianshuMultiblockRole.IGNORED, TianshuMultiblockTemplate.roleAt(airLocal));
        blocks.put(TianshuMultiblockScanner.worldPos(CONTROLLER, airLocal, Direction.NORTH),
                TianshuMultiblockComponent.CASING);
        var result = TianshuMultiblockScanner.scan(CONTROLLER, Direction.NORTH, blocks::get);
        assertFalse(result.formed());
        assertTrue(result.issues().contains(TianshuMultiblockScanIssue.UNEXPECTED_BLOCK));
    }

    @Test
    void mainCoreProvidesFunctionsWhilePhysicalWarehousesProvideCapacity() {
        var blocks = completeStructure(Direction.WEST);
        var patternStorageLocal = new BlockPos(2, 0, 2);
        var seedStorageLocal = TianshuMultiblockTemplate.UPPER_PORT;
        blocks.put(TianshuMultiblockScanner.worldPos(CONTROLLER, patternStorageLocal, Direction.WEST),
                TianshuMultiblockComponent.CLOSED_LOOP_PATTERN_STORAGE);
        blocks.put(TianshuMultiblockScanner.worldPos(CONTROLLER, seedStorageLocal, Direction.WEST),
                TianshuMultiblockComponent.CLOSED_LOOP_SEED_STORAGE);

        var attempt = TianshuMultiblockScanner.scan(CONTROLLER, Direction.WEST, blocks::get);

        assertTrue(attempt.formed(), attempt.issues().toString());
        var profile = attempt.result().functionProfile();
        assertTrue(profile.supportsInventoryMaintenance());
        assertTrue(profile.supportsClosedLoopPatterns());
        assertTrue(profile.supportsClosedLoopSeeds());
        assertEquals(com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceLimits.MAX_ENTRIES,
                profile.maintenanceRuleCapacity());
        assertEquals(64, profile.closedLoopPatternCapacity());
        assertEquals(List.of(TianshuMultiblockScanner.worldPos(
                        CONTROLLER, patternStorageLocal, Direction.WEST)),
                attempt.result().patternStoragePositions());
        assertEquals(List.of(TianshuMultiblockScanner.worldPos(
                        CONTROLLER, seedStorageLocal, Direction.WEST)),
                attempt.result().seedStoragePositions());
    }

    @Test
    void multidimensionalCoreUsesBlankPeripheryWithStorageInCoolingPositions() {
        var blocks = completeStructure(Direction.WEST);
        for (int x = 2; x <= 4; x++) {
            for (int y = 2; y <= 4; y++) {
                for (int z = 2; z <= 4; z++) {
                    var local = new BlockPos(x, y, z);
                    blocks.put(TianshuMultiblockScanner.worldPos(CONTROLLER, local, Direction.WEST),
                            local.equals(new BlockPos(3, 3, 3))
                                    ? TianshuMultiblockComponent.MAIN_MULTIDIMENSIONAL
                                    : TianshuMultiblockComponent.BLANK_UNIT);
                }
            }
        }
        var seedStorageLocal = new BlockPos(2, 0, 2);
        blocks.put(TianshuMultiblockScanner.worldPos(
                        CONTROLLER, TianshuMultiblockTemplate.LOWER_PORT, Direction.WEST),
                TianshuMultiblockComponent.CLOSED_LOOP_PATTERN_STORAGE);
        blocks.put(TianshuMultiblockScanner.worldPos(
                        CONTROLLER, TianshuMultiblockTemplate.UPPER_PORT, Direction.WEST),
                TianshuMultiblockComponent.PORT);
        blocks.put(TianshuMultiblockScanner.worldPos(CONTROLLER, seedStorageLocal, Direction.WEST),
                TianshuMultiblockComponent.CLOSED_LOOP_SEED_STORAGE);

        var attempt = TianshuMultiblockScanner.scan(CONTROLLER, Direction.WEST, blocks::get);

        assertTrue(attempt.formed(), attempt.issues().toString());
        assertEquals(CpuMainCoreTier.MULTIDIMENSIONAL, attempt.result().coreProfile().mainCore());
        assertEquals(1, attempt.result().functionProfile().closedLoopPatternStorageCount());
        assertEquals(1, attempt.result().functionProfile().closedLoopSeedStorageCount());
    }

    @Test
    void autoBuildPreservesClosedLoopStorageInCoolingPositions() {
        var patternStorageLocal = new BlockPos(2, 0, 2);
        var seedStorageLocal = TianshuMultiblockTemplate.UPPER_PORT;
        var plan = TianshuAutoBuildPlan.create(local -> {
            if (local.equals(TianshuMultiblockTemplate.LOWER_PORT)) {
                return TianshuMultiblockComponent.PORT;
            }
            if (local.equals(patternStorageLocal)) {
                return TianshuMultiblockComponent.CLOSED_LOOP_PATTERN_STORAGE;
            }
            if (local.equals(seedStorageLocal)) {
                return TianshuMultiblockComponent.CLOSED_LOOP_SEED_STORAGE;
            }
            return TianshuMultiblockComponent.AIR;
        });

        assertFalse(plan.blocked().contains(patternStorageLocal));
        assertFalse(plan.blocked().contains(seedStorageLocal));
        assertFalse(plan.placements().stream().anyMatch(placement ->
                placement.localPos().equals(patternStorageLocal)
                        || placement.localPos().equals(seedStorageLocal)));
    }

    @Test
    void autoBuildUsesTheOtherPortCandidateWhenLowerCandidateContainsStorage() {
        var plan = TianshuAutoBuildPlan.create(local ->
                local.equals(TianshuMultiblockTemplate.LOWER_PORT)
                        ? TianshuMultiblockComponent.CLOSED_LOOP_PATTERN_STORAGE
                        : TianshuMultiblockComponent.AIR);

        assertFalse(plan.blocked().contains(TianshuMultiblockTemplate.LOWER_PORT));
        assertFalse(plan.placements().stream().anyMatch(placement ->
                placement.localPos().equals(TianshuMultiblockTemplate.LOWER_PORT)));
        assertTrue(plan.placements().stream().anyMatch(placement ->
                placement.localPos().equals(TianshuMultiblockTemplate.UPPER_PORT)
                        && placement.target() == TianshuAutoBuildPlan.Target.PORT));
    }

    private static Map<BlockPos, TianshuMultiblockComponent> completeStructure(Direction direction) {
        var result = new HashMap<BlockPos, TianshuMultiblockComponent>();
        for (int x = 0; x < 7; x++) for (int y = 0; y < 7; y++) for (int z = 0; z < 7; z++) {
            var local = new BlockPos(x, y, z);
            var component = switch (TianshuMultiblockTemplate.roleAt(local)) {
                case CASING -> TianshuMultiblockComponent.CASING;
                case COOLING -> TianshuMultiblockComponent.COOLING;
                case GLASS -> TianshuMultiblockComponent.GLASS;
                case CONTROLLER -> TianshuMultiblockComponent.CONTROLLER;
                case PORT_CANDIDATE -> local.equals(TianshuMultiblockTemplate.LOWER_PORT)
                        ? TianshuMultiblockComponent.PORT : TianshuMultiblockComponent.COOLING;
                case CORE_RESERVED -> local.equals(new BlockPos(3, 3, 3))
                        ? TianshuMultiblockComponent.MAIN_BASELINE
                        : local.equals(new BlockPos(2, 2, 2))
                                ? TianshuMultiblockComponent.PARALLEL_UNIT
                                : TianshuMultiblockComponent.STORAGE_UNIT;
                case IGNORED -> TianshuMultiblockComponent.AIR;
            };
            result.put(TianshuMultiblockScanner.worldPos(CONTROLLER, local, direction), component);
        }
        return result;
    }
}
