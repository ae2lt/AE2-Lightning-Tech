package com.moakiee.ae2lt.logic;

import java.util.Map;

import com.google.common.collect.MapMaker;

/** Factory for non-owning maps whose keys are compared by reference. */
final class WeakIdentityMaps {

    /**
     * Guava deliberately switches weak-key maps from {@code equals()} to
     * identity equality. This makes the map suitable as a front cache for
     * third-party objects whose structural equality is comparatively costly.
     */
    static <K, V> Map<K, V> weakKeys() {
        return new MapMaker()
                .weakKeys()
                .concurrencyLevel(1)
                .makeMap();
    }

    /**
     * Creates a pure canonicalization cache. Neither side owns the objects it
     * relates, so {@code key == value} cannot turn a weak key into a strong one.
     */
    static <K, V> Map<K, V> weakKeysAndValues() {
        return new MapMaker()
                .weakKeys()
                .weakValues()
                .concurrencyLevel(1)
                .makeMap();
    }

    private WeakIdentityMaps() {
    }
}
