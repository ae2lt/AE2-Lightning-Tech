package com.moakiee.ae2lt.grid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

final class FrequencyDeviceIndex<T> {
    private final Map<Integer, LinkedHashMap<DeviceKey, T>> byFrequency = new HashMap<>();

    boolean put(int frequencyId, String dimensionId, long posLong, T entry) {
        var devices = byFrequency.computeIfAbsent(frequencyId, ignored -> new LinkedHashMap<>());
        var key = new DeviceKey(dimensionId, posLong);
        var existing = devices.get(key);
        if (Objects.equals(existing, entry)) {
            return false;
        }
        devices.put(key, entry);
        return true;
    }

    boolean remove(int frequencyId, String dimensionId, long posLong) {
        var devices = byFrequency.get(frequencyId);
        if (devices == null) {
            return false;
        }
        boolean removed = devices.remove(new DeviceKey(dimensionId, posLong)) != null;
        if (devices.isEmpty()) {
            byFrequency.remove(frequencyId);
        }
        return removed;
    }

    List<T> get(int frequencyId) {
        var devices = byFrequency.get(frequencyId);
        if (devices == null || devices.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(devices.values());
    }

    void clearFrequency(int frequencyId) {
        byFrequency.remove(frequencyId);
    }

    void clear() {
        byFrequency.clear();
    }

    void forEach(BiConsumer<Integer, T> consumer) {
        for (var frequency : byFrequency.entrySet()) {
            for (var device : frequency.getValue().values()) {
                consumer.accept(frequency.getKey(), device);
            }
        }
    }

    private record DeviceKey(String dimensionId, long posLong) {
    }
}
