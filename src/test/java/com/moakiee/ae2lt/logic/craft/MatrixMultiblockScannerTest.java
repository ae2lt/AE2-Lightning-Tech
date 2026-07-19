package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

class MatrixMultiblockScannerTest {
    private static final BlockPos CONTROLLER = new BlockPos(10, 20, 30);
    private static final Direction ORIENTATION = Direction.EAST;
    private static final BlockPos DEFAULT_PORT_LOCAL = new BlockPos(6, 5, 3);

    @Test
    void craftingBayRequiresEveryNonCenterSlotToBeFilled() {
        var full = completeStructure();

        var formed = MatrixMultiblockScanner.scan(CONTROLLER, ORIENTATION, full::get);

        assertTrue(formed.formed());

        var withAirGap = new HashMap<>(full);
        withAirGap.put(worldPos(firstNonCenterCraftingSlot()), MatrixMultiblockComponent.AIR);

        var missingFiller = MatrixMultiblockScanner.scan(CONTROLLER, ORIENTATION, withAirGap::get);

        assertFalse(missingFiller.formed());
        assertTrue(missingFiller.issues().contains(MatrixMultiblockScanIssue.UNEXPECTED_COMPONENT));
    }

    @Test
    void mainCoreFormsWithoutASeparateClosedLoopProcessor() {
        var attempt = MatrixMultiblockScanner.scan(
                CONTROLLER, ORIENTATION, completeStructure()::get);
        assertTrue(attempt.formed(), attempt.issues().toString());
        assertTrue(attempt.result().craftingProfile().isValid());
    }

    @Test
    void scannerEnforcesSharedAmplifierRules() {
        var stableWithAmplifier = completeStructure();
        stableWithAmplifier.put(
                worldPos(secondNonCenterCraftingSlot()),
                MatrixMultiblockComponent.MULTIPLIER_SUB_CORE_T1);

        var stableAttempt = MatrixMultiblockScanner.scan(
                CONTROLLER, ORIENTATION, stableWithAmplifier::get);
        assertFalse(stableAttempt.formed());
        assertTrue(stableAttempt.issues().contains(MatrixMultiblockScanIssue.AMPLIFIER_NOT_SUPPORTED));

        var quantumWithSixteen = completeStructure();
        quantumWithSixteen.put(
                worldPos(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL),
                MatrixMultiblockComponent.QUANTUM_MAIN_CORE);
        int placed = 0;
        for (var entry : MatrixMultiblockTemplate.entries()) {
            if (entry.role() == MatrixMultiblockRole.CRAFTING_BAY
                    && !entry.localPos().equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)
                    && !entry.localPos().equals(firstNonCenterCraftingSlot())
                    && placed < 16) {
                quantumWithSixteen.put(
                        worldPos(entry.localPos()), MatrixMultiblockComponent.MULTIPLIER_SUB_CORE_T1);
                placed++;
            }
        }

        var quantumAttempt = MatrixMultiblockScanner.scan(
                CONTROLLER, ORIENTATION, quantumWithSixteen::get);
        assertFalse(quantumAttempt.formed());
        assertTrue(quantumAttempt.issues().contains(
                MatrixMultiblockScanIssue.MULTIPLIER_LIMIT_EXCEEDED));
    }

    private static Map<BlockPos, MatrixMultiblockComponent> completeStructure() {
        var components = new HashMap<BlockPos, MatrixMultiblockComponent>();
        for (var entry : MatrixMultiblockTemplate.entries()) {
            components.put(worldPos(entry.localPos()), componentFor(entry));
        }
        return components;
    }

    private static MatrixMultiblockComponent componentFor(MatrixMultiblockTemplate.Entry entry) {
        return switch (entry.role()) {
            case EMPTY -> MatrixMultiblockComponent.AIR;
            case CASING -> MatrixMultiblockComponent.MATRIX_CASING;
            case CONSTRAINT_FRAME -> MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case GLASS -> MatrixMultiblockComponent.MATRIX_GLASS;
            case CONTROLLER -> MatrixMultiblockComponent.MATRIX_CONTROLLER;
            case PORT_CANDIDATE -> entry.localPos().equals(DEFAULT_PORT_LOCAL)
                    ? MatrixMultiblockComponent.MATRIX_PORT
                    : MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case PATTERN_BAY -> MatrixMultiblockComponent.PATTERN_STORAGE_T1;
            case CRAFTING_BAY -> entry.localPos().equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)
                    ? MatrixMultiblockComponent.STABLE_MAIN_CORE
                    : entry.localPos().equals(firstNonCenterCraftingSlot())
                            ? MatrixMultiblockComponent.THREAD_SUB_CORE_T1
                            : MatrixMultiblockComponent.BLANK_SUB_CORE;
        };
    }

    private static BlockPos firstNonCenterCraftingSlot() {
        for (var entry : MatrixMultiblockTemplate.entries()) {
            if (entry.role() == MatrixMultiblockRole.CRAFTING_BAY
                    && !entry.localPos().equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)) {
                return entry.localPos();
            }
        }
        throw new IllegalStateException("matrix template has no non-center crafting slot");
    }

    private static BlockPos secondNonCenterCraftingSlot() {
        boolean skippedFirst = false;
        for (var entry : MatrixMultiblockTemplate.entries()) {
            if (entry.role() == MatrixMultiblockRole.CRAFTING_BAY
                    && !entry.localPos().equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)) {
                if (skippedFirst) return entry.localPos();
                skippedFirst = true;
            }
        }
        throw new IllegalStateException("matrix template has fewer than two non-center crafting slots");
    }

    private static BlockPos worldPos(BlockPos localPos) {
        return MatrixMultiblockScanner.worldPos(CONTROLLER, localPos, ORIENTATION);
    }
}
