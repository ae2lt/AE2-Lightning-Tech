package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Ae2TexturePortCompatibilityTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path AE2_SCREENS = RESOURCES.resolve(Path.of("assets", "ae2", "screens"));
    private static final Path AE2LT_MODELS = RESOURCES.resolve(Path.of("assets", "ae2lt", "models"));

    @Test
    void machineScreensUseTheAe2_1_20InscriberProgressSprite() throws IOException {
        for (String screen : List.of(
                "atmospheric_ionizer.json",
                "crystal_catalyzer.json",
                "lightning_assembly_chamber.json",
                "lightning_simulation_room.json",
                "tesla_coil.json")) {
            String json = Files.readString(AE2_SCREENS.resolve(screen));
            assertTrue(json.contains("[135, 177, 6, 18]"), screen);
            assertFalse(json.contains("[176, 0, 6, 18]"), screen);
        }

        String seedStorage = Files.readString(AE2_SCREENS.resolve("tianshu_seed_storage.json"));
        assertTrue(seedStorage.contains("[0, 0, 176, 199]"));
        assertFalse(seedStorage.contains("[0, 0, 176, 201]"));
    }

    @Test
    void driveCellsKeepTheirAtlasRowsInsteadOfUsingAe2_1_20TierParents() throws IOException {
        var cells = AE2LT_MODELS.resolve(Path.of("block", "drive", "cells"));
        var expectedRows = List.of(
                "lightning_storage_component_i.json:0, 0, 6, 2",
                "lightning_storage_component_ii.json:0, 2, 6, 4",
                "lightning_storage_component_iii.json:0, 4, 6, 6",
                "lightning_storage_component_iv.json:0, 6, 6, 8",
                "lightning_storage_component_v.json:0, 8, 6, 10",
                "infinite_storage_cell.json:0, 12, 6, 14");

        for (String declaration : expectedRows) {
            String[] parts = declaration.split(":", 2);
            String json = Files.readString(cells.resolve(parts[0]));
            assertTrue(json.contains("[" + parts[1] + "]"), parts[0]);
            assertFalse(json.contains("\"parent\": \"ae2:block/drive/cells/"), parts[0]);
        }
    }

    @Test
    void inheritedModelsSupplyTheTextureSlotsAndUvsExpectedByThePort() throws IOException {
        String terminal = Files.readString(AE2LT_MODELS.resolve(Path.of(
                "item", "tianshu_pattern_encoding_terminal.json")));
        assertTrue(terminal.contains("\"front\": \"ae2:part/monitor_front\""));

        String cable = Files.readString(AE2LT_MODELS.resolve(Path.of("item", "overloaded_cable.json")));
        assertTrue(cable.contains("\"parent\": \"ae2lt:item/covered_dense_cable_base\""));

        String cableBase = Files.readString(AE2LT_MODELS.resolve(Path.of("item", "covered_dense_cable_base.json")));
        assertTrue(cableBase.contains("[4, 4, 12, 12]"));
        assertTrue(cableBase.contains("[16, 4, 13, 12]"));
    }

    @Test
    void customScreensSampleTheAe2_1_20CheckboxAtlas() throws IOException {
        String encoder = Files.readString(Path.of(
                "src", "main", "java", "com", "moakiee", "ae2lt", "client",
                "OverloadPatternEncoderScreen.java"));
        assertTrue(encoder.contains("ENTRY_SWITCH_WIDTH = 14"));
        assertTrue(encoder.contains("ENTRY_SWITCH_HEIGHT = 14"));
        assertTrue(encoder.contains("? 14 : 0"));

        String hub = Files.readString(Path.of(
                "src", "main", "java", "com", "moakiee", "ae2lt", "client", "hub",
                "DeviceHubScreen.java"));
        assertTrue(hub.contains("CHECKBOX_WIDTH = 14"));
        assertTrue(hub.contains("CHECKBOX_HEIGHT = 14"));
        assertTrue(hub.contains("CHECKBOX_OFF_SRC_Y = 0"));
        assertTrue(hub.contains("CHECKBOX_ON_SRC_Y = 14"));
    }

    @Test
    void straightOverloadedCablesUseCubeBuilderPixelUvs() throws IOException {
        String renderer = Files.readString(Path.of(
                "src", "main", "java", "com", "moakiee", "ae2lt", "client", "render",
                "OverloadedCableRenderHelper.java"));
        assertTrue(renderer.contains("setStraightCableUVs(cubeBuilder, facing, 3, 13)"));
        assertFalse(renderer.contains("setStraightCableUVs(cubeBuilder, facing, 3 / 16f, 13 / 16f)"));
    }

    @Test
    void pigmeeAssemblerUsesTheAe2_1_20CutoutLayer() throws IOException {
        String model = Files.readString(AE2LT_MODELS.resolve(Path.of(
                "block", "pigmee_molecular_assembler.json")));
        assertTrue(model.contains("\"parent\": \"ae2:block/molecular_assembler\""));
        assertTrue(model.contains("\"render_type\": \"cutout\""));

        String clientInit = Files.readString(Path.of(
                "src", "main", "java", "com", "moakiee", "ae2lt", "client",
                "ModEntityRenderers.java"));
        assertTrue(clientInit.contains("ModBlocks.PIGMEE_MOLECULAR_ASSEMBLER.get()"));
        assertTrue(clientInit.contains("RenderType.cutout()"));
    }
}
