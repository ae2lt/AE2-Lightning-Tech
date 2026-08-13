package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class Ae2TexturePortCompatibilityTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path AE2_SCREENS = RESOURCES.resolve(Path.of("assets", "ae2", "screens"));
    private static final Path AE2LT_MODELS = RESOURCES.resolve(Path.of("assets", "ae2lt", "models"));

    @Test
    void poweredMachineScreensUseTheirBundledAe2_1_21EnergySprite() throws IOException {
        var screens = Map.of(
                "atmospheric_ionizer.json", "ae2lt:textures/guis/lightning_collector.png",
                "crystal_catalyzer.json", "ae2lt:textures/guis/crystal_catalyzer.png",
                "lightning_assembly_chamber.json", "ae2lt:textures/guis/lightning_assembly_chamber.png",
                "lightning_simulation_room.json", "ae2lt:textures/guis/lightning_simulation_room.png",
                "overload_processing_factory.json", "ae2lt:textures/guis/overload_processing_factory.png",
                "tesla_coil.json", "ae2lt:textures/guis/tesla_coil.png");

        for (var entry : screens.entrySet()) {
            var screen = JsonParser.parseString(Files.readString(AE2_SCREENS.resolve(entry.getKey())))
                    .getAsJsonObject();
            var energyBar = screen.getAsJsonObject("images").getAsJsonObject("energyBar");
            assertTrue(entry.getValue().equals(energyBar.get("texture").getAsString()), entry.getKey());
            assertTrue("[176,0,6,18]".equals(energyBar.getAsJsonArray("srcRect").toString()), entry.getKey());
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

    @Test
    void teslaCoilTopRingDisablesForgeAmbientOcclusion() throws IOException {
        var blockstate = JsonParser.parseString(Files.readString(RESOURCES.resolve(Path.of(
                "assets", "ae2lt", "blockstates", "tesla_coil.json")))).getAsJsonObject();
        for (String facing : List.of("north", "east", "south", "west")) {
            boolean hasOffVariant = false;
            for (var entry : blockstate.getAsJsonArray("multipart")) {
                var part = entry.getAsJsonObject();
                var when = part.getAsJsonObject("when");
                var apply = part.getAsJsonObject("apply");
                if (facing.equals(when.get("facing").getAsString())
                        && when.has("working")
                        && "false".equals(when.get("working").getAsString())
                        && "ae2lt:block/tesla_coil_off".equals(apply.get("model").getAsString())) {
                    hasOffVariant = true;
                    break;
                }
            }
            assertTrue(hasOffVariant, facing);
        }

        for (String modelName : List.of("tesla_coil_off.json", "tesla_coil_on.json")) {
            var model = JsonParser.parseString(Files.readString(
                    AE2LT_MODELS.resolve(Path.of("block", modelName)))).getAsJsonObject();
            int ringCount = 0;
            for (var element : model.getAsJsonArray("elements")) {
                var ring = element.getAsJsonObject();
                if (!ring.has("name") || !"ring".equals(ring.get("name").getAsString())) {
                    continue;
                }
                ringCount++;
                assertTrue(ring.has("shade") && !ring.get("shade").getAsBoolean(), modelName);
                for (var face : ring.getAsJsonObject("faces").asMap().values()) {
                    var forgeData = face.getAsJsonObject().getAsJsonObject("forge_data");
                    assertTrue(forgeData != null
                            && forgeData.has("ambient_occlusion")
                            && !forgeData.get("ambient_occlusion").getAsBoolean(), modelName);
                }
            }
            assertTrue(ringCount == 4, modelName);
        }
    }
}
