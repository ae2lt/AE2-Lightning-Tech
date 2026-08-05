package com.moakiee.ae2lt.crafting.timewheel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TimeWheelTaskWakeIndex<T> {
    private final Map<Object, Set<T>> tasksByKey = new HashMap<>();
    private final Map<T, Set<Object>> keysByTask = new IdentityHashMap<>();

    boolean park(T task, Iterable<?> keys) {
        unpark(task);

        var taskKeys = new HashSet<Object>();
        for (var key : keys) {
            if (key != null) {
                taskKeys.add(key);
            }
        }
        if (taskKeys.isEmpty()) {
            return false;
        }

        for (var key : taskKeys) {
            tasksByKey
                    .computeIfAbsent(key, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                    .add(task);
        }
        keysByTask.put(task, taskKeys);
        return true;
    }

    List<T> wake(Object key) {
        var tasks = tasksByKey.remove(key);
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<>(tasks);
        for (var task : result) {
            unpark(task);
        }
        return result;
    }

    void unpark(T task) {
        var keys = keysByTask.remove(task);
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (var key : keys) {
            var tasks = tasksByKey.get(key);
            if (tasks == null) {
                continue;
            }
            tasks.remove(task);
            if (tasks.isEmpty()) {
                tasksByKey.remove(key);
            }
        }
    }

    void clear() {
        tasksByKey.clear();
        keysByTask.clear();
    }

    boolean isEmpty() {
        return keysByTask.isEmpty();
    }
}
