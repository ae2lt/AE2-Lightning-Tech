package com.moakiee.ae2lt.logic.tianshu.maintenance;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.function.IntSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class ReservedStockRepository {
    public static final long INFINITE = -1L;
    private static final String TAG_ENTRIES = "Entries";
    private final IntSupplier capacity;
    private final LinkedHashMap<AEKey, Entry> reserves = new LinkedHashMap<>();

    public ReservedStockRepository(IntSupplier capacity) { this.capacity = capacity; }
    public int capacity() { return Math.max(0, capacity.getAsInt()); }
    public int size() { return reserves.size(); }
    public long reserve(AEKey key) {
        var entry = reservationFor(key);
        return entry != null ? entry.amount() : 0L;
    }
    public ReservedStockMatchMode matchMode(AEKey key) {
        var entry = reservationFor(key);
        return entry != null ? entry.mode() : ReservedStockMatchMode.EXACT;
    }
    public Map<AEKey, Long> entries() {
        var result = new LinkedHashMap<AEKey, Long>();
        reserves.forEach((key, entry) -> result.put(key, entry.amount()));
        return Map.copyOf(result);
    }
    public List<Entry> reservations() { return List.copyOf(reserves.values()); }
    /** Bounded recovery view; oversized legacy entries remain individually removable. */
    public List<Entry> reservations(int limit) {
        if (limit <= 0) return List.of();
        return reserves.values().stream().limit(limit).toList();
    }

    public PutResult set(AEKey key, long amount) {
        return set(key, ReservedStockMatchMode.EXACT, amount);
    }

    public PutResult set(AEKey key, ReservedStockMatchMode mode, long amount) {
        if (key == null || amount < INFINITE) return PutResult.INVALID;
        if (mode == null) return PutResult.INVALID;
        if (amount == 0) {
            boolean removed = reserves.remove(key) != null;
            if (mode == ReservedStockMatchMode.IGNORE_SECONDARY) {
                removed |= reserves.entrySet().removeIf(entry -> entry.getValue().mode() == mode
                        && sameSecondaryGroup(entry.getKey(), key));
            }
            return removed ? PutResult.REMOVED : PutResult.REMOVED;
        }
        // Preserve oversized legacy state as read-only; amount=0 above remains available
        // so players can reduce it back under the current hard limit.
        if (reserves.size() > capacity()) return PutResult.FULL;
        AEKey storageKey = key;
        if (mode == ReservedStockMatchMode.IGNORE_SECONDARY) {
            for (var existing : reserves.entrySet()) {
                if (existing.getValue().mode() == mode && sameSecondaryGroup(existing.getKey(), key)) {
                    storageKey = existing.getKey();
                    break;
                }
            }
        }
        if (!reserves.containsKey(storageKey) && reserves.size() >= capacity()) {
            return capacity() <= 0 ? PutResult.UNAVAILABLE : PutResult.FULL;
        }
        boolean update = reserves.containsKey(storageKey);
        reserves.put(storageKey, new Entry(storageKey, amount, mode));
        return update ? PutResult.UPDATED : PutResult.ADDED;
    }

    public long usablePreexistingStock(AEKey key, long snapshotAmount) {
        long stock = Math.max(0L, snapshotAmount);
        var reservation = reservationFor(key);
        if (reservation != null && reservation.mode() == ReservedStockMatchMode.IGNORE_SECONDARY) {
            return usablePreexistingStock(key, stock, Map.of(key, stock));
        }
        long reserve = reservation != null ? reservation.amount() : 0L;
        if (reserve == INFINITE) return 0L;
        if (reserve <= 0) return stock;
        return Math.max(0L, stock - reserve);
    }

    public boolean groupsSecondaryVariants(AEKey key) {
        var reservation = reservationFor(key);
        return reservation != null && reservation.mode() == ReservedStockMatchMode.IGNORE_SECONDARY;
    }

    public long usablePreexistingStock(
            AEKey exactVariant, long exactAmount, Map<AEKey, Long> groupSnapshot) {
        var reservation = reservationFor(exactVariant);
        if (reservation == null || reservation.mode() != ReservedStockMatchMode.IGNORE_SECONDARY) {
            return usablePreexistingStock(exactVariant, exactAmount);
        }
        long total = 0L;
        for (var entry : groupSnapshot.entrySet()) {
            if (belongsToGroupReservation(reservation, entry.getKey())) {
                total = saturatingAdd(total, Math.max(0L, entry.getValue()));
            }
        }
        long usableTotal = reservation.amount() == INFINITE
                ? 0L : Math.max(0L, total - reservation.amount());
        var variants = groupSnapshot.entrySet().stream()
                .filter(entry -> belongsToGroupReservation(reservation, entry.getKey()))
                .sorted(Comparator.comparing(entry -> stableKey(entry.getKey())))
                .toList();
        long remaining = usableTotal;
        for (var variant : variants) {
            long usable = Math.min(Math.max(0L, variant.getValue()), remaining);
            if (variant.getKey().equals(exactVariant)) return usable;
            remaining -= usable;
        }
        return 0L;
    }

    public void writeTo(CompoundTag parent, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var entry : reserves.values()) {
            var tag = GenericStack.writeTag(registries, new GenericStack(entry.key(), 1));
            tag.putLong("Reserve", entry.amount());
            tag.putString("Mode", entry.mode().name());
            list.add(tag);
        }
        parent.put(TAG_ENTRIES, list);
    }

    public void readFrom(CompoundTag parent, HolderLookup.Provider registries) {
        reserves.clear();
        var list = parent.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var tag = list.getCompound(i);
            var stack = GenericStack.readTag(registries, tag);
            long amount = tag.getLong("Reserve");
            ReservedStockMatchMode mode;
            try { mode = ReservedStockMatchMode.valueOf(tag.getString("Mode")); }
            catch (IllegalArgumentException ignored) { mode = ReservedStockMatchMode.EXACT; }
            if (stack != null && (amount == INFINITE || amount > 0)) {
                reserves.put(stack.what(), new Entry(stack.what(), amount, mode));
            }
        }
    }

    private Entry reservationFor(AEKey key) {
        if (key == null) return null;
        var exact = reserves.get(key);
        if (exact != null) return exact;
        for (var entry : reserves.values()) {
            if (entry.mode() == ReservedStockMatchMode.IGNORE_SECONDARY
                    && sameSecondaryGroup(entry.key(), key)) return entry;
        }
        return null;
    }

    private boolean belongsToGroupReservation(Entry groupReservation, AEKey candidate) {
        if (!sameSecondaryGroup(groupReservation.key(), candidate)) return false;
        var exact = reserves.get(candidate);
        return exact == null || exact == groupReservation
                || exact.mode() != ReservedStockMatchMode.EXACT;
    }

    private static boolean sameSecondaryGroup(AEKey left, AEKey right) {
        return left != null && right != null && left.dropSecondary().equals(right.dropSecondary());
    }

    private static String stableKey(AEKey key) {
        return key.getId() + "|" + String.valueOf(key.getPrimaryKey()) + "|" + key.hashCode();
    }

    private static long saturatingAdd(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    public record Entry(AEKey key, long amount, ReservedStockMatchMode mode) { }

    public enum PutResult { ADDED, UPDATED, REMOVED, FULL, UNAVAILABLE, INVALID }
}
