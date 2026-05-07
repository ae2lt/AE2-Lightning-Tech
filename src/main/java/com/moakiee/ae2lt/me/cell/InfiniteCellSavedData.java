package com.moakiee.ae2lt.me.cell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * World-level persistent storage for all infinite cell inventories.
 * Each cell is identified by a {@link UUID} stored on its {@link net.minecraft.world.item.ItemStack};
 * the actual entry data lives here, keeping ItemStack NBT minimal.
 * <p>
 * {@link IndexedStorage} instances are cached: the first access for a UUID
 * deserialises from NBT; subsequent accesses return the same instance.
 * Duplicate ItemStacks (same UUID) therefore share one storage object,
 * which naturally prevents item duplication exploits.
 */
public final class InfiniteCellSavedData extends SavedData {

    private static final String DATA_NAME = "ae2lt_infinite_cells";

    private final Map<UUID, CompoundTag> cells = new HashMap<>();
    private final transient Map<UUID, IndexedStorage> storageCache = new HashMap<>();

    public InfiniteCellSavedData() {}

    public static Factory<InfiniteCellSavedData> factory() {
        return new Factory<>(InfiniteCellSavedData::new, InfiniteCellSavedData::load);
    }

    public static InfiniteCellSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static @Nullable InfiniteCellSavedData getOrNull() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? get(server) : null;
    }

    // ── Storage cache ───────────────────────────────────────────────────

    /**
     * Returns the cached {@link IndexedStorage} for the given cell UUID,
     * loading from NBT on first access. Multiple callers with the same UUID
     * receive the same instance — this is the primary anti-duplication guard.
     */
    public IndexedStorage getOrCreateStorage(UUID id, HolderLookup.Provider registries) {
        IndexedStorage cached = storageCache.get(id);
        if (cached != null) return cached;

        var storage = new IndexedStorage();

        CompoundTag data = cells.get(id);
        if (data != null) {
            // IndexedStorage persists a split root (keys/lo/hi/totalTypes), not an "entries" list.
            // Loading unconditionally keeps the deserializer aligned with the current on-disk format.
            storage.load(data, registries);
        }

        storageCache.put(id, storage);
        return storage;
    }

    /**
     * Serialises the given storage to NBT under the specified UUID and marks dirty.
     * Re-registers the storage in the cache in case it was previously removed
     * (e.g. by {@link #removeCell(UUID)}) — the caller's wrapper may still hold
     * a live reference that must continue to persist.
     */
    public void persistStorage(UUID id, IndexedStorage storage, HolderLookup.Provider registries) {
        if (storage == null) return;
        storageCache.put(id, storage);
        CompoundTag lastRoot = cells.get(id);
        CompoundTag data = storage.persist(lastRoot, registries);
        cells.put(id, data);
        setDirty();
    }

    /**
     * Registers a live storage instance as changed without serialising it
     * immediately. This lets the normal world save path flush the cached storage
     * even if the hosting cell container only marks itself dirty.
     */
    public void markStorageDirty(UUID id, IndexedStorage storage) {
        if (id == null || storage == null) return;
        storageCache.put(id, storage);
        setDirty();
    }

    /**
     * Drops all data associated with the given cell UUID. Called when a cell
     * becomes empty so the world save file doesn't accumulate dead entries.
     */
    public void removeCell(UUID id) {
        boolean had = cells.remove(id) != null;
        storageCache.remove(id);
        if (had) setDirty();
    }

    // ── SavedData serialization ─────────────────────────────────────────

    private static InfiniteCellSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new InfiniteCellSavedData();
        CompoundTag cellsTag = tag.getCompound("cells");
        for (String key : cellsTag.getAllKeys()) {
            try {
                data.cells.put(UUID.fromString(key), cellsTag.getCompound(key));
            } catch (IllegalArgumentException ignored) {}
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (var entry : storageCache.entrySet()) {
            if (entry.getValue().needsPersist()) {
                CompoundTag lastRoot = cells.get(entry.getKey());
                CompoundTag data = entry.getValue().persist(lastRoot, registries);
                cells.put(entry.getKey(), data);
            }
        }

        CompoundTag cellsTag = new CompoundTag();
        for (var entry : cells.entrySet()) {
            cellsTag.put(entry.getKey().toString(), entry.getValue());
        }
        tag.put("cells", cellsTag);
        return tag;
    }
}
