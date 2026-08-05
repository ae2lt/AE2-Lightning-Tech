package com.moakiee.ae2lt.overload.runtime.cpu;

import java.util.Objects;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import java.util.LinkedHashSet;

/** Shared insert/push accounting used by the AE2, AdvancedAE and AE2Eco CPU mixins. */
public final class OverloadCpuInsertSupport {
    private OverloadCpuInsertSupport() {
    }

    /**
     * Removes the exact ID_ONLY prefix mirrored in AE2's native waiting list. The mixin then
     * claims that prefix through overload state, which keeps pending and waiting synchronized and
     * preserves requester versus CPU-inventory ownership.
     */
    public static long nativeStrictMatch(
            Object logic,
            AEKey incoming,
            long rawStrictMatch,
            long nativeExactWaiting) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(incoming, "incoming");
        long exactPending = OverloadCpuStateManager.INSTANCE.getRemainingForExactKey(
                logic, incoming);
        return nativeStrictMatch(rawStrictMatch, nativeExactWaiting, exactPending);
    }

    static long nativeStrictMatch(
            long rawStrictMatch,
            long nativeExactWaiting,
            long exactPending) {
        long normalizedStrict = Math.max(0L, rawStrictMatch);
        long strictExcess = Math.max(
                0L,
                Math.max(0L, nativeExactWaiting) - Math.max(0L, exactPending));
        return Math.min(normalizedStrict, strictExcess);
    }

    /**
     * Ordinary outputs and container remainders have no overload-slot provenance. Do not dispatch
     * them while an unresolved ID_ONLY output of the same item id exists on the CPU.
     */
    public static boolean hasPendingCollisionWithOrdinaryPattern(
            Object logic,
            IPatternDetails details) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(details, "details");
        var states = OverloadCpuStateManager.INSTANCE;
        if (!states.hasAnyPending(logic)) return false;

        for (var output : details.getOutputs()) {
            if (output.what() instanceof AEItemKey item
                    && states.getRemainingForItem(logic, item.getId()) > 0) {
                return true;
            }
        }
        for (var input : details.getInputs()) {
            for (var possible : input.getPossibleInputs()) {
                var possibleKey = possible.what();
                var remainder = possibleKey != null
                        ? input.getRemainingKey(possibleKey) : null;
                if (remainder instanceof AEItemKey item
                        && states.getRemainingForItem(logic, item.getId()) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Standard AE2-family CPUs may finish the job inside vanilla {@code insert} before the mixin's
     * RETURN handler can consume an overlapping ID_ONLY tail. Keep STRICT and ID_ONLY work
     * serialized there. TimeWheel has its own pending-aware completion path and does not use this
     * conservative guard.
     */
    public static boolean hasStrictCollisionWithOverloadPattern(
            Object logic,
            IPatternDetails details,
            OverloadedProviderOnlyPatternDetails overload,
            KeyCounter nativeWaiting) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(overload, "overload");
        Objects.requireNonNull(nativeWaiting, "nativeWaiting");

        var idOnlyIds = new LinkedHashSet<net.minecraft.resources.ResourceLocation>();
        var strictIds = new LinkedHashSet<net.minecraft.resources.ResourceLocation>();
        for (var output : overload.overloadPatternDetailsView().outputs()) {
            var key = AEItemKey.of(output.template());
            if (key == null) return true;
            (output.matchMode() == MatchMode.ID_ONLY ? idOnlyIds : strictIds).add(key.getId());
        }
        for (var input : details.getInputs()) {
            for (var possible : input.getPossibleInputs()) {
                var possibleKey = possible.what();
                var remainder = possibleKey != null
                        ? input.getRemainingKey(possibleKey) : null;
                if (remainder instanceof AEItemKey item) strictIds.add(item.getId());
            }
        }
        var states = OverloadCpuStateManager.INSTANCE;
        for (var itemId : idOnlyIds) {
            if (strictIds.contains(itemId)
                    || states.hasNativeStrictWaiting(logic, itemId, nativeWaiting)) {
                return true;
            }
        }
        return false;
    }
}
