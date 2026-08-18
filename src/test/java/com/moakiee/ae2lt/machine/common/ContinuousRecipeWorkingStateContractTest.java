package com.moakiee.ae2lt.machine.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ContinuousRecipeWorkingStateContractTest {
    private static final Path BLOCK_ENTITY_DIR = Path.of(
            "src/main/java/com/moakiee/ae2lt/blockentity");

    @Test
    void successfulRecipeCyclesLeaveTheIdleDecisionToTheNextGridTick() throws Exception {
        assertCompletionDoesNotMarkIdle(
                "OverloadProcessingFactoryBlockEntity.java",
                "public boolean completeLockedRecipe(",
                "public void openMenu(");
        assertCompletionDoesNotMarkIdle(
                "CrystalCatalyzerBlockEntity.java",
                "public boolean completeLockedRecipe(",
                "public long getMachineStoredEnergy()");
        assertCompletionDoesNotMarkIdle(
                "LightningAssemblyChamberBlockEntity.java",
                "public boolean completeLockedRecipe(",
                "public void openMenu(");
        assertCompletionDoesNotMarkIdle(
                "LightningSimulationChamberBlockEntity.java",
                "public boolean completeLockedRecipe(",
                "public void openMenu(");
        assertCompletionDoesNotMarkIdle(
                "TeslaCoilBlockEntity.java",
                "public boolean commitLockedMode()",
                "public void openMenu(");
    }

    @Test
    void tickDriversStillMarkMachinesIdleAfterConfirmingThereIsNoNextCycle() throws Exception {
        String commonLogic = readSource(Path.of(
                "src/main/java/com/moakiee/ae2lt/machine/common/AbstractGridRecipeMachineLogic.java"));
        String noRecipeBranch = section(
                commonLogic,
                "if (lockedRecipe.isEmpty())",
                "host.setWorking(true);");
        assertTrue(noRecipeBranch.contains("host.setWorking(false);"));

        String teslaLogic = readSource(Path.of(
                "src/main/java/com/moakiee/ae2lt/machine/teslacoil/TeslaCoilLogic.java"));
        String noResourcesBranch = section(
                teslaLogic,
                "if (!host.hasLocalResourcesForMinimumOperation())",
                "if (host.canStartSelectedMode()");
        assertTrue(noResourcesBranch.contains("host.setWorking(false);"));
    }

    private static void assertCompletionDoesNotMarkIdle(
            String fileName,
            String methodStart,
            String methodEnd) throws Exception {
        Path path = BLOCK_ENTITY_DIR.resolve(fileName);
        String completionMethod = section(readSource(path), methodStart, methodEnd);
        assertFalse(
                completionMethod.contains("setWorking(false);"),
                () -> fileName + " must not pulse idle after a successful cycle");
    }

    private static String readSource(Path path) throws Exception {
        return Files.readString(path).replace("\r\n", "\n");
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0, () -> "Missing source marker: " + startMarker);
        assertTrue(end > start, () -> "Missing source marker: " + endMarker);
        return source.substring(start, end);
    }
}
