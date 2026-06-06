package com.moakiee.ae2lt.logic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WirelessPatternContainerGroupSelector {
    private WirelessPatternContainerGroupSelector() {
    }

    public static <T> Optional<T> selectMostFrequent(List<T> groups) {
        if (groups.isEmpty()) {
            return Optional.empty();
        }

        var counts = new LinkedHashMap<T, Integer>();
        for (var group : groups) {
            counts.merge(group, 1, Integer::sum);
        }

        T best = null;
        int bestCount = 0;
        for (Map.Entry<T, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return Optional.ofNullable(best);
    }
}
