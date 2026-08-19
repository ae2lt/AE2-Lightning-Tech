package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.item.OverloadPatternItem;
import com.moakiee.ae2lt.logic.AdvancedAECompat;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternPayload;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compares an encoding candidate with patterns already visible on the ME network.
 *
 * <p>Outer item components are deliberately not used as recipe identity. Mods commonly add the
 * encoding player, timestamp or tracking data beside the real encoded payload. Native AE2 pattern
 * components and supported custom-pattern payloads are compared directly; unknown third-party
 * patterns remain on the conservative exact-stack path.
 */
public final class PatternEncodingDuplicateFilter {
    private static final Logger LOG = LoggerFactory.getLogger("ae2lt/TianshuDuplicate");

    public enum MatchMethod {
        NONE,
        EXACT_STACK,
        DECODED_DEFINITION,
        OVERLOAD_PAYLOAD,
        CLOSED_LOOP_PAYLOAD
    }

    public record CheckResult(
            boolean duplicate,
            int matchedSlot,
            MatchMethod matchMethod,
            int occupiedSlots,
            int undecodableSlots) {
    }

    public static boolean containsEquivalentPattern(
            InternalInventory inventory, ItemStack candidate, @Nullable Level level) {
        return checkEquivalentPattern(inventory, candidate, level).duplicate();
    }

