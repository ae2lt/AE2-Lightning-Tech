package com.moakiee.ae2lt.logic.tianshu.maintenance;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class InventoryMaintenanceRepository {
    private static final String TAG_RULES = "Rules";
    private final IntSupplier capacity;
    private final LinkedHashMap<AEKey, InventoryMaintenanceRule> rules = new LinkedHashMap<>();

    public InventoryMaintenanceRepository(IntSupplier capacity) { this.capacity = capacity; }
    public int capacity() { return Math.max(0, capacity.getAsInt()); }
    public int size() { return rules.size(); }
    public List<InventoryMaintenanceRule> rules() { return List.copyOf(rules.values()); }
    /** Rules currently backed by installed maintenance-core capacity. Overflow stays persisted. */
    public List<InventoryMaintenanceRule> activeRules() {
        return rules.values().stream().limit(capacity()).toList();
    }
    public InventoryMaintenanceRule get(AEKey key) { return rules.get(key); }
    public InventoryMaintenanceRule getById(UUID id) {
        if (id == null) return null;
        for (var rule : rules.values()) if (id.equals(rule.id())) return rule;
        return null;
    }

    public PutResult put(InventoryMaintenanceRule rule) {
        if (rule == null) return PutResult.INVALID;
        if (!rules.containsKey(rule.key()) && rules.size() >= capacity()) {
            return capacity() <= 0 ? PutResult.UNAVAILABLE : PutResult.FULL;
        }
        boolean update = rules.containsKey(rule.key());
        rules.put(rule.key(), rule);
        return update ? PutResult.UPDATED : PutResult.ADDED;
    }

    public boolean remove(AEKey key) { return rules.remove(key) != null; }

    public void writeTo(CompoundTag parent, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var rule : rules.values()) {
            var tag = new CompoundTag();
            tag.putUUID("Id", rule.id());
            tag.put("Key", GenericStack.writeTag(registries, new GenericStack(rule.key(), 1)));
            tag.putLong("Lower", rule.lowerThreshold());
            tag.putLong("Upper", rule.upperThreshold());
            tag.putLong("PerJob", rule.amountPerJob());
            tag.putBoolean("Enabled", rule.enabled());
            tag.putBoolean("Replenishing", rule.replenishing());
            if (rule.activeCraftingId() != null) tag.putUUID("CraftingId", rule.activeCraftingId());
            list.add(tag);
        }
        parent.put(TAG_RULES, list);
    }

    public void readFrom(CompoundTag parent, HolderLookup.Provider registries) {
        rules.clear();
        var list = parent.getList(TAG_RULES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                var tag = list.getCompound(i);
                var keyStack = GenericStack.readTag(registries, tag.getCompound("Key"));
                if (keyStack == null || !tag.hasUUID("Id")) continue;
                var rule = new InventoryMaintenanceRule(
                        tag.getUUID("Id"), keyStack.what(), tag.getLong("Lower"), tag.getLong("Upper"),
                        tag.getLong("PerJob"), tag.getBoolean("Enabled"), tag.getBoolean("Replenishing"),
                        tag.hasUUID("CraftingId") ? tag.getUUID("CraftingId") : null);
                rules.put(rule.key(), rule);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public enum PutResult { ADDED, UPDATED, FULL, UNAVAILABLE, INVALID }
}
