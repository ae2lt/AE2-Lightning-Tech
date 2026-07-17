package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

class MultiblockPortBindingTest {
    private static final BlockPos CONTROLLER = new BlockPos(10, 20, 30);
    private static final UUID MACHINE_ID = UUID.fromString("94b9fb92-e00f-4c03-87f7-e350df12f1e0");

    @Test
    void unchangedActiveBindingDoesNotRequireNeighborNotification() {
        assertFalse(MultiblockPortBinding.changes(
                true, CONTROLLER, MACHINE_ID, CONTROLLER, MACHINE_ID));
    }

    @Test
    void lifecycleAndIdentityTransitionsRequireNeighborNotification() {
        assertTrue(MultiblockPortBinding.changes(
                false, null, null, CONTROLLER, MACHINE_ID));
        assertTrue(MultiblockPortBinding.changes(
                false, CONTROLLER, MACHINE_ID, CONTROLLER, MACHINE_ID));
        assertTrue(MultiblockPortBinding.changes(
                true, CONTROLLER, MACHINE_ID, null, null));
        assertTrue(MultiblockPortBinding.changes(
                true, CONTROLLER, MACHINE_ID, CONTROLLER.offset(1, 0, 0), MACHINE_ID));
        assertTrue(MultiblockPortBinding.changes(
                true, CONTROLLER, MACHINE_ID, CONTROLLER, UUID.randomUUID()));
        assertFalse(MultiblockPortBinding.changes(
                false, null, null, null, null));
    }

    @Test
    void tianshuRuntimeTransitionRequiresNeighborNotification() {
        var firstPool = new Object();
        var replacementPool = new Object();

        assertFalse(MultiblockPortBinding.changes(
                true, CONTROLLER, MACHINE_ID, firstPool,
                CONTROLLER, MACHINE_ID, firstPool));
        assertTrue(MultiblockPortBinding.changes(
                true, CONTROLLER, MACHINE_ID, firstPool,
                CONTROLLER, MACHINE_ID, replacementPool));
        assertTrue(MultiblockPortBinding.changes(
                false, null, null, null,
                CONTROLLER, MACHINE_ID, firstPool));
        assertTrue(MultiblockPortBinding.changes(
                true, CONTROLLER, MACHINE_ID, firstPool,
                null, null, null));
    }
}
