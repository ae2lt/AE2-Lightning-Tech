package com.moakiee.ae2lt.client;

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
        assertTrue(config.contains("EmiEncodePatternTransferMixin"));
        assertTrue(plugin.contains("getModFileById(\"ae2jeiintegration\")"));
        assertTrue(plugin.contains("getModFileById(\"emi\")"));
        assertTrue(metadata.contains("${mod_id}.recipeviewer.mixins.json"));
        assertTrue(metadata.contains("modId = \"emi\""));
    }

    @Test
    void bothViewersObserveOnlyActualTransfersAndCaptureStableRecipeIdentity() throws Exception {
        String jei = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/recipeviewer/jei/JeiEncodePatternTransferMixin.java"));
        String emi = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/recipeviewer/emi/EmiEncodePatternTransferMixin.java"));
        String context = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuRecipeTransferContext.java"));

        assertTrue(jei.contains("!doTransfer"));
        assertTrue(jei.contains("captureVanillaRecipe"));
        assertTrue(emi.contains("!doTransfer"));
        assertTrue(emi.contains("category.getId().toString()"));
        assertTrue(emi.contains("emiRecipe.getId().toString()"));
        assertTrue(emi.contains("getWorkstations"));
        assertTrue(emi.contains("addDefaultAlias"));
        assertTrue(context.contains("BuiltInRegistries.RECIPE_TYPE"));
        assertTrue(context.contains("WeakReference<TianshuPatternEncodingTermMenu>"));
        assertTrue(context.contains("String recipeId"));
        org.junit.jupiter.api.Assertions.assertFalse(context.contains("Map<Integer, Component>"));
    }

    @Test
    void transferredRecipesOnlyFeedTheProviderPicker() throws Exception {
        String picker = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuUploadTargetScreen.java"));
        String context = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuRecipeTransferContext.java"));

        assertTrue(picker.contains("TianshuRecipeTransferContext.snapshotFor(menu)"));
        assertTrue(picker.contains("recipeContext.sourceKey()"));
        assertTrue(context.contains("does not start encoding"));
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
        assertTrue(picker.contains("public boolean mouseScrolled"));
        assertTrue(picker.contains("aliasField.setValue(\"\")"));
        org.junit.jupiter.api.Assertions.assertFalse(picker.contains("sourceField.setResponder"));
        org.junit.jupiter.api.Assertions.assertFalse(picker.contains("recipeContext.queries()"));
        org.junit.jupiter.api.Assertions.assertFalse(picker.contains("selectedQueryIndex"));
        org.junit.jupiter.api.Assertions.assertFalse(picker.contains("selectedQuery()"));
        org.junit.jupiter.api.Assertions.assertFalse(picker.contains("rebuildCandidateTooltip"));
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
