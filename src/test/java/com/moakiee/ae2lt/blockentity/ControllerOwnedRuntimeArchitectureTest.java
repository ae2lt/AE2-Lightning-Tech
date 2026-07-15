package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.logic.craft.MatrixCraftingCluster;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternRepository;
import com.moakiee.ae2lt.logic.tianshu.maintenance.TianshuInventoryMaintenanceService;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPool;

class ControllerOwnedRuntimeArchitectureTest {
    @Test
    void tianshuRuntimeIsOwnedByControllerNotPort() throws Exception {
        assertEquals(TimeWheelCraftingCpuPool.class,
                TianshuSupercomputerControllerBlockEntity.class
                        .getDeclaredField("cpuPool").getType());
        assertEquals(TianshuInventoryMaintenanceService.class,
                TianshuSupercomputerControllerBlockEntity.class
                        .getDeclaredField("maintenance").getType());
        assertEquals(ClosedLoopPatternRepository.class,
                TianshuSupercomputerControllerBlockEntity.class
                        .getDeclaredField("closedLoopPatterns").getType());

        var portFields = Arrays.stream(
                TianshuSupercomputerPortBlockEntity.class.getDeclaredFields()).toList();
        assertFalse(portFields.stream().anyMatch(field -> field.getName().equals("cpuPool")));
        assertFalse(portFields.stream().anyMatch(field ->
                field.getType() == TianshuInventoryMaintenanceService.class));
        assertFalse(portFields.stream().anyMatch(field ->
                field.getType() == ClosedLoopPatternRepository.class));
        assertTrue(portFields.stream().anyMatch(field ->
                field.getName().equals("linkedCpuPool")
                        && field.getType() == TimeWheelCraftingCpuPool.class));
    }

    @Test
    void matrixRuntimeIsOwnedByControllerNotPort() throws Exception {
        assertEquals(MatrixCraftingCluster.class,
                MatrixControllerBlockEntity.class.getDeclaredField("cluster").getType());
        assertFalse(Arrays.stream(MatrixPortBlockEntity.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == MatrixCraftingCluster.class));
    }
}
