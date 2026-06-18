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
        assertSprite(texture, 320, 256);

        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/OverloadDeviceWorkbenchScreen.java"));
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/OverloadDeviceWorkbenchMenu.java"));

        assertTrue(screen.contains("\"textures/gui/overload_workplace_gui.png\""));
        assertTrue(screen.contains("TEXTURE_WIDTH = 320"));
        assertTrue(screen.contains("TEXTURE_HEIGHT = 256"));
        assertTrue(screen.contains("GUI_WIDTH = 176"));
        assertTrue(screen.contains("GUI_HEIGHT = 245"));
        assertTrue(screen.contains("TEXT_ON_LIGHT_BG = 0xFF6E748C"));
        assertTrue(screen.contains("TEXT_ON_DARK_BG = 0xFFFFFFFF"));
        assertTrue(screen.contains("STATUS_X = 44"));
        assertTrue(screen.contains("STATUS_Y = 22"));
        assertTrue(screen.contains("MODULE_HEADER_Y = 49"));
        assertTrue(screen.contains("MODULE_ROW_X = 42"));
        assertTrue(screen.contains("MODULE_ROW_Y = 60"));
        assertTrue(screen.contains("MODULE_ROW_WIDTH = 118"));
        assertTrue(screen.contains("MODULE_ROW_HEIGHT = 17"));
        assertTrue(screen.contains("MODULE_ICON_X = 44"));
        assertTrue(screen.contains("MODULE_NAME_X = 67"));
        assertTrue(screen.contains("VISIBLE_ROWS = 5"));
        assertTrue(screen.contains("REMOVE_BUTTON_X = 148"));
        assertTrue(screen.contains("SCROLLBAR_X = 164"));
        assertTrue(screen.contains("SCROLLBAR_Y = 61"));
        assertTrue(screen.contains("SCROLLBAR_THUMB_SRC_X = 180"));
        assertTrue(screen.contains("SCROLLBAR_THUMB_SRC_Y = 0"));
        assertTrue(screen.contains("SCROLLBAR_THUMB_HOVER_SRC_Y = 17"));
        assertTrue(screen.contains("MODULE_ROW_SRC_X = 180"));
        assertTrue(screen.contains("MODULE_ROW_SRC_Y = 90"));
        assertTrue(screen.contains("MODULE_ROW_SELECTED_SRC_Y = 111"));
        assertTrue(screen.contains("REMOVE_BUTTON_SRC_X = 191"));
        assertTrue(screen.contains("REMOVE_BUTTON_SRC_Y = 5"));
        assertTrue(screen.contains("REMOVE_BUTTON_HOVER_SRC_X = 202"));
        assertTrue(screen.contains("REMOVE_BUTTON_HOVER_SRC_Y = 6"));
        assertTrue(screen.contains("ARROW_PROGRESS_SRC_X = 180"));
        assertTrue(screen.contains("ARROW_PROGRESS_SRC_Y = 48"));
        assertTrue(screen.contains("ARROW_PROGRESS_WIDTH = 28"));
        assertTrue(screen.contains("ARROW_PROGRESS_HEIGHT = 38"));
        assertTrue(screen.contains("ARROW_PROGRESS_X = 8"));
        assertTrue(screen.contains("ARROW_PROGRESS_Y = 97"));
        assertFalse(screen.contains("PROGRESS_SRC_Y = 35"));
        assertFalse(screen.contains("PROGRESS_SRC_WIDTH = 72"));
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
        assertSprite(texture, 256, 256);

        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/hub/DeviceHubScreen.java"));

        assertTrue(screen.contains("\"textures/gui/armor_settings_gui.png\""));
        assertTrue(screen.contains("GUI_WIDTH = 176"));
        assertTrue(screen.contains("GUI_HEIGHT = 223"));
        assertTrue(screen.contains("TEXT_ON_LIGHT_BG = 0xFF6E748C"));
        assertTrue(screen.contains("TEXT_ON_DARK_BG = 0xFFFFFFFF"));
        assertTrue(screen.contains("BUTTON_TEXT = TEXT_ON_DARK_BG"));
        assertTrue(screen.contains("renderSelectedTabTexture"));
        assertTrue(screen.contains("renderTabIcons"));
        assertTrue(screen.contains("tabDisplayStack"));
        assertTrue(screen.contains("defaultTabStack"));
        assertTrue(screen.contains("selectedDeviceStack"));
        assertTrue(screen.contains("\"ae2\", \"textures/guis/checkbox.png\""));
        assertTrue(screen.contains("CHECKBOX_WIDTH = 22"));
        assertTrue(screen.contains("CHECKBOX_HEIGHT = 12"));
        assertTrue(screen.contains("CHECKBOX_OFF_SRC_Y = 28"));
        assertTrue(screen.contains("CHECKBOX_ON_SRC_Y = 40"));
        assertTrue(screen.contains("CONFIG_HEADER_Y = 144"));
        assertTrue(screen.contains("CONFIG_Y = 160"));
        assertTrue(screen.contains("SCROLL_HOVER_SRC_Y = 17"));
        assertFalse(screen.contains("renderTabLabels"));
        assertFalse(screen.contains("ae2lt.device_hub.access_point."));
        assertFalse(screen.contains("ae2lt.device_hub.ap."));
        assertFalse(screen.contains("ae2lt.device_hub.appflux"));
        assertFalse(screen.contains("ae2lt.device_hub.workbench_hint"));
        assertFalse(screen.contains("moduleStateLine"));
        assertFalse(screen.contains("ae2lt.device_hub.energy"));
        assertFalse(screen.contains("getEnergyStored"));
        assertFalse(screen.contains("getEnergyCapacity"));
        assertFalse(screen.contains("formatEnergy"));

        String english = Files.readString(Path.of("src/main/resources/assets/ae2lt/lang/en_us.json"));
        String chinese = Files.readString(Path.of("src/main/resources/assets/ae2lt/lang/zh_cn.json"));
        assertTrue(english.contains("\"ae2lt.device_hub.modules\": \"Module List\""));
        assertTrue(chinese.contains("\"ae2lt.device_hub.modules\": \"模块列表\""));
        assertFalse(english.contains("ae2lt.device_hub.access_point."));
        assertFalse(chinese.contains("ae2lt.device_hub.access_point."));
        assertFalse(english.contains("ae2lt.device_hub.appflux"));
        assertFalse(chinese.contains("ae2lt.device_hub.workbench_hint"));
        assertFalse(chinese.contains("ae2lt.device_hub.module.state.active"));
    }

    private static void assertSprite(Path texture, int expectedWidth, int expectedHeight) throws Exception {
        assertTrue(Files.exists(texture), "GUI sprite should be copied into the runtime asset tree");
        BufferedImage image = ImageIO.read(texture.toFile());
        assertNotNull(image, "GUI sprite should be readable as a PNG");
        assertEquals(expectedWidth, image.getWidth());
        assertEquals(expectedHeight, image.getHeight());
    }
}
