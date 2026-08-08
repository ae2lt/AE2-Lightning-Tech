package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
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
 * <p>The exact stack comparison is both cheap and sufficient for normal AE2 patterns. Decoded
 * definitions cover compatible pattern items whose irrelevant outer stack data differs while the
 * encoded pattern definition remains the same.
 */
public final class PatternEncodingDuplicateFilter {
    private static final Logger LOG = LoggerFactory.getLogger("ae2lt/TianshuDuplicate");

    public enum MatchMethod {
        NONE,
        EXACT_STACK,
        DECODED_DEFINITION,
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
        IPatternDetails candidateDetails = closedLoopCandidate ? null : decode(candidate, level);
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
            if (ItemStack.isSameItemSameComponents(stored, candidate)) {
                return new CheckResult(
                        true, slot, MatchMethod.EXACT_STACK,
                        occupiedSlots, undecodableSlots);
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
        if (!closedLoopCandidate && candidateDetails == null) {
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
            if (Objects.equals(stored.getDefinition(), candidate.getDefinition())) {
                return true;
            }
            return stored.equals(candidate);
        } catch (RuntimeException failure) {
            // Third-party implementations may decode successfully but fail while exposing data.
            LOG.warn("Decoded pattern comparison failed (stored={}, candidate={}, storedType={}, "
                            + "candidateType={})",
                    describeStack(storedStack), describeStack(candidateStack),
                    stored.getClass().getName(), candidate.getClass().getName(), failure);
            return false;
        }
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
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "#" + Integer.toUnsignedString(ItemStack.hashItemAndComponents(stack), 16);
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
