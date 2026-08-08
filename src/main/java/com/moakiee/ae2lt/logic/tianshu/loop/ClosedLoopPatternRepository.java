package com.moakiee.ae2lt.logic.tianshu.loop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Persistent logical pattern storage provided by Tianshu closed-loop pattern warehouses. */
public final class ClosedLoopPatternRepository {
    private static final String TAG_PATTERNS = "Patterns";
    private final IntSupplier capacity;
    private final ArrayList<ClosedLoopPatternPayload> patterns = new ArrayList<>();

    public ClosedLoopPatternRepository(IntSupplier capacity) {
        this.capacity = capacity;
    }

    public int capacity() {
        return Math.max(0, capacity.getAsInt());
    }

    public int size() {
        return patterns.size();
    }

    public List<ClosedLoopPatternPayload> patterns() {
        return List.copyOf(patterns);
    }

    /** Patterns currently backed by installed warehouse capacity. Overflow stays persisted. */
    public List<ClosedLoopPatternPayload> activePatterns() {
        return patterns.stream().limit(capacity()).toList();
    }

    public ClosedLoopPatternPayload get(int index) {
        return index >= 0 && index < patterns.size() ? patterns.get(index) : null;
    }

    public int indexOf(ClosedLoopPatternPayload payload) {
        if (payload == null) return -1;
        for (int i = 0; i < patterns.size(); i++) {
            if (patterns.get(i) == payload) return i;
        }
        return -1;
    }

    public PutResult add(ClosedLoopPatternPayload payload) {
        if (payload == null) return PutResult.INVALID;
        if (capacity() <= 0) return PutResult.UNAVAILABLE;
        if (patterns.size() >= capacity()) return PutResult.FULL;
        patterns.add(payload);
        return PutResult.ADDED;
    }

    public PutResult replace(
            ClosedLoopPatternPayload current, ClosedLoopPatternPayload replacement) {
        int index = indexOf(current);
        if (index < 0 || replacement == null) return PutResult.INVALID;
        patterns.set(index, replacement);
        return PutResult.UPDATED;
    }

    public boolean remove(ClosedLoopPatternPayload payload) {
        int index = indexOf(payload);
        if (index < 0) return false;
        patterns.remove(index);
        return true;
    }

    /** Replaces physical storage contents without applying normal insertion admission rules. */
    public void replaceAll(List<ClosedLoopPatternPayload> payloads) {
        patterns.clear();
        if (payloads == null) return;
        for (var payload : payloads) {
            if (payload != null && patterns.size() < capacity()) {
                patterns.add(payload);
            }
        }
    }

    public void clear() {
        patterns.clear();
    }

    public void writeTo(CompoundTag parent) {
        var list = new ListTag();
        for (var pattern : patterns) {
            list.add(ClosedLoopPatternPayloadTagCodec.write(pattern));
        }
        parent.put(TAG_PATTERNS, list);
    }

    public void readFrom(CompoundTag parent) {
        patterns.clear();
        var list = parent.getList(TAG_PATTERNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                var payload = ClosedLoopPatternPayloadTagCodec.read(list.getCompound(i));
                patterns.add(payload);
            } catch (RuntimeException ignored) {
                // Keep other stored patterns usable when one entry was damaged or came from an old format.
            }
        }
    }

    public List<ClosedLoopPatternPayload> overflowedPatterns() {
        int keep = capacity();
        if (patterns.size() <= keep) return List.of();
        var overflow = new ArrayList<ClosedLoopPatternPayload>(patterns.size() - keep);
        int index = 0;
        for (var pattern : patterns) {
            if (index++ >= keep) overflow.add(pattern);
        }
        return List.copyOf(overflow);
    }

    public enum PutResult {
        ADDED,
        UPDATED,
        FULL,
        UNAVAILABLE,
        INVALID
    }
}
