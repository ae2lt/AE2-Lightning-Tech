package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

final class PixelGuiLayoutContractTest {

    @Test
    void workbenchScreenUsesPixelSpriteLayout() throws Exception {
        Path texture = Path.of("src/main/resources/assets/ae2lt/textures/gui/overload_workplace_gui.png");
        assertSprite(texture);

        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/OverloadDeviceWorkbenchScreen.java"));
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/OverloadDeviceWorkbenchMenu.java"));

        assertTrue(screen.contains("\"textures/gui/overload_workplace_gui.png\""));
        assertTrue(screen.contains("GUI_WIDTH = 176"));
        assertTrue(screen.contains("GUI_HEIGHT = 245"));
        assertTrue(screen.contains("TEXT_PRIMARY = 0xFF6E748C"));
        assertTrue(screen.contains("TEXT_OK = 0xFF5F8F78"));
        assertTrue(screen.contains("TEXT_ACCENT = 0xFF9A8A5B"));
        assertTrue(screen.contains("STATUS_X = 44"));
        assertTrue(screen.contains("STATUS_Y = 22"));
        assertTrue(screen.contains("MODULE_HEADER_Y = 49"));
        assertTrue(screen.contains("MODULE_CONTENT_X = 54"));
        assertTrue(screen.contains("ae2lt.overload_device_workbench.screen.network.online"));
        assertFalse(screen.contains("Grid:"));
        assertFalse(screen.contains("screen.armor_summary"));
        assertFalse(screen.contains("screen.railgun_modules"));
        assertTrue(menu.contains("DEVICE_X = 13"));
        assertTrue(menu.contains("DEVICE_Y = 20"));
        assertTrue(menu.contains("STRUCTURAL_X = 13"));
        assertTrue(menu.contains("STRUCTURAL_Y = 48"));
        assertTrue(menu.contains("INPUT_X = 13"));
        assertTrue(menu.contains("INPUT_Y = 74"));
        assertTrue(menu.contains("INVENTORY_X = 8"));
        assertTrue(menu.contains("INVENTORY_Y = 161"));
        assertTrue(menu.contains("HOTBAR_Y = 219"));

        String english = Files.readString(Path.of("src/main/resources/assets/ae2lt/lang/en_us.json"));
        String chinese = Files.readString(Path.of("src/main/resources/assets/ae2lt/lang/zh_cn.json"));
        assertTrue(english.contains("ae2lt.overload_device_workbench.screen.network.online"));
        assertTrue(english.contains("ME Network: online"));
        assertTrue(chinese.contains("ae2lt.overload_device_workbench.screen.network.online"));
        assertTrue(chinese.contains("ME 网络：在线"));
    }

    @Test
    void hubScreenUsesPixelSpriteLayoutWithoutEnergyPanel() throws Exception {
        Path texture = Path.of("src/main/resources/assets/ae2lt/textures/gui/armor_settings_gui.png");
        assertSprite(texture);

        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/hub/DeviceHubScreen.java"));

        assertTrue(screen.contains("\"textures/gui/armor_settings_gui.png\""));
        assertTrue(screen.contains("GUI_WIDTH = 176"));
        assertTrue(screen.contains("GUI_HEIGHT = 223"));
        assertTrue(screen.contains("TEXT_PRIMARY = 0xFF6E748C"));
        assertTrue(screen.contains("TEXT_OK = 0xFF5F8F78"));
        assertTrue(screen.contains("TOGGLE_ON = 0xFF6F927C"));
        assertTrue(screen.contains("BUTTON_TEXT = 0xFFCCD2DE"));
        assertTrue(screen.contains("renderSelectedTabTexture"));
        assertFalse(screen.contains("ae2lt.device_hub.energy"));
        assertFalse(screen.contains("getEnergyStored"));
        assertFalse(screen.contains("getEnergyCapacity"));
        assertFalse(screen.contains("formatEnergy"));
    }

    private static void assertSprite(Path texture) throws Exception {
        assertTrue(Files.exists(texture), "GUI sprite should be copied into the runtime asset tree");
        BufferedImage image = ImageIO.read(texture.toFile());
        assertNotNull(image, "GUI sprite should be readable as a PNG");
        assertEquals(256, image.getWidth());
        assertEquals(256, image.getHeight());
    }
}
