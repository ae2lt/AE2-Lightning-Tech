package com.moakiee.ae2lt.me.cell;

import java.util.Arrays;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/**
 * Array-indexed storage engine for the infinite cell.
 * <p>
 * Each {@link AEKey} is assigned a stable integer id. The id doubles as
 * the position in the persisted {@link ListTag} / {@link LongArrayTag},
 * so incremental persist only touches changed positions.
 * <p>
 * A dirty queue ({@code dirtyQueue}) records which ids changed since the
 * last persist, giving O(changed) persist with no bitset scanning.
 * A per-id boolean {@code isStructDirty} distinguishes key add/remove
 * (needs key-tag write) from amount-only changes (just long[] writes).
 * <p>
 * When the number of free (hole) slots exceeds {@code totalTypes * COMPACT_THRESHOLD},
 * a deferred compaction is scheduled and executed at the next {@link #persist},
 * reassigning contiguous ids and forcing a full rewrite.
 */
final class IndexedStorage {

    private static final int INITIAL_CAPACITY = 256;
    private static final int COMPACT_THRESHOLD = 2;

    // Key registry — id is stable and doubles as ListTag position
    private final Object2IntOpenHashMap<AEKey> keyToId = new Object2IntOpenHashMap<>();
    private AEKey[] idToKey;
    private int nextId;
    private int[] freeIds;
    private int freeCount;

    // Amount arrays (63+63 bit)
    private long[] lo;
    private long[] hi;

    // Cached key serialization — set once per key lifetime, avoids repeated toTagGeneric
    private CompoundTag[] serializedKey;

    // Dirty tracking: queue + per-id flags (replaces bitset scanning)
    private int[] dirtyQueue;
    private int dirtyCount;
    private boolean[] inQueue;
    private boolean[] isStructDirty;

    private int totalTypes;
    private boolean needsPersist;
    private boolean needsCompact;
    private long modCount;

    // Per-AEKeyType aggregates — maintained incrementally
    private final Object2IntOpenHashMap<AEKeyType> typeCounts = new Object2IntOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKeyType> typeAmountLo = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKeyType> typeAmountHi = new Object2LongOpenHashMap<>();

    IndexedStorage() {
        keyToId.defaultReturnValue(-1);
        initArrays(INITIAL_CAPACITY);
    }

    int getTotalTypes() { return totalTypes; }

    boolean needsPersist() { return needsPersist; }

    long getModCount() { return modCount; }

    Object2LongOpenHashMap<AEKeyType> getTypeAmountLo() { return typeAmountLo; }
    Object2LongOpenHashMap<AEKeyType> getTypeAmountHi() { return typeAmountHi; }
    Object2IntOpenHashMap<AEKeyType> getTypeCounts() { return typeCounts; }

