package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.EncodedCraftingPattern;
import appeng.crafting.pattern.EncodedProcessingPattern;
import appeng.crafting.pattern.EncodedSmithingTablePattern;
import appeng.crafting.pattern.EncodedStonecuttingPattern;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.item.OverloadPatternItem;
import com.moakiee.ae2lt.logic.AdvancedAECompat;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternPayload;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Compares an encoding candidate with patterns already visible on the ME network.
 *
 * <p>Outer item components are deliberately not used as recipe identity. Mods commonly add the
 * encoding player, timestamp or tracking data beside the real encoded payload. Native AE2 pattern
 * components and supported custom-pattern payloads are compared directly; unknown third-party
 * patterns remain on the conservative exact-stack path.
 */
public final class PatternEncodingDuplicateFilter {
    public static boolean containsEquivalentPattern(
            InternalInventory inventory, ItemStack candidate, @Nullable Level level) {
        if (inventory == null || candidate == null || candidate.isEmpty()) {
            return false;
        }

        var closedLoopPayload = readClosedLoopPayload(candidate, level);
        boolean closedLoopCandidate = closedLoopPayload != null;
        var overloadPayload = closedLoopCandidate ? null : readOverloadPayload(candidate);
        IPatternDetails candidateDetails = closedLoopCandidate || overloadPayload != null
                ? null : decode(candidate, level);
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stored = inventory.getStackInSlot(slot);
            if (stored.isEmpty()) {
                continue;
            }
            if (closedLoopCandidate) {
                if (sameClosedLoopPayload(
                        readClosedLoopPayload(stored, level), closedLoopPayload)) {
                    return true;
                }
                continue;
            }
            if (ItemStack.isSameItemSameComponents(stored, candidate)) {
                return true;
            }

            if (overloadPayload != null) {
                if (sameOverloadPayload(readOverloadPayload(stored), overloadPayload, level)) {
                    return true;
                }
                continue;
            }

            var nativeComparison = compareNativePatternSemantics(stored, candidate);
            if (nativeComparison != null) {
                if (nativeComparison) {
                    return true;
                }
                continue;
            }

            var storedDetails = decode(stored, level);
            if (sameDetails(storedDetails, candidateDetails)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enabled is runtime management state rather than recipe semantics. Excluding it prevents an
     * unchanged disabled pattern from bypassing duplicate detection.
     */
    @Nullable
    private static ClosedLoopPatternPayload readClosedLoopPayload(
            ItemStack stack, @Nullable Level level) {
        if (level == null || stack == null
                || !(stack.getItem() instanceof ClosedLoopPatternItem item)) {
            return null;
        }
        return item.readPayload(stack, level).orElse(null);
    }

    static boolean sameClosedLoopPayload(
            @Nullable ClosedLoopPatternPayload stored,
            @Nullable ClosedLoopPatternPayload candidate) {
        if (stored == null || candidate == null) {
            return false;
        }
        return sameMembers(stored.memberPatterns(), candidate.memberPatterns())
                && sameStacks(stored.seeds(), candidate.seeds())
                && sameStacks(stored.externalInputs(), candidate.externalInputs())
                && sameStacks(stored.netOutputs(), candidate.netOutputs())
                && stored.executionSeedMultiplier() == candidate.executionSeedMultiplier()
                && stored.storedTaskMultiplier() == candidate.storedTaskMultiplier();
    }

    private static boolean sameMembers(
            List<ClosedLoopMemberPattern> stored,
            List<ClosedLoopMemberPattern> candidate) {
        if (stored.size() != candidate.size()) {
            return false;
        }
        for (int i = 0; i < stored.size(); i++) {
            var left = stored.get(i);
            var right = candidate.get(i);
            if (left.copiesPerCycle() != right.copiesPerCycle()
                    || !left.pattern().fingerprint().equals(right.pattern().fingerprint())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameStacks(
            List<GenericStack> stored,
            List<GenericStack> candidate) {
        if (stored.size() != candidate.size()) {
            return false;
        }
        for (int i = 0; i < stored.size(); i++) {
            var left = stored.get(i);
            var right = candidate.get(i);
            if (left.amount() != right.amount()
                    || !Objects.equals(left.what(), right.what())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameDetails(
            @Nullable IPatternDetails stored, @Nullable IPatternDetails candidate) {
        if (stored == null || candidate == null) {
            return false;
        }
        try {
            return AdvancedAECompat.samePatternSemantics(stored, candidate);
        } catch (RuntimeException ignored) {
            // Third-party implementations may decode successfully but fail while exposing data.
            return false;
        }
    }

    /**
     * Returns {@code null} when the candidate is not a native AE2 pattern. A Boolean result means
     * the candidate is native and the stored stack has been conclusively compared with it.
     */
    @Nullable
    private static Boolean compareNativePatternSemantics(ItemStack stored, ItemStack candidate) {
        if (AEItems.CRAFTING_PATTERN.is(candidate)) {
            if (!AEItems.CRAFTING_PATTERN.is(stored)) return false;
            return sameCraftingPattern(
                    stored.get(AEComponents.ENCODED_CRAFTING_PATTERN),
                    candidate.get(AEComponents.ENCODED_CRAFTING_PATTERN));
        }
        if (AEItems.PROCESSING_PATTERN.is(candidate)) {
            if (!AEItems.PROCESSING_PATTERN.is(stored)) return false;
            return sameProcessingPattern(
                    stored.get(AEComponents.ENCODED_PROCESSING_PATTERN),
                    candidate.get(AEComponents.ENCODED_PROCESSING_PATTERN));
        }
        if (AEItems.SMITHING_TABLE_PATTERN.is(candidate)) {
            if (!AEItems.SMITHING_TABLE_PATTERN.is(stored)) return false;
            return sameSmithingPattern(
                    stored.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN),
                    candidate.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN));
        }
        if (AEItems.STONECUTTING_PATTERN.is(candidate)) {
            if (!AEItems.STONECUTTING_PATTERN.is(stored)) return false;
            return sameStonecuttingPattern(
                    stored.get(AEComponents.ENCODED_STONECUTTING_PATTERN),
                    candidate.get(AEComponents.ENCODED_STONECUTTING_PATTERN));
        }
        return null;
    }

    static boolean sameCraftingPattern(
            @Nullable EncodedCraftingPattern stored,
            @Nullable EncodedCraftingPattern candidate) {
        return stored != null && candidate != null
                && Objects.equals(stored.recipeId(), candidate.recipeId())
                && stored.canSubstitute() == candidate.canSubstitute()
                && stored.canSubstituteFluids() == candidate.canSubstituteFluids()
                && sameItemStacks(stored.inputs(), candidate.inputs())
                && sameItemStack(stored.result(), candidate.result());
    }

    static boolean sameProcessingPattern(
            @Nullable EncodedProcessingPattern stored,
            @Nullable EncodedProcessingPattern candidate) {
        return stored != null && candidate != null
                && sameNullableStacks(stored.sparseInputs(), candidate.sparseInputs())
                && sameNullableStacks(stored.sparseOutputs(), candidate.sparseOutputs());
    }

    static boolean sameSmithingPattern(
            @Nullable EncodedSmithingTablePattern stored,
            @Nullable EncodedSmithingTablePattern candidate) {
        return stored != null && candidate != null
                && Objects.equals(stored.recipeId(), candidate.recipeId())
                && stored.canSubstitute() == candidate.canSubstitute()
                && sameItemStack(stored.template(), candidate.template())
                && sameItemStack(stored.base(), candidate.base())
                && sameItemStack(stored.addition(), candidate.addition())
                && sameItemStack(stored.resultItem(), candidate.resultItem());
    }

    static boolean sameStonecuttingPattern(
            @Nullable EncodedStonecuttingPattern stored,
            @Nullable EncodedStonecuttingPattern candidate) {
        return stored != null && candidate != null
                && Objects.equals(stored.recipeId(), candidate.recipeId())
                && stored.canSubstitute() == candidate.canSubstitute()
                && sameItemStack(stored.input(), candidate.input())
                && sameItemStack(stored.output(), candidate.output());
    }

    @Nullable
    private static OverloadPatternPayload readOverloadPayload(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof OverloadPatternItem item)) return null;
        try {
            return item.readPayload(stack).orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean sameOverloadPayload(
            @Nullable OverloadPatternPayload stored,
            @Nullable OverloadPatternPayload candidate,
            @Nullable Level level) {
        if (stored == null || candidate == null || level == null
                || stored.requiredHostKind() != candidate.requiredHostKind()
                || !sameOverloadConfiguration(stored.encodedPattern(), candidate.encodedPattern())) {
            return false;
        }
        try {
            var storedSource = stored.sourcePattern().toItemStack(level.registryAccess());
            var candidateSource = candidate.sourcePattern().toItemStack(level.registryAccess());
            if (ItemStack.isSameItemSameComponents(storedSource, candidateSource)) return true;
            var nativeComparison = compareNativePatternSemantics(storedSource, candidateSource);
            if (nativeComparison != null) return nativeComparison;
            return AdvancedAECompat.samePatternSemantics(
                    decode(storedSource, level), decode(candidateSource, level));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean sameOverloadConfiguration(
            EncodedOverloadPattern stored, EncodedOverloadPattern candidate) {
        return List.copyOf(stored.inputSlots()).equals(List.copyOf(candidate.inputSlots()))
                && List.copyOf(stored.outputSlots()).equals(List.copyOf(candidate.outputSlots()));
    }

    private static boolean sameItemStacks(List<ItemStack> stored, List<ItemStack> candidate) {
        if (stored.size() != candidate.size()) return false;
        for (int i = 0; i < stored.size(); i++) {
            if (!sameItemStack(stored.get(i), candidate.get(i))) return false;
        }
        return true;
    }

    private static boolean sameItemStack(ItemStack stored, ItemStack candidate) {
        if (stored == null || candidate == null) return stored == candidate;
        if (stored.isEmpty() || candidate.isEmpty()) return stored.isEmpty() && candidate.isEmpty();
        return stored.getCount() == candidate.getCount()
                && ItemStack.isSameItemSameComponents(stored, candidate);
    }

    private static boolean sameNullableStacks(
            List<GenericStack> stored, List<GenericStack> candidate) {
        if (stored.size() != candidate.size()) return false;
        for (int i = 0; i < stored.size(); i++) {
            if (!Objects.equals(stored.get(i), candidate.get(i))) return false;
        }
        return true;
    }

    @Nullable
    private static IPatternDetails decode(ItemStack stack, @Nullable Level level) {
        if (level == null) {
            return null;
        }
        try {
            return PatternDetailsHelper.decodePattern(stack, level);
        } catch (RuntimeException ignored) {
            // A malformed third-party pattern must not break the complete target-list refresh.
            return null;
        }
    }

    private PatternEncodingDuplicateFilter() {
    }
}
