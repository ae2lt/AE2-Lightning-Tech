package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void matrixRestoresPatternStorageOwnersBeforePublishingCraftingProvider() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/"
                        + "MatrixControllerBlockEntity.java"));

        int bindMembers = controller.indexOf(
                "private void bindMembers(MatrixMultiblockScanResult result)");
        int restoreOwner = controller.indexOf(
                "storage.setControllerPos(worldPosition);", bindMembers);
        int publishPort = controller.indexOf(
                "linkedPort.bindToController(worldPosition, machineId);", bindMembers);
        int bindTerminalLink = controller.indexOf(
                "storage.bindToController(worldPosition, linkedPort);", bindMembers);

        assertTrue(bindMembers >= 0);
        assertTrue(restoreOwner > bindMembers);
        assertTrue(publishPort > restoreOwner,
                "Every persisted pattern storage must be controller-owned before AE2 refreshes the provider");
        assertTrue(bindTerminalLink > publishPort,
                "Pattern terminal leaf nodes must be connected after the port is published");
    }

    @Test
    void matrixCoalescesSameTickPatternChangesBeforeRefreshingAe2() throws Exception {
        String port = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/MatrixPortBlockEntity.java"));
        String portBlock = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/block/MatrixPortBlock.java"));

        int serverTick = port.indexOf("public static void serverTick(");
        int flushUpdate = port.indexOf("port.flushPatternUpdate();", serverTick);
        int bindingIntervalCheck = port.indexOf(
                "level.getGameTime() < port.nextBindingCheckTick", serverTick);

        assertTrue(port.contains("patternUpdatePending = true;"));
        assertFalse(port.contains("lastPatternUpdateTick"));
        assertTrue(flushUpdate > serverTick);
        assertTrue(bindingIntervalCheck > flushUpdate,
                "Pending pattern changes must flush every tick, not at the 20-tick binding interval");
        assertTrue(portBlock.contains(
                "MatrixPortBlockEntity.serverTick(tickLevel, pos, tickState, port);"),
                "The matrix port block must actually install its block-entity ticker");
    }

    @Test
    void fastPlanningToggleIsOwnedByControllerAndAppliedToItsPool() throws Exception {
        assertEquals(boolean.class, TianshuSupercomputerControllerBlockEntity.class
                .getDeclaredField("fastPlanningEnabled").getType());
        assertEquals(boolean.class, TianshuSupercomputerControllerBlockEntity.class
                .getDeclaredMethod("isFastPlanningEnabled").getReturnType());
        assertEquals(void.class, TianshuSupercomputerControllerBlockEntity.class
                .getDeclaredMethod("toggleFastPlanning").getReturnType());
        assertEquals(boolean.class, TimeWheelCraftingCpuPool.class
                .getDeclaredMethod("isFastPlanningEnabled").getReturnType());
        assertEquals(void.class, TimeWheelCraftingCpuPool.class
                .getDeclaredMethod("setFastPlanningEnabled", boolean.class).getReturnType());

        var pool = new TimeWheelCraftingCpuPool(null, 1L, 0, 1L, false);
        assertTrue(pool.isFastPlanningEnabled());
        pool.setFastPlanningEnabled(false);
        assertFalse(pool.isFastPlanningEnabled());
    }

    @Test
    void closedLoopProviderRechecksMembersAfterNetworkProvidersReload() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/"
                        + "TianshuSupercomputerControllerBlockEntity.java"));

        assertTrue(controller.contains(
                "refreshClosedLoopProviderForDependencyChanges(port)"));
        assertTrue(controller.contains(
                "closedLoopDependencyChanges.shouldRecheck(grid.getCraftingService())"));
        assertTrue(controller.contains(
                "available.patternDefinitions().equals(publishedClosedLoopPatternDefinitions)"));
    }
}
