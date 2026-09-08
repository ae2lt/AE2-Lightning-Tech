package com.moakiee.ae2lt.integration.useless;

import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternContainer;
import com.moakiee.ae2lt.logic.tianshu.terminal.OmniversalPatternDraft;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional boundary: common terminal classes never resolve Useless Mod classes. */
public final class UselessModCompat {
    private static final Logger LOG = LoggerFactory.getLogger(UselessModCompat.class);
    private static final ResourceLocation PATTERN_ID =
            ResourceLocation.fromNamespaceAndPath("useless_mod", "omniversal_pattern");
    private static boolean reportedFailure;

    private UselessModCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded("useless_mod");
    }

    public static boolean isOmniversalPattern(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && PATTERN_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static ItemStack icon() {
        return isLoaded() ? new ItemStack(BuiltInRegistries.ITEM.get(PATTERN_ID)) : ItemStack.EMPTY;
    }

    public static long recipeGeneration() {
        // A direct method reference resolves the optional bridge before call() can check ModList.
        return call(() -> UselessModPatternBridge.recipeGeneration(), -1L);
    }

    /** Called by the optional JEI mixin with the viewer's exact catalog entry. */
    public static boolean isViewerRecipe(Object entry) {
        return call(() -> UselessModPatternBridge.isViewerRecipe(entry), false);
    }

    public static ItemStack encodeViewerRecipe(Object entry, Level level) {
        return call(() -> UselessModPatternBridge.encodeViewerRecipe(entry, level), ItemStack.EMPTY);
    }

    /** Re-resolves the recipe and validates all inputs/outputs on the authoritative server. */
    public static ItemStack encodeDraft(OmniversalPatternDraft draft, Level level) {
        if (draft == null || draft.isEmpty()) return ItemStack.EMPTY;
        return call(() -> UselessModPatternBridge.encodeDraft(draft.pattern(), level), ItemStack.EMPTY);
    }

    public static EncodingResult encodeMatchingDraft(OmniversalPatternDraft draft, Level level) {
        return call(() -> UselessModPatternBridge.encodeMatchingDraft(draft.pattern(), draft.molds(), level),
                new EncodingResult(ItemStack.EMPTY, "ae2lt.tianshu.omniversal.no_match"));
    }

    public record EncodingResult(ItemStack pattern, String reason) {
    }

    public static Preview preview(OmniversalPatternDraft draft, Level level) {
        if (draft == null || draft.isEmpty()) return Preview.EMPTY;
        return call(() -> UselessModPatternBridge.preview(draft.pattern(), level), Preview.EMPTY);
    }

    public static void clearPendingRecipe(Object logic) {
        call(() -> {
            UselessModPatternBridge.clearPendingRecipe(logic);
            return true;
        }, false);
    }

    @Nullable
    public static TargetState targetState(PatternContainer target, ItemStack pattern, Level level) {
        return call(() -> UselessModPatternBridge.targetState(target, pattern, level), null);
    }

    private static <T> T call(Supplier<T> operation, T fallback) {
        if (!isLoaded()) return fallback;
        try {
            return operation.get();
        } catch (RuntimeException | LinkageError failure) {
            if (!reportedFailure) {
                reportedFailure = true;
                LOG.warn("Omniversal pattern compatibility could not complete an operation", failure);
            }
            return fallback;
        }
    }

    public record Preview(List<GenericStack> inputs, List<GenericStack> outputs,
                          List<ItemStack> molds, String recipeId) {
        public static final Preview EMPTY = new Preview(List.of(), List.of(), List.of(), "");

        public Preview {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            molds = molds.stream().map(ItemStack::copy).toList();
        }
    }

    /** Auto-upload requires matching molds and prefers a ready multiblock over a ready single furnace. */
    public record TargetState(boolean supported, boolean ready, boolean multiblock, String reason) {
    }
}
