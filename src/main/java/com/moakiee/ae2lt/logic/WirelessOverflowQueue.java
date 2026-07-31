package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;

/** Wireless overflow ownership, retry deadlines and compact pattern references. */
final class WirelessOverflowQueue {
    private static final int MAX_BUCKETS = 1024;
    private static final int REARM_BUCKETS = 768;
    private static final int RETRY_MIN = 5;
    private static final int RETRY_MAX = 20;
    private static final int RETRY_STEP = 5;

    enum OverflowAttemptResult {
        CLEARED(true, false, true),
        PROGRESSED(false, true, true),
        BLOCKED(false, true, false);

        private final boolean removeBucket;
        private final boolean reschedule;
        private final boolean persistentStateChanged;

        OverflowAttemptResult(
                boolean removeBucket,
                boolean reschedule,
                boolean persistentStateChanged) {
            this.removeBucket = removeBucket;
            this.reschedule = reschedule;
            this.persistentStateChanged = persistentStateChanged;
        }

        boolean removeBucket() {
            return removeBucket;
        }

        boolean reschedule() {
            return reschedule;
        }

        boolean persistentStateChanged() {
            return persistentStateChanged;
        }
    }

    /** Strong owners, including removed orphan connections with pending work. */
    private final Set<WirelessConnection> owners = new HashSet<>();
    private final DueTaskQueue<WirelessConnection> retries = new DueTaskQueue<>();
    private final WirelessOverflowPatternTable patterns =
            new WirelessOverflowPatternTable();
    private long lastFlushTick = Long.MIN_VALUE;
    private boolean backpressured;

    boolean isEmpty() {
        return owners.isEmpty();
    }

    boolean contains(WirelessConnection connection) {
        return owners.contains(connection);
    }

    int size() {
        return owners.size();
    }

    Set<WirelessConnection> connections() {
        return Set.copyOf(owners);
    }

    Iterable<Bucket> buckets() {
        var result = new ArrayList<Bucket>(owners.size());
        for (var owner : owners) {
            var bucket = owner.wirelessOverflow();
            if (bucket != null) {
                result.add(bucket);
            }
        }
        return result;
    }

    @Nullable
    Bucket get(WirelessConnection connection) {
        var owner = canonical(connection);
        return owner == null ? null : owner.wirelessOverflow();
    }

    /**
     * Returns the existing overflow-owned instance for an equal complete
     * address. This runs only during load/topology refresh, never per push.
     */
    WirelessConnection adopt(WirelessConnection connection) {
        var owner = canonical(connection);
        return owner == null ? connection : owner;
    }

    boolean isBackpressured() {
        return backpressured;
    }

    Bucket store(
            WirelessConnection connection,
            IPatternDetails pattern,
            List<GenericStack> overflow,
            boolean forceFallback,
            long gameTick) {
        short patternId = patterns.intern(pattern, buckets());
        Bucket bucket;
        if (!forceFallback
                && WirelessOverflowPatternTable.isCompactEligible(pattern)) {
            var inputs = pattern.getInputs();
            var first = overflow.get(0);
            int stuckIndex = WirelessOverflowPatternTable.findSlotIndex(
                    inputs, first.what());
            if (stuckIndex >= 0
                    && WirelessOverflowPatternTable.verifySequentialOverflow(
                            inputs, stuckIndex, overflow)) {
                bucket = Bucket.compact(
                        patternId, (short) stuckIndex, first.amount());
            } else {
                bucket = Bucket.fallback(patternId, overflow);
            }
        } else {
            bucket = Bucket.fallback(patternId, overflow);
        }
        put(connection, bucket, gameTick);
        return bucket;
    }

    Bucket storeRouted(
            WirelessConnection connection,
            IPatternDetails pattern,
            List<RoutedPatternOverflow.Entry> overflow,
            long gameTick) {
        var bucket = Bucket.routedFallback(
                patterns.intern(pattern, buckets()), overflow);
        put(connection, bucket, gameTick);
        return bucket;
    }

    void restoreBucket(
            WirelessConnection connection, Bucket bucket, long gameTick) {
        put(connection, bucket, gameTick);
    }

    @Nullable
    Bucket remove(WirelessConnection connection) {
        var owner = canonical(connection);
        if (owner == null) {
            return null;
        }
        retries.remove(owner);
        owners.remove(owner);
        var removed = owner.wirelessOverflow();
        owner.setWirelessOverflow(null);
        refreshBackpressure();
        return removed;
    }

