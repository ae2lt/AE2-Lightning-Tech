package com.moakiee.ae2lt.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/**
 * Small lazy-invalidating due-time queue.
 *
 * <p>Each key has at most one live deadline. Rescheduling appends a new entry
 * and invalidates the previous one through a monotonically increasing token.
 * Stale entries are discarded at the head and the heap is compacted when
 * rescheduling churn grows materially beyond the live-key count.</p>
 */
final class DueTaskQueue<K> {
    private static final int MIN_COMPACTION_SIZE = 64;
    private static final int COMPACTION_MULTIPLIER = 4;

    private record Schedule(long dueTick, long token) {
    }

    private record Entry<K>(K key, long dueTick, long token, long sequence) {
    }

    private final Map<K, Schedule> schedules = new HashMap<>();
    private final PriorityQueue<Entry<K>> entries = new PriorityQueue<>((left, right) -> {
        int dueComparison = Long.compare(left.dueTick(), right.dueTick());
        return dueComparison != 0
                ? dueComparison
                : Long.compare(left.sequence(), right.sequence());
    });
    private long nextToken;
    private long nextSequence;

    void schedule(K key, long dueTick) {
        long token = ++nextToken;
        schedules.put(key, new Schedule(dueTick, token));
        entries.add(new Entry<>(key, dueTick, token, nextSequence++));
        compactIfNeeded();
    }

    boolean contains(K key) {
        return schedules.containsKey(key);
    }

    void remove(K key) {
        schedules.remove(key);
    }

    void retainAll(Set<K> retainedKeys) {
        schedules.keySet().retainAll(retainedKeys);
        compactIfNeeded();
    }

    @Nullable
    K pollDue(long gameTick) {
        discardStaleHead();
        var entry = entries.peek();
        if (entry == null || entry.dueTick() > gameTick) {
            return null;
        }
        entries.poll();
        schedules.remove(entry.key());
        return entry.key();
    }

    long nextDueTick() {
        discardStaleHead();
        var entry = entries.peek();
        return entry != null ? entry.dueTick() : Long.MAX_VALUE;
    }

    int size() {
        return schedules.size();
    }

    void clear() {
        schedules.clear();
        entries.clear();
    }

    private void discardStaleHead() {
        while (!entries.isEmpty() && !isLive(entries.peek())) {
            entries.poll();
        }
    }

    private boolean isLive(Entry<K> entry) {
        var schedule = schedules.get(entry.key());
        return schedule != null
                && schedule.token() == entry.token()
                && schedule.dueTick() == entry.dueTick();
    }

    private void compactIfNeeded() {
        int liveCount = schedules.size();
        int threshold = Math.max(MIN_COMPACTION_SIZE, liveCount * COMPACTION_MULTIPLIER);
        if (entries.size() <= threshold) {
            return;
        }

        entries.clear();
        for (var scheduled : schedules.entrySet()) {
            var value = scheduled.getValue();
            entries.add(new Entry<>(
                    scheduled.getKey(), value.dueTick(), value.token(), nextSequence++));
        }
    }
}
