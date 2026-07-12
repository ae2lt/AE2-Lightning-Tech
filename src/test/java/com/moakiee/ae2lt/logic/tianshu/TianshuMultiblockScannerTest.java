package com.moakiee.ae2lt.logic.tianshu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
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
        blocks.put(center, TianshuMultiblockComponent.STORAGE_CORE);
        var missingMain = TianshuMultiblockScanner.scan(CONTROLLER, Direction.WEST, blocks::get);
        assertFalse(missingMain.formed());
        assertTrue(missingMain.issues().contains(TianshuMultiblockScanIssue.MISSING_MAIN_CORE));

        blocks = completeStructure(Direction.WEST);
        var peripheral = TianshuMultiblockScanner.worldPos(CONTROLLER, new BlockPos(4, 4, 4), Direction.WEST);
        blocks.put(peripheral, TianshuMultiblockComponent.OTHER);
        var gap = TianshuMultiblockScanner.scan(CONTROLLER, Direction.WEST, blocks::get);
        assertFalse(gap.formed());
        assertTrue(gap.issues().contains(TianshuMultiblockScanIssue.INVALID_PERIPHERAL_CORE));
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
    void optionalFunctionUnitsFormAndProduceIndependentCapabilities() {
        var blocks = completeStructure(Direction.WEST);
        var functionComponents = new TianshuMultiblockComponent[] {
                TianshuMultiblockComponent.INVENTORY_MAINTENANCE_CORE,
                TianshuMultiblockComponent.CLOSED_LOOP_PATTERN_CORE,
                TianshuMultiblockComponent.CLOSED_LOOP_PATTERN_STORAGE,
                TianshuMultiblockComponent.CLOSED_LOOP_SEED_STORAGE
        };
        int index = 0;
        for (int x = 2; x <= 4 && index < functionComponents.length; x++) {
            for (int y = 2; y <= 4 && index < functionComponents.length; y++) {
                for (int z = 2; z <= 4 && index < functionComponents.length; z++) {
                    var local = new BlockPos(x, y, z);
                    if (local.equals(new BlockPos(3, 3, 3)) || local.equals(new BlockPos(2, 2, 2))) continue;
                    blocks.put(TianshuMultiblockScanner.worldPos(CONTROLLER, local, Direction.WEST),
                            functionComponents[index++]);
                }
            }
        }

        var attempt = TianshuMultiblockScanner.scan(CONTROLLER, Direction.WEST, blocks::get);

        assertTrue(attempt.formed(), attempt.issues().toString());
        var profile = attempt.result().functionProfile();
        assertTrue(profile.supportsInventoryMaintenance());
        assertTrue(profile.supportsClosedLoopPatterns());
        assertTrue(profile.supportsClosedLoopSeeds());
        assertEquals(64, profile.maintenanceRuleCapacity());
        assertEquals(64, profile.closedLoopPatternCapacity());
        assertEquals(64, profile.seedTypeCapacity());
    }

    private static Map<BlockPos, TianshuMultiblockComponent> completeStructure(Direction direction) {
        var result = new HashMap<BlockPos, TianshuMultiblockComponent>();
        for (int x = 0; x < 7; x++) for (int y = 0; y < 7; y++) for (int z = 0; z < 7; z++) {
            var local = new BlockPos(x, y, z);
            var component = switch (TianshuMultiblockTemplate.roleAt(local)) {
                case CASING -> TianshuMultiblockComponent.CASING;
                case GLASS -> TianshuMultiblockComponent.GLASS;
                case CONTROLLER -> TianshuMultiblockComponent.CONTROLLER;
                case PORT_CANDIDATE -> local.equals(TianshuMultiblockTemplate.LOWER_PORT)
                        ? TianshuMultiblockComponent.PORT : TianshuMultiblockComponent.CASING;
                case CORE_RESERVED -> local.equals(new BlockPos(3, 3, 3))
                        ? TianshuMultiblockComponent.MAIN_BASELINE
                        : local.equals(new BlockPos(2, 2, 2))
                                ? TianshuMultiblockComponent.PARALLEL_CORE
                                : TianshuMultiblockComponent.STORAGE_CORE;
                case IGNORED -> TianshuMultiblockComponent.AIR;
            };
            result.put(TianshuMultiblockScanner.worldPos(CONTROLLER, local, direction), component);
        }
        return result;
    }
}
