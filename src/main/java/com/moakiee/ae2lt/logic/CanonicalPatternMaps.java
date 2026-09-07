package com.moakiee.ae2lt.logic;

import java.util.Map;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import appeng.api.crafting.IPatternDetails;

/** Runtime maps for catalog-canonical patterns: neither equals nor hashCode is called. */
final class CanonicalPatternMaps {
    static <V> Map<IPatternDetails, V> create() {
        return new Reference2ObjectOpenHashMap<>();
    }

    private CanonicalPatternMaps() {
    }
}
