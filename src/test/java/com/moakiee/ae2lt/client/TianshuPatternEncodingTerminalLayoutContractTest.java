package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class TianshuPatternEncodingTerminalLayoutContractTest {
    private static final Path CLIENT_ROOT =
            Path.of("src/main/java/com/moakiee/ae2lt/client");
    private static final Path SCREEN_ROOT =
            Path.of("src/main/resources/assets/ae2/screens/terminals");

    @Test
    void nativeModesUseTheAe2Forge120PanelGeometry() throws Exception {
        String basePanel = Files.readString(CLIENT_ROOT.resolve("TianshuEncodingModePanel.java"));
        assertTrue(basePanel.contains("new Rect2i(x, y, 126, 68)"));

        for (String panelName : List.of(
                "TianshuCraftingEncodingPanel.java",
                "TianshuProcessingEncodingPanel.java",
                "TianshuSmithingTableEncodingPanel.java")) {
            String panel = Files.readString(CLIENT_ROOT.resolve(panelName));
            assertTrue(panel.contains("126, 68"), panelName);
            assertTrue(panel.contains("bounds.getX() + 9"), panelName);
            assertTrue(panel.contains("bounds.getHeight() - 164"), panelName);
            assertFalse(panel.contains("setDisableBackground(true)"), panelName);
        }

        String stonecutting = Files.readString(
                CLIENT_ROOT.resolve("TianshuStonecuttingEncodingPanel.java"));
        assertTrue(stonecutting.contains("src(0, 141, 126, 68)"));
        assertTrue(stonecutting.contains("src(126, 141, 16, 18)"));
        assertTrue(stonecutting.contains("private static final int ROWS = 3"));
        assertTrue(stonecutting.contains("x + 44"));
        assertTrue(stonecutting.contains("y + 8"));
    }

    @Test
    void nativeModeTabsKeepAe2IconsAndAnUnobstructedTwentyPixelRail() throws Exception {
        String screen = Files.readString(
                CLIENT_ROOT.resolve("TianshuPatternEncodingTermScreen.java"));
        String style = Files.readString(
                SCREEN_ROOT.resolve("tianshu_pattern_encoding_terminal.json"));

        assertTrue(screen.contains("panel.getTabIconItem()"));
        assertTrue(Files.readString(CLIENT_ROOT.resolve("TianshuCraftingEncodingPanel.java"))
                .contains("Items.CRAFTING_TABLE.getDefaultInstance()"));
        assertTrue(Files.readString(CLIENT_ROOT.resolve("TianshuProcessingEncodingPanel.java"))
                .contains("Items.FURNACE.getDefaultInstance()"));
        assertTrue(Files.readString(CLIENT_ROOT.resolve("TianshuSmithingTableEncodingPanel.java"))
                .contains("Items.SMITHING_TABLE.getDefaultInstance()"));
        assertTrue(Files.readString(CLIENT_ROOT.resolve("TianshuStonecuttingEncodingPanel.java"))
                .contains("Items.STONECUTTER.getDefaultInstance()"));

        assertFalse(style.contains("\"modeTabButton2\""));
        assertTrue(style.contains(
                "\"modeTabButton3\": { \"left\": 173, \"bottom\": 110, \"width\": 20, \"height\": 20 }"));
        assertTrue(style.contains(
                "\"modeTabButton4\": { \"left\": 173, \"bottom\": 90, \"width\": 20, \"height\": 22 }"));
    }

    @Test
    void processingExtensionsStayInTheGapBetweenInputsAndOutputs() throws Exception {
        String style = Files.readString(
                SCREEN_ROOT.resolve("tianshu_pattern_encoding_terminal.json"));

        assertFalse(style.contains("\"processingClearPattern\""));
        assertFalse(style.contains("\"processingCycleOutput\""));
        for (String id : List.of(
                "processingMultiply2",
                "processingMultiply5",
                "processingDivide2",
                "processingDivide5",
                "advancedEncodingButton",
                "overloadEncodingButton")) {
            assertTrue(style.contains("\"" + id + "\""), id);
        }

        // AE2's processing inputs end at x=78 and its outputs begin at x=110.
        // These two 12-pixel columns occupy x=81..106, leaving both slot groups clear.
        assertTrue(style.contains("\"left\": 81"));
        assertTrue(style.contains("\"left\": 94"));
        assertTrue(style.contains("\"width\": 12"));
    }

    @Test
    void closedLoopScrollbarUsesTheAe2ProcessingGridBaseline() throws Exception {
        String style = Files.readString(
                SCREEN_ROOT.resolve("tianshu_pattern_encoding_terminal.json"));

        assertTrue(style.contains(
                "\"closedLoopScrollbar\": { \"left\": 16, \"bottom\": 157, \"height\": 52 }"));
        assertFalse(style.contains(
                "\"closedLoopScrollbar\": { \"left\": 17, \"bottom\": 156"));
    }

    @Test
    void closedLoopSlotsAlignWithTheirBackgroundFrames() throws Exception {
        String panel = Files.readString(
                CLIENT_ROOT.resolve("TianshuClosedLoopEncodingPanel.java"));

        assertTrue(panel.contains("private static final int MEMBER_X = 16"));
        assertTrue(panel.contains("private static final int SLOT_Y = 9"));
        assertTrue(panel.contains("private static final int OUTPUT_X = 100"));
    }
}
