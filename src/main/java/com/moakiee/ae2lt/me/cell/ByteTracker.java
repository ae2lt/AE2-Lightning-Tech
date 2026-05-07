package com.moakiee.ae2lt.me.cell;

import java.util.function.IntSupplier;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/**
 * Tracks byte-level capacity consumption for the infinite cell using
 * remainder-delta incremental updates keyed on {@link AEKeyType}.
 * <p>
 * Formula: {@code usedBytes = uniqueKeys × bytesPerType + Σ_per_type ceil(typeTotal / apb)}
 * <p>
 * Per-{@link AEKeyType} aggregation reduces the number of tracked remainders
 * from O(keys) to O(keyTypes) (typically 2–3). All arithmetic uses signed
 * 63-bit longs; zero {@code Long.xxxUnsigned} calls.
 */
final class ByteTracker {

    private final Object2LongOpenHashMap<AEKeyType> keyTypeRemainders = new Object2LongOpenHashMap<>();
    private final Object2IntOpenHashMap<AEKeyType> keyTypeCounts = new Object2IntOpenHashMap<>();

    private long usedBytesLo;
    private long usedBytesHi;

    private long capacityLo;
    private long capacityHi;
    private int bytesPerType;
    private int maxTypes;
    private final IntSupplier totalTypesGetter;

    ByteTracker(IntSupplier totalTypesGetter) {
        this.totalTypesGetter = totalTypesGetter;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Configuration
    // ══════════════════════════════════════════════════════════════════════

    void configure(int bytesPerType, int maxTypes, long capacityLo, long capacityHi) {
        this.bytesPerType = bytesPerType;
        this.maxTypes = maxTypes;
        this.capacityLo = capacityLo;
        this.capacityHi = capacityHi;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Hot-path: capacity check
    // ══════════════════════════════════════════════════════════════════════

    long computeMaxInsertable(AEKeyType type, boolean isNewKey) {
        int apb = type.getAmountPerByte();

        if (capacityHi == usedBytesHi) {
            if (capacityLo <= usedBytesLo) return 0;
            return maxFromFreeBytes(capacityLo - usedBytesLo, apb, type, isNewKey);
        }
        if (capacityHi < usedBytesHi) return 0;
        return Long.MAX_VALUE;
    }

    private long maxFromFreeBytes(long freeBytes, int apb, AEKeyType type, boolean isNewKey) {
        int totalTypes = totalTypesGetter.getAsInt();
        if (isNewKey) {
            if (totalTypes >= maxTypes) return 0;
            if (freeBytes < bytesPerType) return 0;
            freeBytes -= bytesPerType;
        }
        long r = keyTypeRemainders.getLong(type);
        long freeInPartial = (r > 0) ? (apb - r) : 0;
        if (freeBytes > Long.MAX_VALUE / apb) return Long.MAX_VALUE;
        long result = freeBytes * apb + freeInPartial;
        return result < 0 ? Long.MAX_VALUE : result;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Hot-path: insert delta
    // ══════════════════════════════════════════════════════════════════════

    void onInsert(AEKeyType type, long amount, boolean isNewKey) {
        int apb = type.getAmountPerByte();

        long oldR = keyTypeRemainders.getLong(type);
        long aMod = amount % apb;
        long combined = oldR + aMod;
        long extra = (combined >= apb) ? 1L : 0L;
        long newR = extra > 0 ? combined - apb : combined;
        keyTypeRemainders.put(type, newR);

        long byteDelta = amount / apb + extra
                + (newR > 0 ? 1L : 0L) - (oldR > 0 ? 1L : 0L);

        if (isNewKey) {
            byteDelta += bytesPerType;
            keyTypeCounts.addTo(type, 1);
        }

        usedBytesLo += byteDelta;
        if (usedBytesLo < 0) { usedBytesLo &= Long.MAX_VALUE; usedBytesHi++; }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Hot-path: extract delta
    // ══════════════════════════════════════════════════════════════════════

    void onExtract(AEKeyType type, long amount, boolean keyRemoved) {
        int apb = type.getAmountPerByte();

        long oldR = keyTypeRemainders.getLong(type);
        long aMod = amount % apb;
        long newR, extra;
        if (oldR >= aMod) {
            newR = oldR - aMod;
            extra = 0;
        } else {
            newR = oldR + apb - aMod;
            extra = 1;
        }

        long bytesFreed = amount / apb + extra
                + (oldR > 0 ? 1L : 0L) - (newR > 0 ? 1L : 0L);

        if (keyRemoved) {
            bytesFreed += bytesPerType;
            int remaining = keyTypeCounts.addTo(type, -1);
            if (remaining <= 0) {
                keyTypeCounts.removeInt(type);
                keyTypeRemainders.removeLong(type);
            } else {
                keyTypeRemainders.put(type, newR);
            }
        } else {
            keyTypeRemainders.put(type, newR);
        }

        usedBytesLo -= bytesFreed;
        if (usedBytesLo < 0) { usedBytesLo &= Long.MAX_VALUE; usedBytesHi--; }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Queries
    // ══════════════════════════════════════════════════════════════════════

    long getUsedBytes() {
        return DualLong126.cap(usedBytesHi, usedBytesLo);
    }

    long getUsedBytesHi() { return usedBytesHi; }

    long getUsedBytesLo() { return usedBytesLo; }

    boolean isFull() {
        if (capacityHi == usedBytesHi) return capacityLo <= usedBytesLo;
        return capacityHi < usedBytesHi;
    }

    boolean isTypeFull() {
        return totalTypesGetter.getAsInt() >= maxTypes;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Cold-path: full rebuild from pre-aggregated per-type data — O(keyTypes)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Rebuild byte tracking state from per-{@link AEKeyType} aggregates
     * already maintained by {@link IndexedStorage}.
     *
     * @param ktLo   per-type total amount (lo half, 63-bit unsigned)
     * @param ktHi   per-type total amount (hi half, 63-bit unsigned)
     * @param ktCounts per-type unique key count
     * @param totalKeys total number of unique keys across all types
     */
    void rebuild(Object2LongOpenHashMap<AEKeyType> ktLo,
                 Object2LongOpenHashMap<AEKeyType> ktHi,
                 Object2IntOpenHashMap<AEKeyType> ktCounts,
                 int totalKeys) {
        keyTypeRemainders.clear();
        keyTypeCounts.clear();
        usedBytesLo = 0;
        usedBytesHi = 0;

        keyTypeCounts.putAll(ktCounts);

        long[] divBuf = new long[2];
        for (var kt : ktLo.keySet()) {
            long tLo = ktLo.getLong(kt);
            long tHi = ktHi.getLong(kt);
            int apb = kt.getAmountPerByte();

            long remainder = (tHi == 0) ? tLo % apb
                                        : DualLong126.mod126(tHi, tLo, apb);
            keyTypeRemainders.put(kt, remainder);

            DualLong126.ceilDiv126(tHi, tLo, apb, divBuf);
            usedBytesLo += divBuf[1];
            if (usedBytesLo < 0) { usedBytesLo &= Long.MAX_VALUE; usedBytesHi++; }
            usedBytesHi += divBuf[0];
        }

        usedBytesLo += (long) totalKeys * bytesPerType;
        if (usedBytesLo < 0) { usedBytesLo &= Long.MAX_VALUE; usedBytesHi++; }
    }
}
