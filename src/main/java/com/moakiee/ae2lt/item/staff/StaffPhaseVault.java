package com.moakiee.ae2lt.item.staff;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Sole owner of each locked real staff. Inventory projections never serialize these stacks. */
public final class StaffPhaseVault extends SavedData {
    static final class Entry {
        final UUID owner, id;
        UUID generation;
        final int slot;
        final ItemStack original;
        ServerPlayer holder;
        ItemStack active = ItemStack.EMPTY;
        Entry(UUID owner, UUID id, UUID generation, int slot, ItemStack original) {
            this.owner = owner; this.id = id; this.generation = generation; this.slot = slot; this.original = original;
        }
        StaffProjectionLink link() { return new StaffProjectionLink(owner, id, generation, slot); }
    }

    private static final Factory<StaffPhaseVault> FACTORY = new Factory<>(StaffPhaseVault::new, StaffPhaseVault::load, null);
    final Map<UUID, Map<Integer, Entry>> entries = new HashMap<>();
    public static StaffPhaseVault get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "ae2lt_staff_phase_vault");
    }
    Entry find(StaffProjectionLink link) {
        if (link == null) return null;
        Entry entry = entries.getOrDefault(link.owner(), Map.of()).get(link.slot());
        return entry != null && entry.id.equals(link.tool()) && entry.generation.equals(link.generation()) ? entry : null;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var slots : entries.values()) for (var entry : slots.values()) {
            var value = new CompoundTag();
            value.putUUID("Owner", entry.owner); value.putUUID("Tool", entry.id); value.putUUID("Generation", entry.generation);
            value.putInt("Slot", entry.slot); value.put("Original", entry.original.save(registries));
            list.add(value);
        }
        tag.put("Entries", list);
        return tag;
    }
    public static StaffPhaseVault load(CompoundTag tag, HolderLookup.Provider registries) {
        var vault = new StaffPhaseVault();
        for (var raw : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            var value = (CompoundTag) raw;
            int slot = value.getInt("Slot");
            if (!value.hasUUID("Owner") || !value.hasUUID("Tool") || !value.hasUUID("Generation")
                    || slot < 0 || slot > 40 || slot >= 36 && slot != 40) continue;
            ItemStack original = ItemStack.parseOptional(registries, value.getCompound("Original"));
            if (!(original.getItem() instanceof MimicryStaffItem) || original.getItem() instanceof StaffProjectionItem || original.getCount() != 1) continue;
            var entry = new Entry(value.getUUID("Owner"), value.getUUID("Tool"), value.getUUID("Generation"), slot, original);
            vault.entries.computeIfAbsent(entry.owner, ignored -> new HashMap<>()).putIfAbsent(slot, entry);
        }
        return vault;
    }
}
