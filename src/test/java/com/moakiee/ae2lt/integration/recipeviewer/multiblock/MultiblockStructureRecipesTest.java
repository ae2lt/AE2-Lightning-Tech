package com.moakiee.ae2lt.integration.recipeviewer.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanner;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockTemplate;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockComponent;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockTemplate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

class MultiblockStructureRecipesTest {
    private static final BlockPos CONTROLLER = new BlockPos(10, 20, 30);

    @Test
    void defaultMatrixPreviewSatisfiesMinimumFormationRequirements() {
        Map<BlockPos, MatrixMultiblockComponent> components = new HashMap<>();
        int threadUnits = 0;

        for (var entry : MatrixMultiblockTemplate.entries()) {
            MatrixMultiblockComponent component = matrixComponentFor(entry);
            components.put(MatrixMultiblockScanner.worldPos(
                    CONTROLLER, entry.localPos(), Direction.EAST), component);
            if (component == MatrixMultiblockComponent.THREAD_UNIT_T1) {
                threadUnits++;
            }
        }

        var attempt = MatrixMultiblockScanner.scan(
                CONTROLLER, Direction.EAST, components::get);

        assertEquals(1, threadUnits);
        assertTrue(attempt.formed(), attempt.issues().toString());
    }

    @Test
    void defaultTianshuPreviewSatisfiesMinimumFormationRequirements() {
        Map<BlockPos, TianshuMultiblockComponent> components = new HashMap<>();
        int parallelUnits = 0;

        for (int y = 0; y < TianshuMultiblockTemplate.SIZE; y++) {
            for (int z = 0; z < TianshuMultiblockTemplate.SIZE; z++) {
                for (int x = 0; x < TianshuMultiblockTemplate.SIZE; x++) {
                    BlockPos local = new BlockPos(x, y, z);
                    TianshuMultiblockComponent component = tianshuComponentFor(local);
                    components.put(TianshuMultiblockScanner.worldPos(
                            CONTROLLER, local, Direction.WEST), component);
                    if (component == TianshuMultiblockComponent.PARALLEL_UNIT) {
                        parallelUnits++;
                    }
                }
            }
        }

        var attempt = TianshuMultiblockScanner.scan(
                CONTROLLER, Direction.WEST, components::get);

        assertEquals(1, parallelUnits);
        assertTrue(attempt.formed(), attempt.issues().toString());
    }

    private static MatrixMultiblockComponent matrixComponentFor(MatrixMultiblockTemplate.Entry entry) {
        return switch (entry.role()) {
            case EMPTY -> MatrixMultiblockComponent.AIR;
            case CASING -> MatrixMultiblockComponent.MATRIX_CASING;
            case CONSTRAINT_FRAME -> MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case GLASS -> MatrixMultiblockComponent.MATRIX_GLASS;
            case CONTROLLER -> MatrixMultiblockComponent.MATRIX_CONTROLLER;
            case PORT_CANDIDATE -> entry.localPos().equals(new BlockPos(6, 5, 3))
                    ? MatrixMultiblockComponent.MATRIX_PORT
                    : MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case PATTERN_BAY -> MatrixMultiblockComponent.PATTERN_STORAGE_T1;
            case CRAFTING_BAY -> entry.localPos().equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)
                    ? MatrixMultiblockComponent.STABLE_MAIN_CORE
                    : MultiblockStructureRecipes.isDefaultMatrixThreadPosition(entry.localPos())
                            ? MatrixMultiblockComponent.THREAD_UNIT_T1
                            : MatrixMultiblockComponent.BLANK_UNIT;
        };
    }

    private static TianshuMultiblockComponent tianshuComponentFor(BlockPos pos) {
        return switch (TianshuMultiblockTemplate.roleAt(pos)) {
            case IGNORED -> TianshuMultiblockComponent.AIR;
            case CASING -> TianshuMultiblockComponent.CASING;
            case COOLING -> TianshuMultiblockComponent.COOLING;
            case GLASS -> TianshuMultiblockComponent.GLASS;
            case CONTROLLER -> TianshuMultiblockComponent.CONTROLLER;
            case PORT_CANDIDATE -> pos.equals(TianshuMultiblockTemplate.LOWER_PORT)
                    ? TianshuMultiblockComponent.PORT
                    : TianshuMultiblockComponent.COOLING;
            case CORE_RESERVED -> pos.equals(new BlockPos(3, 3, 3))
                    ? TianshuMultiblockComponent.MAIN_BASELINE
                    : MultiblockStructureRecipes.isDefaultTianshuParallelPosition(pos)
                            ? TianshuMultiblockComponent.PARALLEL_UNIT
                            : TianshuMultiblockComponent.BLANK_UNIT;
        };
    }
}
