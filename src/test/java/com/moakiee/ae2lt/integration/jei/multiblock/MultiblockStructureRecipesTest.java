package com.moakiee.ae2lt.integration.jei.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanner;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockTemplate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

class MultiblockStructureRecipesTest {
    private static final BlockPos CONTROLLER = new BlockPos(10, 20, 30);
    private static final Direction ORIENTATION = Direction.EAST;
    private static final BlockPos PORT = new BlockPos(6, 5, 3);

    @Test
    void defaultMatrixCoreLayoutSatisfiesScannerRequirements() {
        Map<BlockPos, MatrixMultiblockComponent> components = new HashMap<>();
        int threadUnits = 0;

        for (var entry : MatrixMultiblockTemplate.entries()) {
            MatrixMultiblockComponent component = componentFor(entry);
            components.put(MatrixMultiblockScanner.worldPos(
                    CONTROLLER, entry.localPos(), ORIENTATION), component);
            if (component == MatrixMultiblockComponent.THREAD_UNIT_T1) {
                threadUnits++;
            }
        }

        var attempt = MatrixMultiblockScanner.scan(
                CONTROLLER, ORIENTATION, components::get);

        assertEquals(1, threadUnits);
        assertTrue(attempt.formed(), attempt.issues().toString());
    }

    private static MatrixMultiblockComponent componentFor(MatrixMultiblockTemplate.Entry entry) {
        return switch (entry.role()) {
            case EMPTY -> MatrixMultiblockComponent.AIR;
            case CASING -> MatrixMultiblockComponent.MATRIX_CASING;
            case CONSTRAINT_FRAME -> MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case GLASS -> MatrixMultiblockComponent.MATRIX_GLASS;
            case CONTROLLER -> MatrixMultiblockComponent.MATRIX_CONTROLLER;
            case PORT_CANDIDATE -> entry.localPos().equals(PORT)
                    ? MatrixMultiblockComponent.MATRIX_PORT
                    : MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case PATTERN_BAY -> MatrixMultiblockComponent.PATTERN_STORAGE_T1;
            case CRAFTING_BAY -> craftingComponent(entry.localPos());
        };
    }

    private static MatrixMultiblockComponent craftingComponent(BlockPos pos) {
        if (pos.equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)) {
            return MatrixMultiblockComponent.STABLE_MAIN_CORE;
        }
        return MultiblockStructureRecipes.isDefaultMatrixThreadPosition(pos)
                ? MatrixMultiblockComponent.THREAD_UNIT_T1
                : MatrixMultiblockComponent.BLANK_UNIT;
    }
}
