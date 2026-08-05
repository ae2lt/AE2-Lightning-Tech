package com.moakiee.ae2lt.crafting.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

class DynamicCraftingCpuClusterIndexTest {
    @Test
    void observesNullActivationReplacementAndSuspensionWithoutReplacingProvider() {
        var provider = new MutableProvider();
        var first = new Object();
        var second = new Object();
        var added = new ArrayList<Object>();
        var index = new DynamicCraftingCpuClusterIndex<MutableProvider, Object>();

        index.addProvider(provider);
        assertFalse(index.refresh(current -> current.cluster, added::add));

        provider.cluster = first;
        assertTrue(index.refresh(current -> current.cluster, added::add));
        assertSame(first, index.clusters().iterator().next());
        assertEquals(1, added.size());

        assertFalse(index.refresh(current -> current.cluster, added::add));
        assertEquals(1, added.size());

        provider.cluster = second;
        assertTrue(index.refresh(current -> current.cluster, added::add));
        assertSame(second, index.clusters().iterator().next());
        assertEquals(2, added.size());

        provider.cluster = null;
        assertTrue(index.refresh(current -> current.cluster, added::add));
        assertTrue(index.clusters().isEmpty());
    }

    @Test
    void deduplicatesSharedClusterByIdentity() {
        var cluster = new Object();
        var first = new MutableProvider();
        var second = new MutableProvider();
        first.cluster = cluster;
        second.cluster = cluster;
        var added = new ArrayList<Object>();
        var index = new DynamicCraftingCpuClusterIndex<MutableProvider, Object>();

        index.replaceProviders(java.util.List.of(first, second));
        assertTrue(index.refresh(current -> current.cluster, added::add));
        assertEquals(1, index.clusters().size());
        assertEquals(1, added.size());
    }

    private static final class MutableProvider {
        private Object cluster;
    }
}
