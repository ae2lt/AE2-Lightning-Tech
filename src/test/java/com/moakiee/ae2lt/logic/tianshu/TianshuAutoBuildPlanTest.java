package com.moakiee.ae2lt.logic.tianshu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class TianshuAutoBuildPlanTest {
    private static final BlockPos CONTROLLER = new BlockPos(41, 80, -17);

    @Test
    void emptySiteProducesTheDocumentedShellBillOfMaterials() {
        var plan = TianshuAutoBuildPlan.create(ignored -> TianshuMultiblockComponent.AIR);
        var counts = counts(plan);

        assertTrue(plan.blocked().isEmpty());
        assertEquals(215, plan.placements().size());
        assertEquals(99, counts.getOrDefault(TianshuAutoBuildPlan.Target.CASING, 0));
        assertEquals(17, counts.getOrDefault(TianshuAutoBuildPlan.Target.COOLING, 0));
        assertEquals(98, counts.getOrDefault(TianshuAutoBuildPlan.Target.GLASS, 0));
        assertEquals(1, counts.getOrDefault(TianshuAutoBuildPlan.Target.PORT, 0));
        assertTrue(plan.placements().stream().noneMatch(placement -> {
            var role = TianshuMultiblockTemplate.roleAt(placement.localPos());
            return role == TianshuMultiblockRole.CONTROLLER
                    || role == TianshuMultiblockRole.CORE_RESERVED
                    || role == TianshuMultiblockRole.IGNORED;
        }));
    }

    @Test
    void existingUpperPortIsPreservedAndTheLowerCandidateBecomesCooling() {
        var components = new HashMap<BlockPos, TianshuMultiblockComponent>();
        components.put(TianshuMultiblockTemplate.UPPER_PORT, TianshuMultiblockComponent.PORT);

        var plan = TianshuAutoBuildPlan.create(
                local -> components.getOrDefault(local, TianshuMultiblockComponent.AIR));

        assertTrue(plan.blocked().isEmpty());
        assertFalse(plan.placements().stream()
                .anyMatch(placement -> placement.target() == TianshuAutoBuildPlan.Target.PORT));
        assertTrue(plan.placements().stream().anyMatch(placement ->
                placement.localPos().equals(TianshuMultiblockTemplate.LOWER_PORT)
                        && placement.target() == TianshuAutoBuildPlan.Target.COOLING));
    }

    @Test
    void occupiedWrongTargetsAreReportedInsteadOfReplaced() {
        var blockedLocal = new BlockPos(0, 0, 0);
        var plan = TianshuAutoBuildPlan.create(local -> local.equals(blockedLocal)
                ? TianshuMultiblockComponent.GLASS
                : TianshuMultiblockComponent.AIR);

        assertEquals(java.util.List.of(blockedLocal), plan.blocked());
        assertFalse(plan.placements().stream()
                .anyMatch(placement -> placement.localPos().equals(blockedLocal)));
    }

    @Test
    void applyingThePlanThenInstallingCoreUnitsFormsInEveryDirection() {
        for (var direction : Direction.Plane.HORIZONTAL) {
            var local = new HashMap<BlockPos, TianshuMultiblockComponent>();
            local.put(TianshuMultiblockTemplate.CONTROLLER, TianshuMultiblockComponent.CONTROLLER);
            var plan = TianshuAutoBuildPlan.create(
                    pos -> local.getOrDefault(pos, TianshuMultiblockComponent.AIR));
            for (var placement : plan.placements()) {
                local.put(placement.localPos(), componentFor(placement.target()));
            }
            installCoreUnits(local);

            var world = new HashMap<BlockPos, TianshuMultiblockComponent>();
            for (int x = 0; x < TianshuMultiblockTemplate.SIZE; x++) {
                for (int y = 0; y < TianshuMultiblockTemplate.SIZE; y++) {
                    for (int z = 0; z < TianshuMultiblockTemplate.SIZE; z++) {
                        var pos = new BlockPos(x, y, z);
                        world.put(TianshuMultiblockScanner.worldPos(CONTROLLER, pos, direction),
                                local.getOrDefault(pos, TianshuMultiblockComponent.AIR));
                    }
                }
            }

            var attempt = TianshuMultiblockScanner.scan(
                    CONTROLLER, direction,
                    pos -> world.getOrDefault(pos, TianshuMultiblockComponent.OTHER));
            assertTrue(attempt.formed(), direction + ": " + attempt.issues());
        }
    }

    private static Map<TianshuAutoBuildPlan.Target, Integer> counts(TianshuAutoBuildPlan plan) {
        var result = new EnumMap<TianshuAutoBuildPlan.Target, Integer>(TianshuAutoBuildPlan.Target.class);
        for (var placement : plan.placements()) result.merge(placement.target(), 1, Integer::sum);
        return result;
    }

    private static TianshuMultiblockComponent componentFor(TianshuAutoBuildPlan.Target target) {
        return switch (target) {
            case CASING -> TianshuMultiblockComponent.CASING;
            case COOLING -> TianshuMultiblockComponent.COOLING;
            case GLASS -> TianshuMultiblockComponent.GLASS;
            case PORT -> TianshuMultiblockComponent.PORT;
        };
    }

    private static void installCoreUnits(Map<BlockPos, TianshuMultiblockComponent> local) {
        boolean dispatchInstalled = false;
        for (int x = 2; x <= 4; x++) {
            for (int y = 2; y <= 4; y++) {
                for (int z = 2; z <= 4; z++) {
                    var pos = new BlockPos(x, y, z);
                    if (pos.equals(new BlockPos(3, 3, 3))) {
                        local.put(pos, TianshuMultiblockComponent.MAIN_BASELINE);
                    } else if (!dispatchInstalled) {
                        local.put(pos, TianshuMultiblockComponent.PARALLEL_UNIT);
                        dispatchInstalled = true;
                    } else {
                        local.put(pos, TianshuMultiblockComponent.BLANK_UNIT);
                    }
                }
            }
        }
    }
}
