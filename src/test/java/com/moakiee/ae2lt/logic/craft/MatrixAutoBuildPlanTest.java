package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MatrixAutoBuildPlanTest {
    private static final BlockPos CONTROLLER = new BlockPos(-32, 75, 96);
    private static final BlockPos DEFAULT_PORT = new BlockPos(6, 5, 3);

    @Test
    void emptySiteWithOneStorageProducesTheDocumentedBillOfMaterials() {
        var plan = MatrixAutoBuildPlan.create(ignored -> MatrixMultiblockComponent.AIR, 1);
        var counts = counts(plan);

        assertTrue(plan.blocked().isEmpty());
        assertEquals(0, plan.missingPatternStorages());
        assertEquals(352, plan.placements().size());
        assertEquals(174, counts.getOrDefault(MatrixAutoBuildPlan.Target.CASING, 0));
        assertEquals(132, counts.getOrDefault(MatrixAutoBuildPlan.Target.CONSTRAINT_FRAME, 0));
        assertEquals(44, counts.getOrDefault(MatrixAutoBuildPlan.Target.GLASS, 0));
        assertEquals(1, counts.getOrDefault(MatrixAutoBuildPlan.Target.PORT, 0));
        assertEquals(1, counts.getOrDefault(MatrixAutoBuildPlan.Target.PATTERN_STORAGE, 0));
        assertTrue(plan.placements().stream().noneMatch(placement -> {
            var role = MatrixMultiblockTemplate.roleAt(placement.localPos());
            return role == MatrixMultiblockRole.CONTROLLER
                    || role == MatrixMultiblockRole.CRAFTING_BAY
                    || role == MatrixMultiblockRole.EMPTY;
        }));
    }

    @Test
    void patternStorageBudgetIsRequiredAndCappedByTheFiftyBays() {
        var noStorage = MatrixAutoBuildPlan.create(ignored -> MatrixMultiblockComponent.AIR, 0);
        var excessive = MatrixAutoBuildPlan.create(ignored -> MatrixMultiblockComponent.AIR, Integer.MAX_VALUE);

        assertEquals(1, noStorage.missingPatternStorages());
        assertEquals(0, counts(noStorage).getOrDefault(MatrixAutoBuildPlan.Target.PATTERN_STORAGE, 0));
        assertEquals(0, excessive.missingPatternStorages());
        assertEquals(MatrixMultiblockTemplate.PATTERN_BAY_SLOT_COUNT,
                counts(excessive).getOrDefault(MatrixAutoBuildPlan.Target.PATTERN_STORAGE, 0));
    }

    @Test
    void existingAlternatePortAndT2StorageArePreserved() {
        var alternatePort = MatrixMultiblockTemplate.entries().stream()
                .filter(entry -> entry.role() == MatrixMultiblockRole.PORT_CANDIDATE)
                .map(MatrixMultiblockTemplate.Entry::localPos)
                .filter(pos -> !pos.equals(DEFAULT_PORT))
                .findFirst().orElseThrow();
        var patternBay = MatrixMultiblockTemplate.entries().stream()
                .filter(entry -> entry.role() == MatrixMultiblockRole.PATTERN_BAY)
                .map(MatrixMultiblockTemplate.Entry::localPos)
                .findFirst().orElseThrow();
        var components = new HashMap<BlockPos, MatrixMultiblockComponent>();
        components.put(alternatePort, MatrixMultiblockComponent.MATRIX_PORT);
        components.put(patternBay, MatrixMultiblockComponent.PATTERN_STORAGE_T2);

        var plan = MatrixAutoBuildPlan.create(
                local -> components.getOrDefault(local, MatrixMultiblockComponent.AIR), 0);

        assertEquals(0, plan.missingPatternStorages());
        assertFalse(plan.placements().stream()
                .anyMatch(placement -> placement.target() == MatrixAutoBuildPlan.Target.PORT));
        assertTrue(plan.placements().stream().anyMatch(placement ->
                placement.localPos().equals(DEFAULT_PORT)
                        && placement.target() == MatrixAutoBuildPlan.Target.CONSTRAINT_FRAME));
        assertFalse(plan.placements().stream()
                .anyMatch(placement -> placement.localPos().equals(patternBay)));
    }

    @Test
    void occupiedWrongTargetsAreReportedInsteadOfReplaced() {
        var blockedLocal = new BlockPos(0, 0, 0);
        var plan = MatrixAutoBuildPlan.create(local -> local.equals(blockedLocal)
                ? MatrixMultiblockComponent.MATRIX_GLASS
                : MatrixMultiblockComponent.AIR, 1);

        assertEquals(java.util.List.of(blockedLocal), plan.blocked());
        assertFalse(plan.placements().stream()
                .anyMatch(placement -> placement.localPos().equals(blockedLocal)));
    }

    @Test
    void applyingThePlanThenInstallingCoreUnitsFormsInEveryDirection() {
        for (var direction : Direction.Plane.HORIZONTAL) {
            var local = new HashMap<BlockPos, MatrixMultiblockComponent>();
            local.put(MatrixMultiblockTemplate.CONTROLLER_LOCAL,
                    MatrixMultiblockComponent.MATRIX_CONTROLLER);
            var plan = MatrixAutoBuildPlan.create(
                    pos -> local.getOrDefault(pos, MatrixMultiblockComponent.AIR), 1);
            for (var placement : plan.placements()) {
                local.put(placement.localPos(), componentFor(placement.target()));
            }
            installCoreUnits(local);

            var world = new HashMap<BlockPos, MatrixMultiblockComponent>();
            for (var entry : MatrixMultiblockTemplate.entries()) {
                world.put(MatrixMultiblockScanner.worldPos(
                                CONTROLLER, entry.localPos(), direction),
                        local.getOrDefault(entry.localPos(), MatrixMultiblockComponent.AIR));
            }
            var attempt = MatrixMultiblockScanner.scan(
                    CONTROLLER, direction,
                    pos -> world.getOrDefault(pos, MatrixMultiblockComponent.OTHER));
            assertTrue(attempt.formed(), direction + ": " + attempt.issues());
        }
    }

    private static Map<MatrixAutoBuildPlan.Target, Integer> counts(MatrixAutoBuildPlan plan) {
        var result = new EnumMap<MatrixAutoBuildPlan.Target, Integer>(MatrixAutoBuildPlan.Target.class);
        for (var placement : plan.placements()) result.merge(placement.target(), 1, Integer::sum);
        return result;
    }

    private static MatrixMultiblockComponent componentFor(MatrixAutoBuildPlan.Target target) {
        return switch (target) {
            case CASING -> MatrixMultiblockComponent.MATRIX_CASING;
            case CONSTRAINT_FRAME -> MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case GLASS -> MatrixMultiblockComponent.MATRIX_GLASS;
            case PORT -> MatrixMultiblockComponent.MATRIX_PORT;
            case PATTERN_STORAGE -> MatrixMultiblockComponent.PATTERN_STORAGE_T1;
        };
    }

    private static void installCoreUnits(Map<BlockPos, MatrixMultiblockComponent> local) {
        boolean dispatchInstalled = false;
        for (var entry : MatrixMultiblockTemplate.entries()) {
            if (entry.role() != MatrixMultiblockRole.CRAFTING_BAY) continue;
            if (entry.localPos().equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)) {
                local.put(entry.localPos(), MatrixMultiblockComponent.STABLE_MAIN_CORE);
            } else if (!dispatchInstalled) {
                local.put(entry.localPos(), MatrixMultiblockComponent.THREAD_UNIT_T1);
                dispatchInstalled = true;
            } else {
                local.put(entry.localPos(), MatrixMultiblockComponent.BLANK_UNIT);
            }
        }
    }
}