    public static CheckResult checkEquivalentPattern(
            InternalInventory inventory, ItemStack candidate, @Nullable Level level) {
        if (inventory == null || candidate == null || candidate.isEmpty()) {
            return new CheckResult(false, -1, MatchMethod.NONE, 0, 0);
        }

        var closedLoopPayload = readClosedLoopPayload(candidate, level);
        boolean closedLoopCandidate = closedLoopPayload != null;
        var overloadPayload = closedLoopCandidate ? null : readOverloadPayload(candidate);
        IPatternDetails candidateDetails = closedLoopCandidate || overloadPayload != null
                ? null : decode(candidate, level);
        int occupiedSlots = 0;
        int undecodableSlots = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stored = inventory.getStackInSlot(slot);
            if (stored.isEmpty()) {
                continue;
            }
            occupiedSlots++;
            if (closedLoopCandidate) {
                if (sameClosedLoopPayload(
                        readClosedLoopPayload(stored, level), closedLoopPayload)) {
                    return new CheckResult(
                            true, slot, MatchMethod.CLOSED_LOOP_PAYLOAD,
                            occupiedSlots, undecodableSlots);
                }
                continue;
            }
            if (ItemStack.isSameItemSameTags(stored, candidate)) {
                return new CheckResult(
                        true, slot, MatchMethod.EXACT_STACK,
                        occupiedSlots, undecodableSlots);
            }

            if (overloadPayload != null) {
                if (sameOverloadPayload(readOverloadPayload(stored), overloadPayload, level)) {
                    return new CheckResult(
                            true, slot, MatchMethod.OVERLOAD_PAYLOAD,
                            occupiedSlots, undecodableSlots);
                }
                continue;
            }

            var nativeComparison = compareNativePatternSemantics(stored, candidate, level);
            if (nativeComparison != null) {
                if (nativeComparison) {
                    return new CheckResult(
                            true, slot, MatchMethod.DECODED_DEFINITION,
                            occupiedSlots, undecodableSlots);
                }
                continue;
            }

            var storedDetails = decode(stored, level);
            if (storedDetails == null) {
                undecodableSlots++;
            }
            if (sameDetails(storedDetails, candidateDetails, stored, candidate)) {
                return new CheckResult(
                        true, slot, MatchMethod.DECODED_DEFINITION,
                        occupiedSlots, undecodableSlots);
            }
        }
        if (!closedLoopCandidate && overloadPayload == null && candidateDetails == null) {
            LOG.warn("Candidate could not be decoded during duplicate check: {}",
                    describeStack(candidate));
        }
        return new CheckResult(
                false, -1, MatchMethod.NONE, occupiedSlots, undecodableSlots);
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
            @Nullable IPatternDetails stored,
            @Nullable IPatternDetails candidate,
            ItemStack storedStack,
            ItemStack candidateStack) {
        if (stored == null || candidate == null) {
            return false;
        }
        try {
            return AdvancedAECompat.samePatternSemantics(stored, candidate);
        } catch (RuntimeException failure) {
            // Third-party implementations may decode successfully but fail while exposing data.
            LOG.warn("Decoded pattern comparison failed (stored={}, candidate={}, storedType={}, "
                            + "candidateType={})",
                    describeStack(storedStack), describeStack(candidateStack),
                    stored.getClass().getName(), candidate.getClass().getName(), failure);
            return false;
        }
    }

    /**
     * Returns {@code null} when the candidate is not a native AE2 pattern. A Boolean result means
     * the candidate is native and the stored stack has been conclusively compared with it.
     */
    @Nullable
    private static Boolean compareNativePatternSemantics(
            ItemStack stored, ItemStack candidate, @Nullable Level level) {
        if (AEItems.CRAFTING_PATTERN.isSameAs(candidate)) {
            if (!AEItems.CRAFTING_PATTERN.isSameAs(stored)) return false;
            return sameNativePatternDefinition(stored, candidate, level);
        }
        if (AEItems.PROCESSING_PATTERN.isSameAs(candidate)) {
            if (!AEItems.PROCESSING_PATTERN.isSameAs(stored)) return false;
            return sameNativePatternDefinition(stored, candidate, level);
        }
        if (AEItems.SMITHING_TABLE_PATTERN.isSameAs(candidate)) {
            if (!AEItems.SMITHING_TABLE_PATTERN.isSameAs(stored)) return false;
            return sameNativePatternDefinition(stored, candidate, level);
        }
        if (AEItems.STONECUTTING_PATTERN.isSameAs(candidate)) {
            if (!AEItems.STONECUTTING_PATTERN.isSameAs(stored)) return false;
            return sameNativePatternDefinition(stored, candidate, level);
        }
        return null;
    }

    private static boolean sameNativePatternDefinition(
            ItemStack stored, ItemStack candidate, @Nullable Level level) {
        var storedDetails = decode(stored, level);
        var candidateDetails = decode(candidate, level);
        if (storedDetails == null || candidateDetails == null) return false;
        try {
            return Objects.equals(storedDetails.getDefinition(), candidateDetails.getDefinition());
        } catch (RuntimeException failure) {
            LOG.warn("Native pattern comparison failed (stored={}, candidate={})",
                    describeStack(stored), describeStack(candidate), failure);
            return false;
        }
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
            var storedSource = stored.sourcePattern().toItemStack();
            var candidateSource = candidate.sourcePattern().toItemStack();
            if (ItemStack.isSameItemSameTags(storedSource, candidateSource)) return true;
            var nativeComparison = compareNativePatternSemantics(storedSource, candidateSource, level);
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

    @Nullable
    private static IPatternDetails decode(ItemStack stack, @Nullable Level level) {
        if (level == null) {
            return null;
        }
        try {
            return PatternDetailsHelper.decodePattern(stack, level);
        } catch (RuntimeException failure) {
            // A malformed third-party pattern must not break the complete target-list refresh.
            LOG.warn("Pattern decode failed during duplicate check: {}",
                    describeStack(stack), failure);
            return null;
        }
    }

    public static String describeStack(ItemStack stack) {
        if (stack == null) {
            return "null";
        }
        if (stack.isEmpty()) {
            return "empty";
        }
        int fingerprint = Objects.hash(stack.getItem(), stack.getTag());
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "#" + Integer.toUnsignedString(fingerprint, 16);
    }

    public static String describeOccupiedStacks(InternalInventory inventory, int limit) {
        if (inventory == null) {
            return "null-inventory";
        }
        var result = new StringBuilder();
        int described = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (described >= Math.max(1, limit)) {
                result.append(", ...");
                break;
            }
            if (described > 0) {
                result.append(", ");
            }
            result.append(slot).append('=').append(describeStack(stack));
            described++;
        }
        return described == 0 ? "empty" : result.toString();
    }

    private PatternEncodingDuplicateFilter() {
    }
}
