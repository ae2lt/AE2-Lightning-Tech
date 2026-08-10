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
        assertCraftingGroup("expatternprovider", "ex_molecular_assembler");
        assertCraftingGroup("expatternprovider", "assembler_matrix_pattern");
        assertCraftingGroup("extendedae_plus", "assembler_matrix_pattern_plus");
        assertCraftingGroup("neoecoae", "crafting_system_l4");
        assertCraftingGroup("neoecoae", "crafting_system_l6");
        assertCraftingGroup("neoecoae", "crafting_system_l9");
        assertCraftingGroup("ae2cs", "meteorite_pattern_provider");
        assertCraftingGroup("ae2lt", "matter_warping_matrix_controller");

        assertMatrixGroup("matter_warping_matrix_controller");

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

    @Test
    void processingUploadsKeepThePickerAndMatrixRejectsProcessingPatterns() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuPatternEncodingTermScreen.java"));
        String matrixStorage = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/MatrixPatternStorageBlockEntity.java"));

        assertTrue(screen.contains(
                "new TianshuUploadTargetScreen<>(this, directUploadRequested)"));
        assertTrue(menu.contains(
                "!= TianshuPatternUploadRouting.Route.PROCESSING_PROVIDER"));
        assertTrue(matrixStorage.contains(
                "return details instanceof IMolecularAssemblerSupportedPattern"));
    }

    @Test
    void duplicateFilteringInterceptsEncodingAndCanBeDisabledByTheClient() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String duplicateFilter = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/tianshu/terminal/"
                        + "PatternEncodingDuplicateFilter.java"));
        String clientConfig = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/config/AE2LTClientConfig.java"));
        String settingsScreen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuTerminalSettingsScreen.java"));

        assertTrue(menu.contains(
                "registerClientAction(\"encodeTianshu\", Boolean.class, "
                        + "this::encodeServerWithOptions)"));
        assertTrue(menu.contains("sendClientAction(\"encodeTianshu\","));
        assertTrue(menu.contains("previewAe2EncodingCandidate()"));
        assertTrue(menu.contains(
                "shouldInterceptDuplicateEncoding(candidate, interceptDuplicates)"));
        assertTrue(menu.contains("commitAe2EncodingCandidate(candidate)"));
        assertTrue(menu.contains(
                "route == TianshuPatternUploadRouting.Route.INVALID"));
        assertTrue(clientConfig.contains(
                ".define(\"interceptDuplicatePatternEncoding\", true)"));
        assertTrue(settingsScreen.contains("toggleDuplicateEncoding"));
        assertTrue(duplicateFilter.contains("ItemStack.isSameItemSameTags(stored, candidate)"));
        assertTrue(duplicateFilter.contains("readClosedLoopPayload(candidate, level)"));
        assertTrue(duplicateFilter.contains("sameClosedLoopPayload("));
        assertTrue(duplicateFilter.contains("left.pattern().fingerprint()"));
        assertTrue(duplicateFilter.contains(
                "Objects.equals(stored.getDefinition(), candidate.getDefinition())"));

        int processingUpload = menu.indexOf(
                "public void uploadTianshuPatternToTarget(ServerPlayer player");
        int uploadWriter = menu.indexOf("private void uploadToProvider", processingUpload);
        assertFalse(menu.substring(processingUpload, uploadWriter)
                .contains("containsUploadedPattern"));
    }

    @Test
    void closedLoopIdentityIsNotPersistedInThePayloadOrRepository() throws Exception {
        String payload = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/tianshu/loop/"
                        + "ClosedLoopPatternPayload.java"));
        String codec = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/tianshu/loop/"
                        + "ClosedLoopPatternPayloadTagCodec.java"));
        String identity = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/tianshu/loop/"
                        + "ClosedLoopPatternIdentity.java"));

        assertFalse(payload.contains("UUID patternId"));
        assertFalse(payload.contains("long version"));
        assertFalse(codec.contains("putUUID("));
        assertFalse(codec.contains("TAG_VERSION"));
        assertTrue(identity.contains("SourcePatternSnapshot"));
        assertTrue(identity.contains("UUID.nameUUIDFromBytes"));
    }

    private static void assertCraftingGroup(String namespace, String path) {
        assertTrue(TianshuPatternUploadRouting.isCraftingUploadGroupId(
                ResourceLocation.fromNamespaceAndPath(namespace, path)));
    }

    private static void assertMatrixGroup(String path) {
        assertTrue(TianshuPatternUploadRouting.isMatterWarpingMatrixId(
                ResourceLocation.fromNamespaceAndPath("ae2lt", path)));
    }
}
