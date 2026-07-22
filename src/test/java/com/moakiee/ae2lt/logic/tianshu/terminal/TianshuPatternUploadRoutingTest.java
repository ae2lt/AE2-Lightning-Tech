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
    void craftingUploadsUseTheExactSupportedGroupIds() {
        assertCraftingGroup("ae2", "molecular_assembler");
        assertCraftingGroup("extendedae", "ex_molecular_assembler");
        assertCraftingGroup("extendedae", "assembler_matrix_pattern");
        assertCraftingGroup("extendedae_plus", "assembler_matrix_pattern_plus");
        assertCraftingGroup("neoecoae", "crafting_system_l4");
        assertCraftingGroup("neoecoae", "crafting_system_l6");
        assertCraftingGroup("neoecoae", "crafting_system_l9");
        assertCraftingGroup("ae2lt", "matter_warping_matrix_port");

        assertFalse(TianshuPatternUploadRouting.isCraftingUploadGroupId(
                ResourceLocation.fromNamespaceAndPath("ae2", "pattern_provider")));
        assertFalse(TianshuPatternUploadRouting.isCraftingUploadGroupId(
                ResourceLocation.fromNamespaceAndPath("neoecoae", "crafting_pattern_bus")));
        assertFalse(TianshuPatternUploadRouting.isCraftingUploadGroupId(null));
    }

    @Test
    void craftingUploadTriesTheMatrixBeforeOtherWhitelistedGroups() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        int methodStart = menu.indexOf("private void uploadCraftingPatternServer");
        int matrixPass = menu.indexOf(
                "uploadCraftingPatternToFirstTarget(player, stack, true)", methodStart);
        int compatiblePass = menu.indexOf(
                "uploadCraftingPatternToFirstTarget(player, stack, false)", methodStart);

        assertTrue(methodStart >= 0);
        assertTrue(matrixPass >= 0);
        assertTrue(compatiblePass > matrixPass);
    }

    @Test
    void automaticCraftingUploadFiltersEveryTargetByGroup() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuPatternEncodingTermScreen.java"));

        assertTrue(menu.contains("isCraftingUploadGroup(group)"));
        assertTrue(menu.contains("isMatterWarpingMatrixGroup(group) != matrixTarget"));
        assertFalse(screen.contains("craftingUploadTargetRequest"));
    }

    private static void assertCraftingGroup(String namespace, String path) {
        assertTrue(TianshuPatternUploadRouting.isCraftingUploadGroupId(
                ResourceLocation.fromNamespaceAndPath(namespace, path)));
    }
}
