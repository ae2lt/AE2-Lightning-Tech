package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/**
 * Centralizes the fuzzy-card semantics shared by every overloaded-interface
 * access path.
 */
public final class OverloadedInterfaceFuzzyResolver {
    private static final Comparator<AEKey> FUZZY_ORDER = Comparator
            .comparingInt(AEKey::getFuzzySearchValue)
            .reversed()
            .thenComparingInt(Object::hashCode);

    private OverloadedInterfaceFuzzyResolver() {
    }

    public static boolean matches(AEKey configured, @Nullable AEKey candidate,
                                  boolean fuzzyEnabled, FuzzyMode fuzzyMode) {
        Objects.requireNonNull(configured, "configured");
        Objects.requireNonNull(fuzzyMode, "fuzzyMode");
        if (configured.equals(candidate)) {
            return true;
        }
        return candidate != null
                && fuzzyEnabled
                && configured.supportsFuzzyRangeSearch()
                && configured.fuzzyEquals(candidate, fuzzyMode);
    }

    /**
     * Returns a deterministic candidate order. The exact configured key is
     * always first, even when it is currently absent from the network index.
     */
    public static List<AEKey> orderedCandidates(AEKey configured, KeyCounter available,
                                                boolean fuzzyEnabled, FuzzyMode fuzzyMode) {
        Objects.requireNonNull(configured, "configured");
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(fuzzyMode, "fuzzyMode");

        if (!fuzzyEnabled || !configured.supportsFuzzyRangeSearch()) {
            return List.of(configured);
        }

        var fuzzyCandidates = new ArrayList<AEKey>();
        for (var entry : available.findFuzzy(configured, fuzzyMode)) {
            var candidate = entry.getKey();
            if (!configured.equals(candidate)
                    && matches(configured, candidate, true, fuzzyMode)) {
                fuzzyCandidates.add(candidate);
            }
        }
        fuzzyCandidates.sort(FUZZY_ORDER);

        var ordered = new LinkedHashSet<AEKey>();
        ordered.add(configured);
        ordered.addAll(fuzzyCandidates);
        return List.copyOf(ordered);
    }

    /**
     * Reuses the selected variant while it remains available, matching the
     * single physical storage stack used by the vanilla interface.
     */
    @Nullable
    public static AEKey selectLimited(@Nullable AEKey selected, AEKey configured,
                                      List<AEKey> orderedCandidates,
                                      boolean fuzzyEnabled, FuzzyMode fuzzyMode,
                                      ToLongFunction<AEKey> availableAmount) {
        Objects.requireNonNull(configured, "configured");
        Objects.requireNonNull(orderedCandidates, "orderedCandidates");
        Objects.requireNonNull(fuzzyMode, "fuzzyMode");
        Objects.requireNonNull(availableAmount, "availableAmount");

        if (matches(configured, selected, fuzzyEnabled, fuzzyMode)
                && availableAmount.applyAsLong(selected) > 0) {
            return selected;
        }

        for (var candidate : orderedCandidates) {
            if (availableAmount.applyAsLong(candidate) > 0) {
                return candidate;
            }
        }
        return null;
    }

    public static List<AEKey> selectUnlimited(List<AEKey> orderedCandidates,
                                              ToLongFunction<AEKey> availableAmount) {
        Objects.requireNonNull(orderedCandidates, "orderedCandidates");
        Objects.requireNonNull(availableAmount, "availableAmount");
        var result = new ArrayList<AEKey>(orderedCandidates.size());
        for (var candidate : orderedCandidates) {
            if (availableAmount.applyAsLong(candidate) > 0) {
                result.add(candidate);
            }
        }
        return List.copyOf(result);
    }
}
