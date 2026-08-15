package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TianshuJeiDirectUploadSourceContractTest {
    @Test
    void jeiCloseInterceptionUsesTheArmedDirectUploadRequest() throws Exception {
        String mixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/recipeviewer/jei/"
                        + "JeiRecipeTransferButtonControllerMixin.java"));

        assertTrue(mixin.contains(
                "TianshuDirectUploadClient.holdRecipeScreen(tianshuMenu, recipeScreen)"));
        assertFalse(mixin.contains("Screen.hasAltDown()"),
                "JEI close interception must not resample the Alt key after transfer");
    }

    @Test
    void ambiguousTargetsReplaceJeiWithThePickerInTheSameTick() throws Exception {
        String coordinator = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuDirectUploadClient.java"));
        String terminalScreen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuPatternEncodingTermScreen.java"));

        int closeRecipeViewer = coordinator.indexOf("recipeScreen.onClose()");
        int openPicker = coordinator.indexOf("terminalScreen.openDirectUploadFallback()");
        assertTrue(closeRecipeViewer >= 0 && openPicker > closeRecipeViewer,
                "ambiguous JEI uploads must replace the restored parent with the picker immediately");
        assertTrue(terminalScreen.contains(
                "switchToScreen(new TianshuUploadTargetScreen<>(this, true))"));
    }
}
