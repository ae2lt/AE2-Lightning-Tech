package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Type-limited, amount-unbounded seed storage owned by one Tianshu port. */
public final class TianshuSeedStorage {
    private static final String TAG_CONTENTS = "Contents";
    private final IntSupplier typeCapacity;
    private final LinkedHashMap<AEKey, Long> contents = new LinkedHashMap<>();

    public TianshuSeedStorage(IntSupplier typeCapacity) {
        this.typeCapacity = typeCapacity;
    }

    public int typeCapacity() { return Math.max(0, typeCapacity.getAsInt()); }
    public int storedTypes() { return contents.size(); }
    public long amount(AEKey key) { return key == null ? 0L : contents.getOrDefault(key, 0L); }

    public List<GenericStack> contents() {
        var result = new ArrayList<GenericStack>(contents.size());
        for (var entry : contents.entrySet()) result.add(new GenericStack(entry.getKey(), entry.getValue()));
        return List.copyOf(result);
    }

    public long insert(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0) return 0L;
        if (!contents.containsKey(key) && contents.size() >= typeCapacity()) return 0L;
        long current = contents.getOrDefault(key, 0L);
        long accepted = Math.min(amount, Long.MAX_VALUE - current);
        if (accepted > 0 && mode == Actionable.MODULATE) contents.put(key, current + accepted);
        return accepted;
    }

    public long extract(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0) return 0L;
        long extracted = Math.min(amount, contents.getOrDefault(key, 0L));
        if (extracted > 0 && mode == Actionable.MODULATE) {
            long remaining = contents.get(key) - extracted;
            if (remaining > 0) contents.put(key, remaining); else contents.remove(key);
        }
        return extracted;
    }

    public void writeTo(CompoundTag parent, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var entry : contents.entrySet()) {
            list.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getValue())));
        }
        parent.put(TAG_CONTENTS, list);
    }

    public void readFrom(CompoundTag parent, HolderLookup.Provider registries) {
        contents.clear();
        var list = parent.getList(TAG_CONTENTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var stack = GenericStack.readTag(registries, list.getCompound(i));
            if (stack != null && stack.amount() > 0) {
                contents.merge(stack.what(), stack.amount(), TianshuSeedStorage::saturatingAdd);
            }
        }
    }

    private static long saturatingAdd(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
