package com.moakiee.ae2lt.celestweave.phase;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The authoritative server-side slots for phase-locked Celestweave armor.
 *
 * <p>Values are deliberately returned by identity. The vault, equipment accessors and runtime
 * services all operate on the same {@link ItemStack}; no armor snapshot is ever copied back into a
 * player's inventory.</p>
 */
public final class PhaseArmorVaultSavedData extends SavedData {
    private static final String DATA_NAME = "ae2lt_phase_armor_vault";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_ARMOR = "Armor";

    public static final Factory<PhaseArmorVaultSavedData> FACTORY = new Factory<>(
            PhaseArmorVaultSavedData::new,
            PhaseArmorVaultSavedData::load,
            null);

    private final Map<UUID, EnumMap<EquipmentSlot, ItemStack>> armorByPlayer = new HashMap<>();

    public static PhaseArmorVaultSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    @Nullable
    ItemStack getMutable(UUID playerId, EquipmentSlot slot) {
        var armorBySlot = armorByPlayer.get(playerId);
        ItemStack armor = armorBySlot == null ? null : armorBySlot.get(slot);
        if (armor != null) {
            // Callers receive the live stack and may mutate its components or energy immediately.
            setDirty();
        }
        return armor;
    }

    boolean store(UUID playerId, EquipmentSlot slot, ItemStack armor) {
        if (!isArmorSlot(slot) || armor == null || armor.isEmpty()) {
            return false;
        }
        var armorBySlot = armorByPlayer.computeIfAbsent(
                playerId,
                ignored -> new EnumMap<>(EquipmentSlot.class));
        if (armorBySlot.containsKey(slot)) {
            return false;
        }
        armorBySlot.put(slot, armor);
        setDirty();
        return true;
    }

    ItemStack take(UUID playerId, EquipmentSlot slot) {
        var armorBySlot = armorByPlayer.get(playerId);
        if (armorBySlot == null) {
            return ItemStack.EMPTY;
        }
        ItemStack armor = armorBySlot.remove(slot);
        if (armor == null) {
            return ItemStack.EMPTY;
        }
        if (armorBySlot.isEmpty()) {
            armorByPlayer.remove(playerId);
        }
        setDirty();
        return armor;
    }

    EnumMap<EquipmentSlot, ItemStack> takeAll(UUID playerId) {
        var armorBySlot = armorByPlayer.remove(playerId);
        if (armorBySlot == null) {
            return new EnumMap<>(EquipmentSlot.class);
        }
        setDirty();
        return armorBySlot;
    }

    boolean contains(UUID playerId, EquipmentSlot slot) {
        var armorBySlot = armorByPlayer.get(playerId);
        return armorBySlot != null && armorBySlot.containsKey(slot);
    }

    boolean containsAny(UUID playerId) {
        var armorBySlot = armorByPlayer.get(playerId);
        return armorBySlot != null && !armorBySlot.isEmpty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        for (var playerEntry : armorByPlayer.entrySet()) {
            for (var armorEntry : playerEntry.getValue().entrySet()) {
                ItemStack armor = armorEntry.getValue();
                if (armor == null || armor.isEmpty()) {
                    continue;
                }
                var entryTag = new CompoundTag();
                entryTag.putUUID(TAG_PLAYER, playerEntry.getKey());
                entryTag.putString(TAG_SLOT, armorEntry.getKey().getName());
                entryTag.put(TAG_ARMOR, armor.save(registries));
                entries.add(entryTag);
            }
        }
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }

    static PhaseArmorVaultSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new PhaseArmorVaultSavedData();
        if (!tag.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            return data;
        }
        var entries = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entryTag = entries.getCompound(i);
            if (!entryTag.hasUUID(TAG_PLAYER) || !entryTag.contains(TAG_ARMOR, Tag.TAG_COMPOUND)) {
                continue;
            }
            EquipmentSlot slot = entryTag.contains(TAG_SLOT, Tag.TAG_STRING)
                    ? armorSlot(entryTag.getString(TAG_SLOT))
                    : EquipmentSlot.CHEST; // Legacy single-slot vault entries were always chestplates.
            if (slot == null) {
                continue;
            }
            ItemStack armor = ItemStack.parseOptional(registries, entryTag.getCompound(TAG_ARMOR));
            if (!armor.isEmpty()) {
                data.armorByPlayer
                        .computeIfAbsent(
                                entryTag.getUUID(TAG_PLAYER),
                                ignored -> new EnumMap<>(EquipmentSlot.class))
                        .putIfAbsent(slot, armor);
            }
        }
        return data;
    }

    private static boolean isArmorSlot(EquipmentSlot slot) {
        return slot != null && slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    @Nullable
    private static EquipmentSlot armorSlot(String name) {
        return switch (name) {
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }
}
