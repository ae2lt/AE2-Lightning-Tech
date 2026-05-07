package com.moakiee.ae2lt.me.cell;

import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import org.jetbrains.annotations.Nullable;

/**
 * Lightweight wrapper around a shared {@link IndexedStorage} cached in
 * {@link InfiniteCellSavedData}. Each wrapper owns its own {@link ByteTracker}
 * configured by the cell item's parameters, so different-capacity cells sharing
 * a UUID (edge case) each enforce their own limits independently.
 * <p>
 * A {@code modCount}-based {@link #ensureSync()} mechanism detects when another
 * wrapper has modified the shared storage and rebuilds the ByteTracker from
 * pre-aggregated per-{@link appeng.api.stacks.AEKeyType} data — O(keyTypes),
 * not O(totalKeys).
 */
public class InfiniteCellInventory implements StorageCell {

    private static final String TAG_CELL_ID = "ae2lt:cell_id";

    private final ItemStack stack;
    private final @Nullable HolderLookup.Provider explicitRegistries;
    private final @Nullable ISaveProvider saveProvider;
    private final IndexedStorage storage;
    private final ByteTracker byteTracker;
    private final double idleDrain;
    private UUID cellId;
    private long lastSyncModCount = -1;
    private int lastWrittenTypes = -1;
    private long lastWrittenBytes = -1;

    private InfiniteCellInventory(ItemStack stack, @Nullable HolderLookup.Provider registries,
                                  @Nullable ISaveProvider saveProvider,
                                  int bytesPerType, int maxTypes,
                                  long capacityLo, long capacityHi,
                                  double idleDrain) {
        this.stack = stack;
        this.explicitRegistries = registries;
        this.saveProvider = saveProvider;
        this.idleDrain = idleDrain;
        this.cellId = readCellId();

        var savedData = InfiniteCellSavedData.getOrNull();
        if (cellId != null && savedData != null) {
            this.storage = savedData.getOrCreateStorage(cellId, resolveRegistries());
        } else {
            this.storage = new IndexedStorage();
        }

        this.byteTracker = new ByteTracker(storage::getTotalTypes);
        byteTracker.configure(bytesPerType, maxTypes, capacityLo, capacityHi);
        syncByteTracker();
    }

    private void syncByteTracker() {
        byteTracker.rebuild(
                storage.getTypeAmountLo(), storage.getTypeAmountHi(),
                storage.getTypeCounts(), storage.getTotalTypes());
        lastSyncModCount = storage.getModCount();
    }

    private void ensureSync() {
        if (storage.getModCount() != lastSyncModCount) {
            syncByteTracker();
        }
    }

    private HolderLookup.Provider resolveRegistries() {
        if (explicitRegistries != null) return explicitRegistries;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        throw new IllegalStateException("No registries available — server not running");
    }

    public static InfiniteCellInventory create(ItemStack stack, @Nullable HolderLookup.Provider registries,
                                               @Nullable ISaveProvider saveProvider,
                                               int bytesPerType, int maxTypes,
                                               long capacityLo, long capacityHi,
                                               double idleDrain) {
        return new InfiniteCellInventory(stack, registries,
                saveProvider,
                bytesPerType, maxTypes, capacityLo, capacityHi, idleDrain);
    }

    public static InfiniteCellInventory create(ItemStack stack, @Nullable HolderLookup.Provider registries,
                                               int bytesPerType, int maxTypes,
                                               long capacity, double idleDrain) {
        return create(stack, registries, null, bytesPerType, maxTypes, capacity, 0, idleDrain);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MEStorage — insert / extract / getAvailableStacks
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0) return 0;
        ensureSync();

        boolean isNewKey = !storage.containsKey(what);
        long maxInsertable = byteTracker.computeMaxInsertable(what.getType(), isNewKey);
        if (maxInsertable <= 0) return 0;
        long toInsert = Math.min(amount, maxInsertable);

        if (mode == Actionable.SIMULATE) return toInsert;

        storage.insert(what, toInsert, Actionable.MODULATE);
        byteTracker.onInsert(what.getType(), toInsert, isNewKey);
        lastSyncModCount = storage.getModCount();
        syncSummary();
        markChanged();
        return toInsert;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0) return 0;
        ensureSync();

        if (mode == Actionable.SIMULATE) {
            return storage.extract(what, amount, Actionable.SIMULATE);
        }

        long taken = storage.extract(what, amount, Actionable.MODULATE);
        if (taken > 0) {
            boolean keyRemoved = !storage.containsKey(what);
            byteTracker.onExtract(what.getType(), taken, keyRemoved);
            lastSyncModCount = storage.getModCount();
            syncSummary();
            markChanged();
        }
        return taken;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        storage.getAvailableStacks(out);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return storage.containsKey(what);
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  StorageCell
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public CellState getStatus() {
        ensureSync();
        if (storage.getTotalTypes() == 0) return CellState.EMPTY;
        if (byteTracker.isFull()) return CellState.FULL;
        if (byteTracker.isTypeFull()) return CellState.TYPES_FULL;
        return CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return idleDrain;
    }

    @Override
    public boolean canFitInsideCell() {
        ensureSync();
        return storage.getTotalTypes() == 0;
    }

    @Override
    public void persist() {
        var savedData = InfiniteCellSavedData.getOrNull();
        if (savedData == null) return;

        if (storage.getTotalTypes() == 0) {
            if (storage.needsPersist()) {
                storage.persist(null, resolveRegistries());
            }
            if (cellId != null) {
                savedData.removeCell(cellId);
                clearCellId();
                cellId = null;
            }
            syncSummary();
            return;
        }

        if (!storage.needsPersist()) return;

        if (cellId == null) {
            cellId = UUID.randomUUID();
            writeCellId(cellId);
        }

        savedData.persistStorage(cellId, storage, resolveRegistries());
        ensureSync();
        syncSummary();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Public queries
    // ══════════════════════════════════════════════════════════════════════

    public long getUsedBytes() {
        ensureSync();
        return byteTracker.getUsedBytes();
    }

    public int getTotalTypes() {
        return storage.getTotalTypes();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UUID on ItemStack
    // ══════════════════════════════════════════════════════════════════════

    private @Nullable UUID readCellId() {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(TAG_CELL_ID)) return null;
        return tag.getUUID(TAG_CELL_ID);
    }

    private void writeCellId(UUID id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putUUID(TAG_CELL_ID, id));
    }

    private void clearCellId() {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.remove(TAG_CELL_ID));
    }

    private void markChanged() {
        if (storage.getTotalTypes() == 0) {
            persist();
        } else {
            var savedData = InfiniteCellSavedData.getOrNull();
            if (savedData != null) {
                ensureCellId();
                savedData.markStorageDirty(cellId, storage);
            }
        }

        if (saveProvider != null) {
            saveProvider.saveChanges();
        }
    }

    private void ensureCellId() {
        if (cellId != null) return;
        cellId = UUID.randomUUID();
        writeCellId(cellId);
    }

    private void syncSummary() {
        int t = storage.getTotalTypes();
        long b = byteTracker.getUsedBytes();
        if (t == lastWrittenTypes && b == lastWrittenBytes) return;
        lastWrittenTypes = t;
        lastWrittenBytes = b;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt("ae2lt:types", t);
            tag.putLong("ae2lt:bytes", b);
        });
    }
}
