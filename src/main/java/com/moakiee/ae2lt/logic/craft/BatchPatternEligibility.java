package com.moakiee.ae2lt.logic.craft;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.function.Predicate;

import appeng.api.crafting.IPatternDetails;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

import com.moakiee.thunderbolt.core.crafting.loop.ClosedLoopBatchPatternDetails;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopExpandedPatternDetails;
import com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails;

/**
 * Selects pattern types that may enter Thunderbolt's batch-provider dispatch.
 */
public final class BatchPatternEligibility {
    private BatchPatternEligibility() {
    }

    public static boolean isEligible(IPatternDetails details) {
        return isEligible(
                details,
                candidate -> candidate instanceof ClosedLoopExpandedPatternDetails,
                candidate -> candidate instanceof ClosedLoopBatchPatternDetails);
    }

    static boolean isEligible(
            IPatternDetails details,
            Predicate<IPatternDetails> isClosedLoop,
            Predicate<IPatternDetails> isClosedLoopBatchSafe) {
        var visited = Collections.newSetFromMap(
                new IdentityHashMap<IPatternDetails, Boolean>());
        var current = details;

        while (current != null && visited.add(current)) {
            // Closed-loop execution has stricter accounting requirements than ordinary patterns.
            if (isClosedLoop.test(current)) {
                return isClosedLoopBatchSafe.test(current);
            }
            if (current instanceof IWrappedPatternDetails wrapped) {
                current = wrapped.wrappedPatternDetails();
                continue;
            }
            return current instanceof IMolecularAssemblerSupportedPattern
                    || current.supportsPushInputsToExternalInventory();
        }
        return false;
    }
}
