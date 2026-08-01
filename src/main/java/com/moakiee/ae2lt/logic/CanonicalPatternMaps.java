package com.moakiee.ae2lt.logic;

import java.util.Map;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;

import appeng.api.crafting.IPatternDetails;

/** Hashes canonical patterns structurally but compares them only by reference. */
final class CanonicalPatternMaps {
    private static final Hash.Strategy<IPatternDetails> STRATEGY =
            new Hash.Strategy<>() {
                @Override
                public int hashCode(IPatternDetails pattern) {
                    return pattern == null ? 0 : pattern.hashCode();
                }

                @Override
                public boolean equals(
                        IPatternDetails left,
                        IPatternDetails right) {
                    return left == right;
                }
            };

    static Hash.Strategy<IPatternDetails> strategy() {
        return STRATEGY;
    }

    static <V> Map<IPatternDetails, V> create() {
        return new Object2ObjectOpenCustomHashMap<>(STRATEGY);
    }

    private CanonicalPatternMaps() {
    }
}
