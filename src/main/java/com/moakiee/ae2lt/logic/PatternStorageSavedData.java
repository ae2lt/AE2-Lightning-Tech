package com.moakiee.ae2lt.logic;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-level storage for pattern provider inventories that exceed 36 slots.
 * Each entry is keyed by {@code BlockPos.asLong()} (per-level, no dimension needed).
 */
public class PatternStorageSavedData extends SavedData {

    private static final String DATA_NAME = "ae2lt_patterns";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_POS = "Pos";
    private static final String TAG_SLOTS = "Slots";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_ITEM = "Item";

    private final Long2ObjectOpenHashMap<ItemStack[]> storage = new Long2ObjectOpenHashMap<>();

    public static final Factory<PatternStorageSavedData> FACTORY = new Factory<>(
            PatternStorageSavedData::new,
            PatternStorageSavedData::load,
            null
    );

    public PatternStorageSavedData() {
        super();
    }

    public static PatternStorageSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * Retrieve stored patterns for the given block position.
     *
     * @return the stored array (may be shorter/longer than {@code capacity}), or {@code null}
     */
    public ItemStack[] get(long pos) {
        return storage.get(pos);
    }

    /**
     * Store patterns for a block position. Only non-empty slots are persisted.
     */
    public void set(long pos, ItemStack[] patterns) {
        storage.put(pos, patterns);
        setDirty();
    }

    /**
     * Remove the entry for a block position (e.g. when block is broken).
     */
    public void remove(long pos) {
        if (storage.remove(pos) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        for (var entry : storage.entrySet()) {
            var entryTag = new CompoundTag();
            entryTag.putLong(TAG_POS, entry.getKey());

            var slotsTag = new ListTag();
            var patterns = entry.getValue();
            for (int i = 0; i < patterns.length; i++) {
                if (patterns[i] != null && !patterns[i].isEmpty()) {
                    var slotTag = new CompoundTag();
                    slotTag.putInt(TAG_SLOT, i);
                    slotTag.put(TAG_ITEM, patterns[i].save(registries));
                    slotsTag.add(slotTag);
                }
            }
            entryTag.put(TAG_SLOTS, slotsTag);
            entries.add(entryTag);
        }
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }

    private static PatternStorageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new PatternStorageSavedData();
        if (tag.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            var entries = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                var entryTag = entries.getCompound(i);
                long pos = entryTag.getLong(TAG_POS);

                var slotsTag = entryTag.getList(TAG_SLOTS, Tag.TAG_COMPOUND);
                int maxSlot = 0;
                for (int j = 0; j < slotsTag.size(); j++) {
                    maxSlot = Math.max(maxSlot, slotsTag.getCompound(j).getInt(TAG_SLOT) + 1);
                }
                var patterns = new ItemStack[maxSlot];
                for (int j = 0; j < maxSlot; j++) {
                    patterns[j] = ItemStack.EMPTY;
                }
                for (int j = 0; j < slotsTag.size(); j++) {
                    var slotTag = slotsTag.getCompound(j);
                    int slot = slotTag.getInt(TAG_SLOT);
                    patterns[slot] = ItemStack.parseOptional(registries, slotTag.getCompound(TAG_ITEM));
                }
                data.storage.put(pos, patterns);
            }
        }
        return data;
    }
}
