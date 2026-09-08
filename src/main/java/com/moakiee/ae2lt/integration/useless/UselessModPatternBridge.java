package com.moakiee.ae2lt.integration.useless;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternContainer;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PendingOmniversalPatternHolder;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.component.UComponents;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Direct calls to the mod's existing public methods, loaded only behind the optional boundary. */
final class UselessModPatternBridge {
    private UselessModPatternBridge() {
    }

    static long recipeGeneration() {
        return AlloyFurnaceRecipeCatalog.generation();
    }

    static ItemStack encodeViewerRecipe(Object selection, Level level) {
        if (!(selection instanceof AlloyFurnaceRecipeCatalog.Entry entry)) return ItemStack.EMPTY;
        var source = OmniversalPatternEncoding.createProcessingPattern(entry.recipe());
        return source.isEmpty() ? ItemStack.EMPTY : OmniversalPatternEncoding.encode(source, entry, level);
    }

    static boolean isViewerRecipe(Object selection) {
        return selection instanceof AlloyFurnaceRecipeCatalog.Entry;
    }

    static ItemStack encodeDraft(ItemStack pattern, Level level) {
        if (!UselessModCompat.isOmniversalPattern(pattern)) return ItemStack.EMPTY;
        var data = pattern.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        var encoded = pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (data == null || encoded == null) return ItemStack.EMPTY;
        // Rebuild a plain source solely for the native encoder. The terminal never publishes it.
        var source = PatternDetailsHelper.encodeProcessingPattern(
                encoded.sparseInputs(), encoded.sparseOutputs());
        var details = new AEProcessingPattern(Objects.requireNonNull(AEItemKey.of(source)));
        var entry = AlloyFurnaceRecipeCatalog.resolvePattern(
                        level, data.sourceId(), data.identity(), details)
                .filter(candidate -> data.sourceId().isBlank()
                        || candidate.sourceId().equals(data.sourceId()))
                .filter(candidate -> AlloyFurnaceRecipeCatalog.matchesRecipe(
                        level, data.sourceId(), candidate.recipe(), details)).orElse(null);
        if (entry == null) return ItemStack.EMPTY;
        var result = OmniversalPatternEncoding.encode(source, details, entry, level);
        return !result.isEmpty()
                && PatternDetailsHelper.decodePattern(result, level) instanceof OmniversalPatternDetails
                ? result : ItemStack.EMPTY;
    }

    static UselessModCompat.Preview preview(ItemStack pattern, Level level) {
        var encoded = pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) return UselessModCompat.Preview.EMPTY;
        var data = pattern.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        var recipe = data == null ? null
                : AlloyFurnaceRecipeCatalog.resolve(level, data.sourceId(), data.identity()).orElse(null);
        return new UselessModCompat.Preview(
                encoded.sparseInputs().stream().filter(Objects::nonNull).toList(),
                encoded.sparseOutputs().stream().filter(Objects::nonNull).toList(),
                recipe == null ? List.of() : molds(recipe.recipe()),
                data == null ? "" : data.recipeId().toString());
    }

    static UselessModCompat.EncodingResult encodeMatchingDraft(
            ItemStack pattern, @Nullable List<ItemStack> editedMolds, Level level) {
        var encoded = pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null || encoded.sparseInputs().stream().noneMatch(Objects::nonNull)
                || encoded.sparseOutputs().stream().noneMatch(Objects::nonNull)) {
            return new UselessModCompat.EncodingResult(ItemStack.EMPTY, "ae2lt.tianshu.omniversal.no_match");
        }
        var availableMolds = (editedMolds == null ? preview(pattern, level).molds() : editedMolds)
                .stream().filter(stack -> !stack.isEmpty()).toList();
        // Reuse the selected recipe only when all three editable sections still match.
        var selected = encodeDraft(pattern, level);
        if (!selected.isEmpty()
                && PatternDetailsHelper.decodePattern(selected, level) instanceof OmniversalPatternDetails nativeDetails
                && matchesMolds(nativeDetails.recipe(), availableMolds)) {
            return new UselessModCompat.EncodingResult(selected, "");
        }
        var source = PatternDetailsHelper.encodeProcessingPattern(encoded.sparseInputs(), encoded.sparseOutputs());
        var details = new AEProcessingPattern(Objects.requireNonNull(AEItemKey.of(source)));
        var match = AlloyFurnaceRecipeCatalog.findPatternCandidates(level, details).stream()
                .filter(entry -> matchesMolds(entry.recipe(), availableMolds))
                .filter(entry -> AlloyFurnaceRecipeCatalog.matchesRecipe(level, entry.sourceId(), entry.recipe(), details))
                .findFirst().orElse(null);
        if (match == null) {
            return new UselessModCompat.EncodingResult(ItemStack.EMPTY, "ae2lt.tianshu.omniversal.no_match");
        }
        var result = OmniversalPatternEncoding.encode(source, details, match, level);
        return new UselessModCompat.EncodingResult(result, "ae2lt.tianshu.omniversal.no_match");
    }

    private static boolean matchesMolds(AdvancedAlloyFurnaceRecipe recipe, List<ItemStack> supplied) {
        var requirements = recipe.molds().stream().filter(mold -> mold != null && !mold.isEmpty()).toList();
        return requirements.size() == supplied.size()
                && OmniversalMoldHubBlockEntity.matchesMolds(requirements, supplied);
    }

    private static List<ItemStack> molds(AdvancedAlloyFurnaceRecipe recipe) {
        return recipe.molds().stream().map(AdapterUtils::itemRepresentative)
                .filter(Objects::nonNull).filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    static void clearPendingRecipe(Object logic) {
        if (logic instanceof PendingOmniversalPatternHolder holder) {
            holder.uselessMod$setPendingOmniversalRecipe(null);
            holder.uselessMod$setPendingOmniversalSourceId(null);
        }
    }

    @Nullable
    static UselessModCompat.TargetState targetState(
            PatternContainer target, ItemStack pattern, Level level) {
        CraftingTaskContext machine;
        boolean single;
        if (target instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
            machine = furnace;
            single = true;
        } else if (target instanceof MePatternAssemblyBlockEntity assembly) {
            var controller = assembly.getController();
            if (controller == null || !controller.isFormed()) {
                return new UselessModCompat.TargetState(false, false, true,
                        "ae2lt.tianshu.omniversal.upload.unformed");
            }
            machine = controller;
            single = false;
        } else {
            return null;
        }
        if (!(PatternDetailsHelper.decodePattern(pattern, level) instanceof OmniversalPatternDetails details)) {
            return new UselessModCompat.TargetState(false, false, !single, "ae2lt.tianshu.omniversal.invalid");
        }
        if (single && details.recipe().molds().size() > 1) {
            return new UselessModCompat.TargetState(false, false, false,
                    "ae2lt.tianshu.omniversal.upload.requires_multiblock");
        }
        var availability = machine.getTaskAvailability(details.recipe());
        if (availability.available()) return new UselessModCompat.TargetState(true, true, !single, "");
        boolean missingMolds = availability.statusKey().endsWith("waiting_missing_mold")
                || availability.statusKey().endsWith("waiting_mold_hub");
        return new UselessModCompat.TargetState(missingMolds, false, !single,
                missingMolds ? "ae2lt.tianshu.omniversal.upload.missing_mold" : availability.statusKey());
    }
}