    boolean beginFlush(long gameTick) {
        if (owners.isEmpty() || lastFlushTick == gameTick) {
            return false;
        }
        lastFlushTick = gameTick;
        return true;
    }

    @Nullable
    WirelessConnection pollDue(long gameTick) {
        return retries.pollDue(gameTick);
    }

    void rescheduleBlocked(
            WirelessConnection connection, Bucket bucket, long gameTick) {
        bucket.retryDelay = nextRetryDelay(
                bucket.retryDelay, OverflowAttemptResult.BLOCKED);
        schedule(connection, bucket, gameTick + bucket.retryDelay);
    }

    void reschedule(
            WirelessConnection connection,
            Bucket bucket,
            long gameTick,
            OverflowAttemptResult result) {
        bucket.retryDelay = nextRetryDelay(
                bucket.retryDelay, result);
        schedule(connection, bucket, gameTick + bucket.retryDelay);
    }

    long nextDueTick() {
        return retries.nextDueTick();
    }

    @Nullable
    IPatternDetails pattern(int unsignedId) {
        return patterns.get(unsignedId);
    }

    void restorePattern(int id, IPatternDetails pattern) {
        patterns.restore(id, pattern);
    }

    void clear() {
        for (var owner : owners) {
            owner.setWirelessOverflow(null);
        }
        owners.clear();
        retries.clear();
        patterns.clear();
        lastFlushTick = Long.MIN_VALUE;
        backpressured = false;
    }

    void refreshBackpressure() {
        int total = owners.size();
        if (backpressured) {
            if (total <= REARM_BUCKETS) {
                backpressured = false;
            }
        } else if (total >= MAX_BUCKETS) {
            backpressured = true;
        }
    }

    private void put(
            WirelessConnection connection, Bucket bucket, long gameTick) {
        var owner = adopt(connection);
        owner.setWirelessOverflow(bucket);
        owners.add(owner);
        bucket.retryDelay = initialRetryDelay();
        schedule(connection, bucket, gameTick + bucket.retryDelay);
        refreshBackpressure();
    }

    private void schedule(
            WirelessConnection connection, Bucket bucket, long dueTick) {
        var owner = canonical(connection);
        if (owner != null && owner.wirelessOverflow() == bucket) {
            retries.schedule(owner, dueTick);
        }
    }

    @Nullable
    private WirelessConnection canonical(WirelessConnection connection) {
        for (var owner : owners) {
            if (owner.equals(connection)) {
                return owner;
            }
        }
        return null;
    }

    static final class Bucket
            implements WirelessOverflowPatternTable.PatternReference {
        final boolean compactMode;
        short patternId;
        short stuckIndex;
        long remaining;
        final RoutedPatternOverflow fallback;
        int retryDelay = initialRetryDelay();

        private Bucket(
                boolean compactMode,
                short patternId,
                short stuckIndex,
                long remaining,
                RoutedPatternOverflow fallback) {
            this.compactMode = compactMode;
            this.patternId = patternId;
            this.stuckIndex = stuckIndex;
            this.remaining = remaining;
            this.fallback = fallback;
        }

        static Bucket compact(
                short patternId, short stuckIndex, long remaining) {
            return new Bucket(
                    true,
                    patternId,
                    stuckIndex,
                    remaining,
                    RoutedPatternOverflow.unrouted(List.of()));
        }

        static Bucket fallback(
                short patternId, List<GenericStack> overflow) {
            return new Bucket(
                    false,
                    patternId,
                    (short) 0,
                    0L,
                    RoutedPatternOverflow.unrouted(overflow));
        }

        static Bucket routedFallback(
                short patternId, List<RoutedPatternOverflow.Entry> overflow) {
            return new Bucket(
                    false,
                    patternId,
                    (short) 0,
                    0L,
                    RoutedPatternOverflow.routed(overflow));
        }

        @Override
        public boolean usesPatternDefinition() {
            return compactMode;
        }

        @Override
        public int unsignedPatternId() {
            return Short.toUnsignedInt(patternId);
        }

        @Override
        public void setPatternId(short patternId) {
            this.patternId = patternId;
        }
    }

    static int initialRetryDelay() {
        return RETRY_MIN;
    }

    static int nextRetryDelay(
            int currentDelay, OverflowAttemptResult result) {
        if (result == OverflowAttemptResult.PROGRESSED) {
            return RETRY_MIN;
        }
        if (result == OverflowAttemptResult.CLEARED) {
            return 0;
        }
        int normalized = Math.clamp(currentDelay, RETRY_MIN, RETRY_MAX);
        return Math.min(RETRY_MAX, normalized + RETRY_STEP);
    }
}
