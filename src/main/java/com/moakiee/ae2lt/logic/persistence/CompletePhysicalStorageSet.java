package com.moakiee.ae2lt.logic.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Resolves an ordered physical storage set without ever exposing a loaded subset. */
public final class CompletePhysicalStorageSet {
    public static <P, T> Optional<List<T>> resolve(
            List<P> positions,
            Function<P, T> resolver) {
        if (positions == null || resolver == null) return Optional.empty();
        var result = new ArrayList<T>(positions.size());
        for (var position : positions) {
            var storage = resolver.apply(position);
            if (storage == null) return Optional.empty();
            result.add(storage);
        }
        return Optional.of(List.copyOf(result));
    }

    private CompletePhysicalStorageSet() {
    }
}
