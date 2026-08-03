package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TianshuRecipeViewerIntegrationSourceContractTest {
    @Test
    void optionalJeiAndEmiMixinsAreGatedBeforeExternalTypesLoad() throws Exception {
        String config = Files.readString(Path.of(
                "src/main/resources/ae2lt.recipeviewer.mixins.json"));
        String plugin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/recipeviewer/RecipeViewerMixinPlugin.java"));
        String metadata = Files.readString(Path.of(
                "src/main/templates/META-INF/neoforge.mods.toml"));

        assertTrue(config.contains("\"required\": false"));
        assertTrue(config.contains("RecipeViewerMixinPlugin"));
        assertTrue(config.contains("JeiEncodePatternTransferMixin"));
        assertTrue(config.contains("JeiRecipeTransferButtonControllerMixin"));
        assertTrue(config.contains("EmiEncodePatternTransferMixin"));
        assertTrue(config.contains("EmiRecipeTransferResultAccessor"));
        assertTrue(plugin.contains("getModFileById(\"ae2jeiintegration\")"));
        assertTrue(plugin.contains("getModFileById(\"emi\")"));
        assertTrue(metadata.contains("${mod_id}.recipeviewer.mixins.json"));
        assertTrue(metadata.contains("modId = \"emi\""));
    }

    @Test
    void emiRegistersRecipeHandlersForBothTianshuTerminalMenuTypes() throws Exception {
        String emiPlugin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/integration/emi/AE2LTEmiPlugin.java"));

        assertTrue(emiPlugin.contains("TianshuPatternEncodingTermMenu.TYPE"));
        assertTrue(emiPlugin.contains(
                "new EmiEncodePatternHandler<>(TianshuPatternEncodingTermMenu.class)"));
        assertTrue(emiPlugin.contains("TianshuWirelessPatternEncodingTermMenu.TYPE"));
        assertTrue(emiPlugin.contains(
                "new EmiEncodePatternHandler<>(TianshuWirelessPatternEncodingTermMenu.class)"));
    }

    @Test
    void bothViewersObserveOnlyActualTransfersAndCaptureStableRecipeIdentity() throws Exception {
        String jei = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/recipeviewer/jei/JeiEncodePatternTransferMixin.java"));
        String emi = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/recipeviewer/emi/EmiEncodePatternTransferMixin.java"));
        String context = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuRecipeTransferContext.java"));
        String transferButton = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/recipeviewer/jei/"
                        + "JeiRecipeTransferButtonControllerMixin.java"));
        String jeiMetadata = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/"
                        + "JeiRecipeTransferMetadata.java"));

        assertTrue(jei.contains("!doTransfer"));
        assertTrue(jei.contains("resetProcessingEncoding()"));
        assertTrue(jei.contains("TianshuRecipeTransferContext.clear"));
        assertTrue(jei.contains("captureVanillaRecipe"));
        assertTrue(jei.contains("fallback.sourceKey()"));
        assertFalse(jei.contains("if (ModList.get().isLoaded(\"emi\")) return;"));
        assertFalse(jei.contains(
                "!TianshuRecipeTransferContext.isSupportedCraftingRecipe(recipeBase)"));
        assertTrue(emi.contains("!doTransfer"));
        assertTrue(emi.contains("resetProcessingEncoding()"));
        assertTrue(emi.contains("TianshuRecipeTransferContext.clear"));
        assertTrue(emi.contains("category.getId().toString()"));
        assertTrue(emi.contains("emiRecipe.getId().toString()"));
        assertTrue(emi.contains("getWorkstations"));
        assertTrue(emi.contains("addDefaultAlias"));
        assertTrue(emi.contains("captureVanillaRecipe("));
        assertTrue(emi.contains("tianshuMenu, holder, sourceKey, defaultAliases"));
        assertFalse(emi.contains(
                "isSupportedCraftingRecipe(holder)) return"));
        assertTrue(context.contains("BuiltInRegistries.RECIPE_TYPE"));
        assertTrue(context.contains("fallbackSourceKey"));
        assertTrue(context.contains("ResourceLocation.tryParse(sourceKey)"));
        assertTrue(context.contains("sourceId.getNamespace()"));
        assertTrue(context.contains("holder.id().getNamespace()"));
        assertTrue(emi.contains("emiRecipe.getId().getNamespace()"));
        assertTrue(context.contains("WeakReference<TianshuPatternEncodingTermMenu>"));
        assertTrue(context.contains("String recipeId"));
        assertTrue(context.contains("beginEncoding("));
        assertTrue(context.contains("encodingAckReceived"));
        assertTrue(context.contains("isEncodingResultReady("));
        assertTrue(context.contains("ENCODING_RESULT_GRACE_TICKS"));
        assertFalse(context.contains("boundEncodedPattern"));
        assertFalse(context.contains("retainAfterEncodedSlotChange("));
        assertFalse(context.contains("hashItemAndComponents"));
        assertFalse(context.contains("Map<Integer, Component>"));
        assertTrue(transferButton.contains("ae2lt$getRecipeLayout()"));
        assertTrue(transferButton.contains("JeiRecipeTransferMetadata.begin("));
        assertTrue(transferButton.contains("JeiRecipeTransferMetadata.clear()"));
        assertTrue(jeiMetadata.contains("getRecipeCategory()"));
        assertTrue(jeiMetadata.contains("getRecipeType()"));
        assertTrue(jeiMetadata.contains("getUid().toString()"));
        assertTrue(jeiMetadata.contains("package com.moakiee.ae2lt.client;"));
        assertFalse(jeiMetadata.contains("package com.moakiee.ae2lt.mixin."));

        int jeiClear = jei.indexOf("TianshuRecipeTransferContext.clear(tianshuMenu)");
        assertTrue(jeiClear >= 0);
        assertTrue(jeiClear < jei.indexOf("ModList.get().isLoaded(\"emi\")"));
        assertTrue(jeiClear < jei.indexOf("TianshuEncodingMode.CLOSED_LOOP"));
        int emiClear = emi.indexOf("TianshuRecipeTransferContext.clear(tianshuMenu)");
        assertTrue(emiClear >= 0);
        assertTrue(emiClear < emi.indexOf("TianshuEncodingMode.CLOSED_LOOP"));
    }

    @Test
    void nonClosedLoopTransferredRecipesFeedTheProviderPicker() throws Exception {
        String picker = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuUploadTargetScreen.java"));
        String selection = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuUploadSourceSelection.java"));
        String context = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuRecipeTransferContext.java"));

        assertTrue(picker.contains("TianshuUploadSourceSelection.collect(menu)"));
        assertTrue(selection.contains("TianshuRecipeTransferContext.snapshotFor(menu)"));
        assertTrue(selection.contains("recipeContext.sourceKey()"));
        assertTrue(selection.contains("return Selection.EMPTY;"));
        assertFalse(selection.contains("PatternDetailsHelper.decodePattern"));
        assertFalse(selection.contains("getPrimaryOutput()"));
        assertFalse(selection.contains("key.getId().toString()"));
        assertFalse(context.contains("recipe.getType().toString()"));
        assertTrue(context.contains("does not start encoding itself"));
    }

    @Test
    void altTransferEncodesOnlyAfterViewerSuccessAndDirectUploadHasASafeFallback()
            throws Exception {
        String jei = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/recipeviewer/jei/JeiEncodePatternTransferMixin.java"));
        String emi = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/recipeviewer/emi/EmiEncodePatternTransferMixin.java"));
        String picker = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuUploadTargetScreen.java"));
        String matcher = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuUploadTargetMatcher.java"));
        String coordinator = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuDirectUploadClient.java"));
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String transferButton = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/recipeviewer/jei/"
                        + "JeiRecipeTransferButtonControllerMixin.java"));

        assertTrue(jei.contains("at = @At(\"RETURN\")"));
        assertTrue(jei.contains("cir.getReturnValue() != null"));
        assertTrue(jei.contains("Screen.hasAltDown()"));
        assertTrue(jei.contains("encodeAndUploadDirectly()"));
        assertTrue(emi.contains("EmiRecipeTransferResultAccessor result"));
        assertTrue(emi.contains("result.ae2lt$canCraft()"));
        assertTrue(emi.contains("encodeAndUploadDirectly()"));
        assertTrue(picker.contains("directUploadRequested"));
        assertTrue(picker.contains("findUniqueCandidate"));
        assertTrue(picker.contains("if (selected != null) select("));
        assertTrue(matcher.contains("findUniqueCandidate"));
        assertTrue(coordinator.contains("TianshuUploadSourceSelection.collect(menu)"));
        assertTrue(coordinator.contains("selection.initialQuery()"));
        assertTrue(transferButton.contains("RecipeTransferButtonController"));
        assertTrue(transferButton.contains("TianshuDirectUploadClient.holdRecipeScreen"));
        assertTrue(transferButton.contains("recipesGui.onClose()"));
        assertTrue(coordinator.contains("hasTriggeredUploadAck()"));
        assertTrue(coordinator.contains("isEncodingResultReady(menu, stack)"));
        assertTrue(coordinator.contains("requestDirectUploadTargetsAfterEncoding()"));
        assertTrue(coordinator.contains("hasFreshDirectUploadTargets()"));
        assertTrue(coordinator.contains("recipeScreen.onClose()"));
        assertTrue(coordinator.contains("menu.uploadTianshuPatternToTarget(target.group())"));
        assertTrue(picker.contains("\"ae2lt.tianshu.upload.success_target\""));
        assertTrue(coordinator.contains("\"ae2lt.tianshu.upload.success_target\""));
        assertTrue(coordinator.contains(
                "Component.translatable(\"ae2lt.tianshu.upload.failed\")"));
        assertTrue(coordinator.contains("displayClientMessage(result, false)"));

        int resultReady = coordinator.indexOf("isEncodingResultReady(menu, stack)");
        int targetRefresh = coordinator.indexOf("requestDirectUploadTargetsAfterEncoding()");
        assertTrue(resultReady >= 0 && targetRefresh > resultReady);
        int beginEncoding = menu.indexOf("private void beginClientEncoding(");
        int encodeServer = menu.indexOf("private void encodeServerWithOptions(", beginEncoding);
        assertTrue(beginEncoding >= 0 && encodeServer > beginEncoding);
        assertFalse(menu.substring(beginEncoding, encodeServer).contains("requestUploadTargets()"));
    }

    @Test
    void closedLoopTransfersMarkThePrimaryOutputAndImmediatelyStartDiscovery() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String jei = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/recipeviewer/jei/JeiEncodePatternTransferMixin.java"));
        String emi = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/recipeviewer/emi/EmiEncodePatternTransferMixin.java"));

        assertTrue(menu.contains("markClosedLoopPrimaryOutput(ItemStack stack)"));
        assertTrue(menu.contains("closedLoopOutputSlots.getFirst()).setFilterTo(stack)"));
        assertTrue(jei.contains("RecipeIngredientRole.OUTPUT"));
        assertTrue(jei.contains("markClosedLoopPrimaryOutput(output)"));
        assertTrue(jei.contains("tianshuMenu.autoFillClosedLoop()"));
        assertTrue(jei.contains("cancellable = true"));
        assertTrue(jei.contains("cir.setReturnValue(null)"));
        assertTrue(emi.contains("EmiStackHelper.ofOutputs(emiRecipe)"));
        assertTrue(emi.contains("markClosedLoopPrimaryOutput("));
        assertTrue(emi.contains("tianshuMenu.autoFillClosedLoop()"));
        assertTrue(emi.contains("at = @At(\"RETURN\")"));
        assertTrue(emi.contains("setTianshuMode(TianshuEncodingMode.CLOSED_LOOP)"));
    }

    @Test
    void closedLoopMemberCopiesRenderAsTheirOwnSlotAmount() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuPatternEncodingTermScreen.java"));

        assertTrue(screen.contains("isClosedLoopMemberSlot(slot) && slot.hasItem()"));
        assertTrue(screen.contains("menu.closedLoopDraftSync.copies(slot.getContainerSlot())"));
        assertTrue(screen.contains("Long.toString(copies)"));
        assertTrue(screen.contains("StackSizeRenderer.renderSizeLabel("));
    }

    @Test
    void providerPickerCyclesDefaultAliasValuesWithoutASeparateFilterMode() throws Exception {
        String picker = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuUploadTargetScreen.java"));

        assertTrue(picker.contains("sourceField.setValue(sourceKey)"));
        assertTrue(picker.contains("aliasField.setValue(storedAlias)"));
        assertTrue(picker.contains("String source = sourceField.getValue().strip()"));
        assertTrue(picker.contains("String query = aliasField.getValue().strip()"));
        assertTrue(picker.contains("defaultAliases.get(defaultAliasIndex)"));
        assertTrue(picker.contains("findClosestUniqueAlias"));
        assertTrue(picker.contains("initialAliasSelectionPending"));
        assertTrue(picker.contains("storedAlias.isBlank()"));
        assertTrue(picker.contains("focusedIndex = -1;"));
        assertFalse(picker.contains("focusedIndex = filtered.isEmpty() ? -1 : 0;"));
        assertTrue(picker.contains("public boolean mouseScrolled"));
        assertTrue(picker.contains("aliasField.setValue(\"\")"));
        assertFalse(picker.contains("sourceField.setResponder"));
        assertFalse(picker.contains("recipeContext.queries()"));
        assertFalse(picker.contains("selectedQueryIndex"));
        assertFalse(picker.contains("selectedQuery()"));
        assertFalse(picker.contains("rebuildCandidateTooltip"));
    }

    @Test
    void adaptedSourceNoticeAndLicenseArePackaged() throws Exception {
        String notice = Files.readString(Path.of("THIRD_PARTY_NOTICES.md"));
        String build = Files.readString(Path.of("build.gradle"));

        assertTrue(notice.contains("ExtendedAE Plus [ClientPlus]"));
        assertTrue(notice.contains("07f8373c590c0c6d845f794e7c25090e5ef5703e"));
        assertTrue(notice.contains("GNU Lesser General Public License version 3"));
        assertTrue(build.contains("from('LICENSE')"));
        assertTrue(build.contains("from('THIRD_PARTY_NOTICES.md')"));
    }
}
