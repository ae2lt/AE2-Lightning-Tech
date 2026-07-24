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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Compares an encoding candidate with patterns already visible on the ME network.
 *
 * <p>The exact stack comparison is both cheap and sufficient for normal AE2 patterns. Decoded
 * definitions cover compatible pattern items whose irrelevant outer stack data differs while the
 * encoded pattern definition remains the same.
 */
public final class PatternEncodingDuplicateFilter {
    public static boolean containsEquivalentPattern(
            InternalInventory inventory, ItemStack candidate, @Nullable Level level) {
        if (inventory == null || candidate == null || candidate.isEmpty()) {
            return false;
        }

        var closedLoopPayload = readClosedLoopPayload(candidate, level);
        boolean closedLoopCandidate = closedLoopPayload != null;
        IPatternDetails candidateDetails = closedLoopCandidate ? null : decode(candidate, level);
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
            if (Objects.equals(stored.getDefinition(), candidate.getDefinition())) {
                return true;
            }
            return stored.equals(candidate);
        } catch (RuntimeException ignored) {
            // Third-party implementations may decode successfully but fail while exposing data.
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
        } catch (RuntimeException ignored) {
            // A malformed third-party pattern must not break the complete target-list refresh.
            return null;
        }
    }

    private PatternEncodingDuplicateFilter() {
    }
}
