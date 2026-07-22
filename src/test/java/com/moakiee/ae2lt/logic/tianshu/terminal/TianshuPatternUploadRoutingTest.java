package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TianshuPatternUploadRoutingTest {
    @Test
    void nullModeIsInvalid() {
        assertEquals(TianshuPatternUploadRouting.Route.INVALID,
                TianshuPatternUploadRouting.forEncodingMode(null));
    }

    @Test
    void contextlessResultsAreNeverAcknowledged() {
        assertFalse(TianshuPatternUploadRouting.isValidEncodingResult(null, null));
    }

    @Test
    void schedulerOnlyShowsPickerForProcessingFamily() {
        assertEquals(TianshuPatternUploadRouting.Route.CRAFTING_ASSEMBLER,
                TianshuPatternUploadRouting.forEncodingMode(TianshuEncodingMode.CRAFTING));
        assertEquals(TianshuPatternUploadRouting.Route.CRAFTING_ASSEMBLER,
                TianshuPatternUploadRouting.forEncodingMode(TianshuEncodingMode.STONECUTTING));
        assertEquals(TianshuPatternUploadRouting.Route.CRAFTING_ASSEMBLER,
                TianshuPatternUploadRouting.forEncodingMode(TianshuEncodingMode.SMITHING_TABLE));
        assertEquals(TianshuPatternUploadRouting.Route.PROCESSING_PROVIDER,
                TianshuPatternUploadRouting.forEncodingMode(TianshuEncodingMode.PROCESSING));
        assertEquals(TianshuPatternUploadRouting.Route.CLOSED_LOOP_STORAGE,
                TianshuPatternUploadRouting.forEncodingMode(TianshuEncodingMode.CLOSED_LOOP));
    }

    @Test
    void onlyTheBuiltInMatterWarpingMatrixGetsCraftingUploadPriority() {
        assertTrue(TianshuPatternUploadRouting.isMatterWarpingMatrixId(
                ResourceLocation.fromNamespaceAndPath(
                        "ae2lt", "matter_warping_matrix_port")));
        assertFalse(TianshuPatternUploadRouting.isMatterWarpingMatrixId(
                ResourceLocation.fromNamespaceAndPath(
                        "other", "matter_warping_matrix_port")));
        assertFalse(TianshuPatternUploadRouting.isMatterWarpingMatrixId(
                ResourceLocation.fromNamespaceAndPath(
                        "ae2lt", "matter_warping_matrix_controller")));
        assertFalse(TianshuPatternUploadRouting.isMatterWarpingMatrixId(null));
    }

    @Test
    void craftingUploadTriesTheMatrixBeforeRequestingAGroupSelection() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        int methodStart = menu.indexOf("private void uploadCraftingPatternServer");
        int matrixPass = menu.indexOf(
                "uploadCraftingPatternToMatrix(player, stack)", methodStart);
        int groupSelection = menu.indexOf("craftingUploadTargetRequest++", methodStart);

        assertTrue(methodStart >= 0);
        assertTrue(matrixPass >= 0);
        assertTrue(groupSelection > matrixPass);
        assertFalse(menu.contains(
                "uploadCraftingPatternToFirstTarget(player, stack, false)"));
    }

    @Test
    void craftingGroupSelectionIsValidatedOnTheServer() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuPatternEncodingTermScreen.java"));

        assertTrue(menu.contains(
                "route != TianshuPatternUploadRouting.Route.CRAFTING_ASSEMBLER"));
        assertTrue(menu.contains("group.equals(target.getTerminalGroup())"));
        assertTrue(screen.contains(
                "observedCraftingUploadTargetRequest != menu.craftingUploadTargetRequest"));
        assertTrue(screen.contains("new TianshuUploadTargetScreen<>(this)"));
    }
}
