package com.moakiee.ae2lt.grid.wirelesslink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

final class WirelessLinkIndex {
    private static final int MIN_TOMBSTONES_BEFORE_COMPACT = 128;

    private final Map<UUID, WirelessLink> byId = new LinkedHashMap<>();
    private final Map<String, Long2ObjectOpenHashMap<LinkedHashMap<UUID, WirelessLink>>> byPosition =
            new HashMap<>();
    private final List<UUID> orderedIds = new ArrayList<>();

    private int cursor;
    private int tombstones;

    boolean isEmpty() {
        return byId.isEmpty();
    }

    boolean contains(UUID linkId) {
        return byId.containsKey(linkId);
    }

    WirelessLink get(UUID linkId) {
        return byId.get(linkId);
    }

    Collection<WirelessLink> values() {
        return byId.values();
    }

    List<WirelessLink> findAllInDimension(String dimensionId) {
        var matches = new ArrayList<WirelessLink>();
        for (var link : byId.values()) {
            if (link.dimensionId().equals(dimensionId)) {
                matches.add(link);
            }
        }
        return matches;
    }

    Collection<WirelessLink> findAllAt(String dimensionId, long posLong) {
        var dimension = byPosition.get(dimensionId);
        if (dimension == null) {
            return List.of();
        }
        var matches = dimension.get(posLong);
        return matches == null ? List.of() : matches.values();
    }

    void clear() {
        byId.clear();
        byPosition.clear();
        orderedIds.clear();
        cursor = 0;
        tombstones = 0;
    }

    void put(WirelessLink link) {
        var previous = byId.put(link.linkId(), link);
        if (previous != null && (!previous.dimensionId().equals(link.dimensionId())
                || previous.posLong() != link.posLong())) {
            removeFromPositionIndex(previous);
        }
        byPosition
                .computeIfAbsent(link.dimensionId(), ignored -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(link.posLong(), ignored -> new LinkedHashMap<>())
                .put(link.linkId(), link);
        if (previous == null) {
            orderedIds.add(link.linkId());
        }
    }

    WirelessLink remove(UUID linkId) {
        var removed = byId.remove(linkId);
        if (removed == null) {
            return null;
        }
        removeFromPositionIndex(removed);
        tombstones++;
        compactOrderIfNeeded();
        return removed;
    }

    private void removeFromPositionIndex(WirelessLink link) {
        var dimension = byPosition.get(link.dimensionId());
        if (dimension == null) {
            return;
        }
        var matches = dimension.get(link.posLong());
        if (matches == null) {
            return;
        }
        matches.remove(link.linkId());
        if (matches.isEmpty()) {
            dimension.remove(link.posLong());
        }
        if (dimension.isEmpty()) {
            byPosition.remove(link.dimensionId());
        }
    }

    List<WirelessLink> nextBatch(int requestedBatchSize) {
        if (byId.isEmpty() || requestedBatchSize <= 0) {
            return List.of();
        }

        int limit = Math.min(requestedBatchSize, byId.size());
        var batch = new ArrayList<WirelessLink>(limit);
        int scanned = 0;
        int scanLimit = orderedIds.size();
        while (batch.size() < limit && scanned < scanLimit && !orderedIds.isEmpty()) {
            if (cursor >= orderedIds.size()) {
                cursor = 0;
            }
            var link = byId.get(orderedIds.get(cursor++));
            scanned++;
            if (link != null) {
                batch.add(link);
            }
        }
        return batch;
    }

    private void compactOrderIfNeeded() {
        if (tombstones < MIN_TOMBSTONES_BEFORE_COMPACT || tombstones * 4 < orderedIds.size()) {
            return;
        }
        orderedIds.clear();
        orderedIds.addAll(byId.keySet());
        if (cursor > orderedIds.size()) {
            cursor = orderedIds.size();
        }
        tombstones = 0;
    }
}
