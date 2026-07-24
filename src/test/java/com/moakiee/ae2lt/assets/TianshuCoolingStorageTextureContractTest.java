package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

final class TianshuCoolingStorageTextureContractTest {

    private static final Path BLOCKSTATES =
            Path.of("src/main/resources/assets/ae2lt/blockstates");
    private static final Path MODELS =
            Path.of("src/main/resources/assets/ae2lt/models/block");
    private static final Path TEXTURES =
            Path.of("src/main/resources/assets/ae2lt/textures/block");

    @Test
    void formedStoragesUseCoolingCompatibleConnectedModels() throws Exception {
        assertFormedStorage("closed_loop_pattern_storage");
        assertFormedStorage("closed_loop_seed_storage");

        String coolingModel = Files.readString(MODELS.resolve("phase_change_cooling_unit_formed.json"));
        assertTrue(coolingModel.contains("\"connection\": \"ae2lt:tianshu_formed_cooling_compatible\""));

        String predicates = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/ctm/ConnectionPredicates.java"));
        assertTrue(predicates.contains(
                "register(rl(\"tianshu_formed_cooling_compatible\"), TIANSHU_FORMED_COOLING_COMPATIBLE)"));

        String bakedModel = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/ctm/ConnectedTextureBakedModel.java"));
        assertTrue(bakedModel.contains("fullFace(side, overlaySprite, OVERLAY_OFFSET)"));
    }

    @Test
    void storageTexturesKeepSixteenPixelCoolingBaseAndTransparentIdentityLayer() throws Exception {
        assertComposedTexture("closed_loop_pattern_storage");
        assertComposedTexture("closed_loop_seed_storage");
    }

    private static void assertFormedStorage(String name) throws Exception {
        String blockstate = Files.readString(BLOCKSTATES.resolve(name + ".json"));
        assertTrue(blockstate.contains("\"model\": \"ae2lt:block/" + name + "_formed\""));

        String model = Files.readString(MODELS.resolve(name + "_formed.json"));
        assertTrue(model.contains("\"base\": \"ae2lt:block/tianshu/phase_change_cooling_unit_formed\""));
        assertTrue(model.contains("\"ctm\": \"ae2lt:block/tianshu/phase_change_cooling_unit_ctm\""));
        assertTrue(model.contains("\"connection\": \"ae2lt:tianshu_formed_cooling_compatible\""));
        assertTrue(model.contains("\"overlay\": \"ae2lt:block/tianshu/" + name + "_layer\""));
    }

    private static void assertTextureDimensions(Path path, int width, int height) throws Exception {
        assertTrue(Files.isRegularFile(path), path + " should exist");
        BufferedImage image = ImageIO.read(path.toFile());
        assertEquals(width, image.getWidth(), path + " width");
        assertEquals(height, image.getHeight(), path + " height");
    }

    private static void assertComposedTexture(String name) throws Exception {
        Path compositePath = TEXTURES.resolve(name + ".png");
        Path layerPath = TEXTURES.resolve("tianshu/" + name + "_layer.png");
        Path basePath = TEXTURES.resolve("tianshu/phase_change_cooling_unit.png");
        assertTextureDimensions(compositePath, 16, 16);
        assertTextureDimensions(layerPath, 16, 16);

        BufferedImage composite = ImageIO.read(compositePath.toFile());
        BufferedImage layer = ImageIO.read(layerPath.toFile());
        BufferedImage base = ImageIO.read(basePath.toFile());
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int layerPixel = layer.getRGB(x, y);
                int alpha = layerPixel >>> 24;
                int expected = alpha == 0 ? base.getRGB(x, y) : layerPixel;
                assertEquals(expected, composite.getRGB(x, y), name + " pixel " + x + "," + y);
            }
        }
    }
}
