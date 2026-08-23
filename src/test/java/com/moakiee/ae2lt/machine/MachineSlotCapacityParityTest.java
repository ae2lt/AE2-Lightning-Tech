package com.moakiee.ae2lt.machine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class MachineSlotCapacityParityTest {
    private static final Path MACHINE_DIR = Path.of("src/main/java/com/moakiee/ae2lt/machine");
    private static final Path BLOCK_ENTITY_DIR =
            Path.of("src/main/java/com/moakiee/ae2lt/blockentity");
    private static final Path ITEM_DIR = Path.of("src/main/java/com/moakiee/ae2lt/item");
    private static final Path ITEM_REGISTRY =
            Path.of("src/main/java/com/moakiee/ae2lt/registry/ModItems.java");

    @Test
    void itemSlotCapacitiesMatchTheMainProjectContract() throws Exception {
        assertContains("atmosphericionizer/AtmosphericIonizerInventory.java", "return 1;");
        assertContains("lightningcollector/LightningCollectorInventory.java", "return 1;");

        assertContains("teslacoil/TeslaCoilInventory.java", "DUST_SLOT_LIMIT = 1024");
        assertContains("teslacoil/TeslaCoilInventory.java", "slot == SLOT_MATRIX ? 1 : DUST_SLOT_LIMIT");

        assertContains("lightningchamber/LightningSimulationChamberInventory.java", "LARGE_SLOT_LIMIT = 8192");
        assertContains("lightningchamber/LightningSimulationChamberInventory.java", "MATRIX_SLOT_LIMIT = 1");
        assertContains("lightningchamber/LightningSimulationChamberInventory.java",
                "slot == SLOT_CATALYST ? MATRIX_SLOT_LIMIT : LARGE_SLOT_LIMIT");

        assertContains("lightningassembly/LightningAssemblySlotLimits.java", "LARGE_SLOT_LIMIT = 8192");
        assertContains("lightningassembly/LightningAssemblySlotLimits.java", "MATRIX_SLOT_LIMIT = 1");
        assertContains("lightningassembly/LightningAssemblyChamberInventory.java",
                "return LightningAssemblySlotLimits.getSlotLimit(slot)");

        assertContains("crystalcatalyzer/CrystalCatalyzerInventory.java", "CATALYST_SLOT_LIMIT = 256");
        assertContains("crystalcatalyzer/CrystalCatalyzerInventory.java", "OUTPUT_SLOT_LIMIT = 1024");
        assertContains("crystalcatalyzer/CrystalCatalyzerInventory.java", "MATRIX_SLOT_LIMIT = 1");

        assertContains("firmament/FirmamentConversionInventory.java", "SLOT_LIMIT = 64");

        assertContains("overloadfactory/OverloadProcessingFactoryInventory.java", "LARGE_SLOT_LIMIT = 16_384");
        assertContains("overloadfactory/OverloadProcessingFactoryInventory.java", "MATRIX_SLOT_LIMIT = 32");
    }

    @Test
    void everyMatrixMachineRetainsTheSharedHostAndMemoryCardIntegration() throws Exception {
        for (String fileName : List.of(
                "TeslaCoilBlockEntity.java",
                "LightningSimulationChamberBlockEntity.java",
                "LightningAssemblyChamberBlockEntity.java",
                "CrystalCatalyzerBlockEntity.java",
                "OverloadProcessingFactoryBlockEntity.java")) {
            String source = Files.readString(BLOCK_ENTITY_DIR.resolve(fileName));
            assertTrue(source.contains("LightningCollapseMatrixHost"),
                    fileName + " must support direct matrix insertion");
            assertTrue(source.contains("public IItemHandlerModifiable getMatrixInventory()"),
                    fileName + " must expose its matrix inventory to the shared host");
            assertTrue(source.contains("public int getMatrixSlot()"),
                    fileName + " must identify its matrix slot");
            assertTrue(source.contains("MemoryCardConfigSupport.writeMatrixCount(tag, this)"),
                    fileName + " must export the installed matrix count");
            assertTrue(source.contains("MemoryCardConfigSupport.restoreMatrixCount(tag, player, this)"),
                    fileName + " must restore the installed matrix count");
        }
    }

    @Test
    void matrixItemRetainsSneakUseInsertionBehavior() throws Exception {
        String registrySource = Files.readString(ITEM_REGISTRY);
        assertTrue(registrySource.contains("RegistryObject<LightningCollapseMatrixItem> LIGHTNING_COLLAPSE_MATRIX"),
                "the matrix must be registered as its interactive item class");
        assertTrue(registrySource.contains("LightningCollapseMatrixItem::new"),
                "the matrix registry factory must construct the interactive item class");

        String itemSource = Files.readString(ITEM_DIR.resolve("LightningCollapseMatrixItem.java"));
        assertTrue(itemSource.contains("player.isSecondaryUseActive()"),
                "direct matrix insertion must remain gated behind sneak-use");
        assertTrue(itemSource.contains("host.insertMatricesFromHand(player, context.getHand())"),
                "sneak-use must insert matrices into the clicked machine host");
    }

    private static void assertContains(String relativePath, String expected) throws Exception {
        String source = Files.readString(MACHINE_DIR.resolve(relativePath));
        assertTrue(source.contains(expected), relativePath + " must contain: " + expected);
    }
}