    private void enqueueDirty(int id) {
        if (!inQueue[id]) {
            inQueue[id] = true;
            if (dirtyCount == dirtyQueue.length) {
                dirtyQueue = Arrays.copyOf(dirtyQueue, dirtyQueue.length * 2);
            }
            dirtyQueue[dirtyCount++] = id;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  insert
    // ══════════════════════════════════════════════════════════════════════

    long insert(AEKey key, long amount, Actionable mode) {
        if (amount <= 0) return 0;
        if (mode == Actionable.SIMULATE) return amount;

        int id = keyToId.getInt(key);
        boolean isNewKey = (id == -1);
        if (isNewKey) {
            id = allocateId(key);
            totalTypes++;
        } else {
            enqueueDirty(id);
        }

        long newLo = lo[id] + amount;
        if (newLo < 0) { newLo &= Long.MAX_VALUE; hi[id]++; }
        lo[id] = newLo;

        AEKeyType kt = key.getType();
        if (isNewKey) typeCounts.addTo(kt, 1);
        long sumLo = typeAmountLo.getLong(kt) + amount;
        long sumHi = typeAmountHi.getLong(kt);
        if (sumLo < 0) { sumLo &= Long.MAX_VALUE; sumHi++; }
        typeAmountLo.put(kt, sumLo);
        typeAmountHi.put(kt, sumHi);

        needsPersist = true;
        modCount++;
        return amount;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  extract
    // ══════════════════════════════════════════════════════════════════════

    long extract(AEKey key, long amount, Actionable mode) {
        if (amount <= 0) return 0;

        int id = keyToId.getInt(key);
        if (id == -1) return 0;

        long curLo = lo[id], curHi = hi[id];
        long taken = DualLong126.geq(curHi, curLo, amount) ? amount : curLo;

        if (mode == Actionable.SIMULATE) return taken;

        long newLo = curLo - taken;
        if (newLo < 0) { newLo &= Long.MAX_VALUE; hi[id]--; }

        boolean keyRemoved = (newLo == 0 && hi[id] == 0);
        if (keyRemoved) {
            recycleId(id, key);
            totalTypes--;
            if (freeCount > Math.max(totalTypes, 1) * COMPACT_THRESHOLD) {
                needsCompact = true;
            }
        } else {
            lo[id] = newLo;
            enqueueDirty(id);
        }

        AEKeyType kt = key.getType();
        long sumLo = typeAmountLo.getLong(kt) - taken;
        long sumHi = typeAmountHi.getLong(kt);
        if (sumLo < 0) { sumLo &= Long.MAX_VALUE; sumHi--; }
        if (keyRemoved) {
            int remaining = typeCounts.addTo(kt, -1);
            if (remaining <= 0) {
                typeCounts.removeInt(kt);
                typeAmountLo.removeLong(kt);
                typeAmountHi.removeLong(kt);
            } else {
                typeAmountLo.put(kt, sumLo);
                typeAmountHi.put(kt, sumHi);
            }
        } else {
            typeAmountLo.put(kt, sumLo);
            typeAmountHi.put(kt, sumHi);
        }

        needsPersist = true;
        modCount++;
        return taken;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Queries
    // ══════════════════════════════════════════════════════════════════════

    void getAvailableStacks(KeyCounter out) {
        for (int id = 0; id < nextId; id++) {
            if (idToKey[id] != null) {
                out.add(idToKey[id], DualLong126.cap(hi[id], lo[id]));
            }
        }
    }

    boolean containsKey(AEKey key) {
        return keyToId.containsKey(key);
    }

    long getAmount(AEKey key) {
        int id = keyToId.getInt(key);
        if (id == -1) return 0;
        return DualLong126.cap(hi[id], lo[id]);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Persist — queue-driven, O(changed). Split layout:
    //  ListTag<CompoundTag> for keys, LongArrayTag for lo/hi.
    // ══════════════════════════════════════════════════════════════════════

    CompoundTag persist(@Nullable CompoundTag lastRoot, HolderLookup.Provider registries) {
        return persist(lastRoot, (key, reg) -> key.toTagGeneric(reg), registries);
    }

    CompoundTag persist(@Nullable CompoundTag lastRoot, KeySerializer keySerializer, HolderLookup.Provider registries) {
        if (needsCompact) {
            compact();
            lastRoot = null;
        }
        if (lastRoot == null) {
            return persistFull(keySerializer, registries);
        }

        ListTag keys = lastRoot.getList("keys", Tag.TAG_COMPOUND);
        long[] pLo = lastRoot.getLongArray("lo");
        long[] pHi = lastRoot.getLongArray("hi");

        int tagLen = alignPow2(nextId);
        if (pLo.length < nextId) {
            pLo = Arrays.copyOf(pLo, tagLen);
            pHi = Arrays.copyOf(pHi, tagLen);
            lastRoot.put("lo", new LongArrayTag(pLo));
            lastRoot.put("hi", new LongArrayTag(pHi));
        }
        while (keys.size() < nextId) {
            keys.add(new CompoundTag());
        }

        for (int i = 0; i < dirtyCount; i++) {
            int id = dirtyQueue[i];
            inQueue[id] = false;

            if (isStructDirty[id]) {
                isStructDirty[id] = false;
                if (idToKey[id] != null) {
                    if (serializedKey[id] == null) {
                        serializedKey[id] = keySerializer.toTag(idToKey[id], registries);
                    }
                    CompoundTag tag = new CompoundTag();
                    tag.put("key", serializedKey[id]);
                    keys.set(id, tag);
                } else {
                    keys.set(id, new CompoundTag());
                }
            }

            pLo[id] = lo[id];
            pHi[id] = hi[id];
        }
        dirtyCount = 0;

        lastRoot.putInt("totalTypes", totalTypes);
        needsPersist = false;
        return lastRoot;
    }

    private CompoundTag persistFull(KeySerializer keySerializer, HolderLookup.Provider registries) {
        // Clear dirty state — everything is being written
        for (int i = 0; i < dirtyCount; i++) {
            int id = dirtyQueue[i];
            inQueue[id] = false;
            isStructDirty[id] = false;
        }
        dirtyCount = 0;

        int tagLen = alignPow2(nextId);
        ListTag keys = new ListTag();
        long[] pLo = new long[tagLen];
        long[] pHi = new long[tagLen];

        for (int id = 0; id < nextId; id++) {
            if (idToKey[id] != null) {
                if (serializedKey[id] == null) {
                    serializedKey[id] = keySerializer.toTag(idToKey[id], registries);
                }
                CompoundTag tag = new CompoundTag();
                tag.put("key", serializedKey[id]);
                keys.add(tag);
                pLo[id] = lo[id];
                pHi[id] = hi[id];
            } else {
                keys.add(new CompoundTag());
            }
        }

        CompoundTag root = new CompoundTag();
        root.put("keys", keys);
        root.put("lo", new LongArrayTag(pLo));
        root.put("hi", new LongArrayTag(pHi));
        root.putInt("totalTypes", totalTypes);
        needsPersist = false;
        return root;
    }

    private void compact() {
        int newCap = alignPow2(Math.max(totalTypes, 1));
        int newNext = 0;

        AEKey[] nKey = new AEKey[newCap];
        long[] nLo = new long[newCap];
        long[] nHi = new long[newCap];
        CompoundTag[] nSer = new CompoundTag[newCap];

        for (int old = 0; old < nextId; old++) {
            if (idToKey[old] == null) continue;
            int nid = newNext++;
            nKey[nid] = idToKey[old];
            nLo[nid] = lo[old];
            nHi[nid] = hi[old];
            nSer[nid] = serializedKey[old];
            keyToId.put(idToKey[old], nid);
        }

        idToKey = nKey;
        lo = nLo;
        hi = nHi;
        serializedKey = nSer;
        inQueue = new boolean[newCap];
        isStructDirty = new boolean[newCap];
        dirtyQueue = new int[Math.max(64, newNext)];
        dirtyCount = 0;
        nextId = newNext;
        freeIds = new int[64];
        freeCount = 0;
        needsCompact = false;
    }

    private static int alignPow2(int n) {
        if (n <= INITIAL_CAPACITY) return INITIAL_CAPACITY;
        return Integer.highestOneBit(n - 1) << 1;
    }

    @FunctionalInterface
    interface KeySerializer {
        CompoundTag toTag(AEKey key, HolderLookup.Provider registries);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Load
    // ══════════════════════════════════════════════════════════════════════

    void load(CompoundTag root, HolderLookup.Provider registries) {
        keyToId.clear();
        nextId = 0;
        freeCount = 0;
        totalTypes = 0;
        dirtyCount = 0;
        typeCounts.clear();
        typeAmountLo.clear();
        typeAmountHi.clear();

        ListTag keys = root.getList("keys", Tag.TAG_COMPOUND);
        long[] pLo = root.getLongArray("lo");
        long[] pHi = root.getLongArray("hi");
        int size = keys.size();
        ensureCapacity(size);
        nextId = size;

        for (int id = 0; id < size; id++) {
            inQueue[id] = false;
            isStructDirty[id] = false;

            CompoundTag entry = keys.getCompound(id);
            if (!entry.contains("key")) {
                addFree(id);
                continue;
            }
            AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound("key"));
            if (key == null) {
                addFree(id);
                continue;
            }

            keyToId.put(key, id);
            idToKey[id] = key;
            lo[id] = id < pLo.length ? pLo[id] : 0L;
            hi[id] = id < pHi.length ? pHi[id] : 0L;
            serializedKey[id] = entry.getCompound("key");
            totalTypes++;

            AEKeyType kt = key.getType();
            typeCounts.addTo(kt, 1);
            long sumLo = typeAmountLo.getLong(kt) + lo[id];
            long sumHi = typeAmountHi.getLong(kt) + hi[id];
            if (sumLo < 0) { sumLo &= Long.MAX_VALUE; sumHi++; }
            typeAmountLo.put(kt, sumLo);
            typeAmountHi.put(kt, sumHi);
        }

        if (freeCount > Math.max(totalTypes, 1) * COMPACT_THRESHOLD) {
            needsCompact = true;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ID lifecycle — stable: id = ListTag position
    // ══════════════════════════════════════════════════════════════════════

    private int allocateId(AEKey key) {
        int id;
        if (freeCount > 0) {
            id = freeIds[--freeCount];
        } else {
            id = nextId++;
            ensureCapacity(id);
        }
        keyToId.put(key, id);
        idToKey[id] = key;
        lo[id] = 0;
        hi[id] = 0;
        serializedKey[id] = null;
        isStructDirty[id] = true;
        enqueueDirty(id);
        return id;
    }

    private void recycleId(int id, AEKey key) {
        keyToId.removeInt(key);
        idToKey[id] = null;
        lo[id] = 0;
        hi[id] = 0;
        serializedKey[id] = null;
        isStructDirty[id] = true;
        enqueueDirty(id);
        addFree(id);
    }

    private void addFree(int id) {
        if (freeCount == freeIds.length) {
            freeIds = Arrays.copyOf(freeIds, freeIds.length * 2);
        }
        freeIds[freeCount++] = id;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Capacity
    // ══════════════════════════════════════════════════════════════════════

    private void ensureCapacity(int required) {
        if (required < lo.length) return;
        int newCap = Math.max(INITIAL_CAPACITY, Integer.highestOneBit(required) << 1);
        lo = Arrays.copyOf(lo, newCap);
        hi = Arrays.copyOf(hi, newCap);
        idToKey = Arrays.copyOf(idToKey, newCap);
        serializedKey = Arrays.copyOf(serializedKey, newCap);
        inQueue = Arrays.copyOf(inQueue, newCap);
        isStructDirty = Arrays.copyOf(isStructDirty, newCap);
    }

    private void initArrays(int capacity) {
        lo = new long[capacity];
        hi = new long[capacity];
        idToKey = new AEKey[capacity];
        serializedKey = new CompoundTag[capacity];
        inQueue = new boolean[capacity];
        isStructDirty = new boolean[capacity];
        dirtyQueue = new int[64];
        freeIds = new int[64];
    }
}
