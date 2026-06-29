package com.moakiee.ae2lt.logic.batch;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

import appeng.api.networking.crafting.ICraftingProvider;

public final class BatchProviderFilterIterable implements Iterable<ICraftingProvider> {
    private final Iterable<ICraftingProvider> raw;
    private final IdentityHashMap<ICraftingProvider, Boolean> excluded;

    public BatchProviderFilterIterable(Iterable<ICraftingProvider> raw,
                                       IdentityHashMap<ICraftingProvider, Boolean> excluded) {
        this.raw = raw;
        this.excluded = excluded;
    }

    @Override
    public Iterator<ICraftingProvider> iterator() {
        return new FilteringIterator(raw.iterator(), excluded);
    }

    private static final class FilteringIterator implements Iterator<ICraftingProvider> {
        private final Iterator<ICraftingProvider> raw;
        private final IdentityHashMap<ICraftingProvider, Boolean> excluded;
        private ICraftingProvider next;
        private boolean ready;

        private FilteringIterator(Iterator<ICraftingProvider> raw,
                                  IdentityHashMap<ICraftingProvider, Boolean> excluded) {
            this.raw = raw;
            this.excluded = excluded;
        }

        @Override
        public boolean hasNext() {
            while (!ready && raw.hasNext()) {
                var candidate = raw.next();
                if (!excluded.containsKey(candidate)) {
                    next = candidate;
                    ready = true;
                }
            }
            return ready;
        }

        @Override
        public ICraftingProvider next() {
            if (!ready && !hasNext()) {
                throw new NoSuchElementException();
            }
            ready = false;
            var result = next;
            next = null;
            return result;
        }
    }
}
