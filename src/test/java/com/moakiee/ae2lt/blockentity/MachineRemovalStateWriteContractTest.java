package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MachineRemovalStateWriteContractTest {
    private static final Path BLOCK_ENTITIES = Path.of("src/main/java/com/moakiee/ae2lt/blockentity");

    @Test
    void assemblyRemovalSuppressesGridCallbackStateWrites() throws Exception {
        String source = read("LightningAssemblyChamberBlockEntity.java");

        assertAppearsBefore(source, "removing = true", "frequencyBinding.setRemoved()");
        assertTrue(source.contains("if (!removing && reason != IGridNodeListener.State.GRID_BOOT)"));
    }

    @Test
    void machineStateWritersRequireTheLiveBlockEntity() throws Exception {
        for (String file : List.of(
                "LightningAssemblyChamberBlockEntity.java",
                "LightningSimulationChamberBlockEntity.java",
                "OverloadProcessingFactoryBlockEntity.java",
                "CrystalCatalyzerBlockEntity.java",
                "TeslaCoilBlockEntity.java",
                "LightningCollectorBlockEntity.java",
                "AtmosphericIonizerBlockEntity.java")) {
            String source = read(file);
            assertTrue(source.contains("level.getBlockState(worldPosition)"), file);
            assertTrue(source.contains("level.getBlockEntity(worldPosition) == this"), file);
        }
    }

    private static String read(String file) throws Exception {
        return Files.readString(BLOCK_ENTITIES.resolve(file));
    }

    private static void assertAppearsBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0, () -> "Missing source fragment: " + first);
        assertTrue(secondIndex >= 0, () -> "Missing source fragment: " + second);
        assertTrue(firstIndex < secondIndex, () -> first + " must appear before " + second);
    }
}
