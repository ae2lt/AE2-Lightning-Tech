package com.moakiee.ae2lt.logic.tianshu.loop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Persistent logical pattern storage provided by Tianshu closed-loop pattern warehouses. */
public final class ClosedLoopPatternRepository {
    private static final String TAG_PATTERNS = "Patterns";
    private final IntSupplier capacity;
    private final LinkedHashMap<UUID, ClosedLoopPatternPayload> patterns = new LinkedHashMap<>();

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
        return List.copyOf(patterns.values());
    }

    public ClosedLoopPatternPayload get(UUID id) {
        return id == null ? null : patterns.get(id);
    }

    public PutResult put(ClosedLoopPatternPayload payload) {
        if (payload == null) return PutResult.INVALID;
        var previous = patterns.get(payload.patternId());
        if (previous != null) {
            if (payload.version() < previous.version()) return PutResult.STALE_VERSION;
            patterns.put(payload.patternId(), payload);
            return PutResult.UPDATED;
        }
        if (capacity() <= 0) return PutResult.UNAVAILABLE;
        if (patterns.size() >= capacity()) return PutResult.FULL;
        patterns.put(payload.patternId(), payload);
        return PutResult.ADDED;
    }

    public boolean remove(UUID id) {
        return id != null && patterns.remove(id) != null;
    }

    public void writeTo(CompoundTag parent, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var pattern : patterns.values()) {
            list.add(ClosedLoopPatternPayloadTagCodec.write(pattern, registries));
        }
        parent.put(TAG_PATTERNS, list);
    }

    public void readFrom(CompoundTag parent, HolderLookup.Provider registries) {
        patterns.clear();
        var list = parent.getList(TAG_PATTERNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                var payload = ClosedLoopPatternPayloadTagCodec.read(list.getCompound(i), registries);
                patterns.put(payload.patternId(), payload);
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
        for (var pattern : patterns.values()) {
            if (index++ >= keep) overflow.add(pattern);
        }
        return List.copyOf(overflow);
    }

    public enum PutResult {
        ADDED,
        UPDATED,
        FULL,
        UNAVAILABLE,
        STALE_VERSION,
        INVALID
    }
}
