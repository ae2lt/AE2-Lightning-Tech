package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    @Test
    void tianshuUploadScreenUsesProvidedPixelAtlas() throws Exception {
        Path texture = Path.of("src/main/resources/assets/ae2lt/textures/gui/tianshu_upload_targets.png");
        assertSprite(texture, 256, 256);

        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuUploadTargetScreen.java"));
        String style = Files.readString(Path.of(
                "src/main/resources/assets/ae2/screens/tianshu_upload_targets.json"));

        assertTrue(screen.contains("\"textures/gui/tianshu_upload_targets.png\""));
        assertTrue(screen.contains("TEXTURE_SIZE = 256"));
        assertTrue(screen.contains("GUI_WIDTH = 190"));
        assertTrue(screen.contains("GUI_HEADER_HEIGHT = 33"));
        assertTrue(screen.contains("GUI_FOOTER_HEIGHT = 18"));
        assertTrue(screen.contains("ROW_HEIGHT = 17"));
        assertTrue(screen.contains("ROW_TEXTURE_X = 9"));
        assertTrue(screen.contains("ROW_TEXTURE_WIDTH = 158"));
        assertTrue(screen.contains("ROW_NORMAL_TEXTURE_Y = 222"));
        assertTrue(screen.contains("ROW_SELECTED_TEXTURE_Y = 239"));
        assertTrue(screen.contains("Scrollbar.SMALL"));
        assertTrue(screen.contains("visibleRows * ROW_HEIGHT - 1"));
        assertTrue(screen.contains("HIDDEN_SLOT_POS = -10_000"));
        assertTrue(screen.contains("protected void updateBeforeRender()"));
        assertTrue(screen.contains("hideAllMenuSlots();"));
        assertTrue(screen.contains("slot.x = HIDDEN_SLOT_POS"));
        assertTrue(screen.contains("slot.y = HIDDEN_SLOT_POS"));
        assertFalse(screen.contains("public void renderSlot(GuiGraphics graphics, Slot slot)"));
        assertFalse(screen.contains("hideParentSlots();"));
        assertFalse(screen.contains("graphics.fill(offsetX"));

        assertTrue(style.contains("\"scrollbar\": { \"left\": 175, \"top\": 33 }"));
        assertTrue(style.contains("\"field_search\": { \"left\": 7, \"top\": 16, \"width\": 81, \"height\": 13 }"));
        assertTrue(style.contains("\"field_alias\": { \"left\": 90, \"top\": 16, \"width\": 79, \"height\": 13 }"));
    }

    @Test
    void tianshuPatternConfigScreensShareProvidedPixelAtlas() throws Exception {
        Path texture = Path.of("src/main/resources/assets/ae2lt/textures/gui/tianshu_pattern_config.png");
        assertSprite(texture, 256, 256);

        String advanced = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuAdvancedPatternConfigScreen.java"));
        String overload = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuOverloadPatternConfigScreen.java"));
        String layout = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuPatternConfigLayout.java"));
        String advancedStyle = Files.readString(Path.of(
                "src/main/resources/assets/ae2/screens/tianshu_advanced_pattern_config.json"));
        String overloadStyle = Files.readString(Path.of(
                "src/main/resources/assets/ae2/screens/tianshu_overload_pattern_config.json"));

        assertTrue(layout.contains("\"textures/gui/tianshu_pattern_config.png\""));
        assertTrue(layout.contains("TEXTURE_SIZE = 256"));
        assertTrue(layout.contains("GUI_WIDTH = 190"));
        assertTrue(layout.contains("HEADER_HEIGHT = 32"));
        assertTrue(layout.contains("ROW_HEIGHT = 25"));
        assertTrue(layout.contains("FOOTER_HEIGHT = 30"));
        assertTrue(layout.contains("ROW_TEXTURE_X = 9"));
        assertTrue(layout.contains("ROW_TEXTURE_Y = 190"));
        assertTrue(layout.contains("ROW_TEXTURE_WIDTH = 158"));
        assertTrue(layout.contains("ROW_CONTENT_X_OFFSET = 4"));
        assertTrue(layout.contains("SCROLLBAR_HEIGHT = 129"));

        for (String screen : List.of(advanced, overload)) {
            assertTrue(screen.contains("TianshuPatternConfigLayout.drawBackground"));
            assertFalse(screen.contains("graphics.fill(offsetX"));
        }

        for (String style : List.of(advancedStyle, overloadStyle)) {
            assertTrue(style.contains("\"scrollbar\": { \"left\": 175, \"top\": 30 }"));
            assertTrue(style.contains("\"button_back\": { \"left\": 166"));
            assertTrue(style.contains("\"save\": { \"left\": 121, \"top\": 165"));
        }
    }

    @Test
    void tianshuInventoryScreensUseAe2WidgetsAndReplaceableSolidAtlas() throws Exception {
        Path texture = Path.of("src/main/resources/assets/ae2lt/textures/guis/tianshu_inventory.png");
        assertSprite(texture, 256, 256);

        BufferedImage image = ImageIO.read(texture.toFile());
        int backgroundColor = image.getRGB(0, 0);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                assertEquals(backgroundColor, image.getRGB(x, y),
                        "temporary inventory GUI background must remain a replaceable solid color");
            }
        }

        String terminal = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuPatternEncodingTermScreen.java"));
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String overview = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuGlobalReserveScreen.java"));
        String rule = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuMaintenanceRuleScreen.java"));
        String intro = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuMaintenanceIntroScreen.java"));

        assertTrue(terminal.contains("new MaintenanceOverviewButton()"));
        assertTrue(terminal.contains("syncSyntheticMaintenanceEntries()"));
        assertTrue(terminal.contains("new TianshuMaintenanceIntroScreen<>"));
        assertTrue(menu.contains("lastMaintenanceSummaryTick != Integer.MIN_VALUE"));

        assertTrue(overview.contains("widgets.addTextField(\"search\")"));
        assertTrue(overview.contains("widgets.addScrollBar(\"scrollbar\", Scrollbar.SMALL)"));
        assertTrue(overview.contains("widgets.addButton("));
        assertTrue(overview.contains("new TabButton("));
        assertTrue(rule.contains("widgets.addTextField("));
        assertTrue(rule.contains("widgets.addCheckbox("));
        assertTrue(rule.contains("widgets.addScrollBar(\"scrollbar\", Scrollbar.SMALL)"));
        assertTrue(rule.contains("widgets.addButton("));
        assertTrue(intro.contains("widgets.addCheckbox("));
        assertTrue(intro.contains("widgets.addButton("));

        for (String screen : List.of(overview, rule, intro)) {
            assertFalse(screen.contains("Button.builder("));
            assertFalse(screen.contains("new EditBox("));
        }

        for (String styleName : List.of(
                "tianshu_inventory_overview.json",
                "tianshu_maintenance_rule.json",
                "tianshu_reserve_edit.json",
                "tianshu_maintenance_intro.json")) {
            String style = Files.readString(Path.of(
                    "src/main/resources/assets/ae2/screens/" + styleName));
            assertTrue(style.contains("ae2lt:textures/guis/tianshu_inventory.png"));
            assertTrue(style.contains("\"textureWidth\": 256"));
            assertTrue(style.contains("\"textureHeight\": 256"));
            assertTrue(style.contains("\"srcRect\": [0, 0, 228,"));
        }
    }

    private static void assertSprite(Path texture, int expectedWidth, int expectedHeight) throws Exception {
        assertTrue(Files.exists(texture), "GUI sprite should be copied into the runtime asset tree");
        BufferedImage image = ImageIO.read(texture.toFile());
        assertNotNull(image, "GUI sprite should be readable as a PNG");
        assertEquals(expectedWidth, image.getWidth());
        assertEquals(expectedHeight, image.getHeight());
    }
}
