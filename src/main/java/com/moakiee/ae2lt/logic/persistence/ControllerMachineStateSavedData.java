package com.moakiee.ae2lt.logic.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-global state for controller-owned machines. The UUID survives controller removal, while
 * runtime ownership claims are deliberately transient and prevent two loaded copies of one
 * controller from running the same state concurrently.
 */
public final class ControllerMachineStateSavedData extends SavedData {
    private static final String DATA_NAME = "ae2lt_controller_machine_states";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_TYPE = "Type";
    private static final String TAG_ID = "Id";
    private static final String TAG_STATE = "State";

    public static final Factory<ControllerMachineStateSavedData> FACTORY = new Factory<>(
            ControllerMachineStateSavedData::new,
            ControllerMachineStateSavedData::load,
            null);

    private final Map<MachineKey, CompoundTag> states = new HashMap<>();
    private final Map<MachineKey, Owner> owners = new HashMap<>();

    public enum MachineType {
        TIANSHU,
        MATRIX
    }

    private record MachineKey(MachineType type, UUID id) {
        private MachineKey {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(id, "id");
        }
    }

    private record Owner(String dimension, long position) {
    }

    public static ControllerMachineStateSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public boolean hasState(MachineType type, UUID id) {
        return type != null && id != null && states.containsKey(new MachineKey(type, id));
    }

    /** Returns a defensive copy; callers must publish changes with {@link #setState}. */
    public CompoundTag getState(MachineType type, UUID id) {
        if (type == null || id == null) return new CompoundTag();
        var state = states.get(new MachineKey(type, id));
        return state == null ? new CompoundTag() : state.copy();
    }

    public void setState(MachineType type, UUID id, CompoundTag state) {
        if (type == null || id == null || state == null) return;
        var key = new MachineKey(type, id);
        var copy = state.copy();
        if (!copy.equals(states.get(key))) {
            states.put(key, copy);
            setDirty();
        }
    }

    public boolean claim(MachineType type, UUID id, ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        return claim(type, id, level.dimension().location().toString(), pos.asLong());
    }

    public boolean claim(MachineType type, UUID id, String dimension, long position) {
        if (type == null || id == null || dimension == null) return false;
        var key = new MachineKey(type, id);
        var owner = new Owner(dimension, position);
        var current = owners.get(key);
        if (current != null && !current.equals(owner)) return false;
        owners.put(key, owner);
        return true;
    }

    public void release(MachineType type, UUID id, ServerLevel level, BlockPos pos) {
        if (level != null && pos != null) {
            release(type, id, level.dimension().location().toString(), pos.asLong());
        }
    }

    public void release(MachineType type, UUID id, String dimension, long position) {
        if (type == null || id == null || dimension == null) return;
        owners.remove(new MachineKey(type, id), new Owner(dimension, position));
    }

    public boolean isOwner(MachineType type, UUID id, ServerLevel level, BlockPos pos) {
        if (type == null || id == null || level == null || pos == null) return false;
        return new Owner(level.dimension().location().toString(), pos.asLong())
                .equals(owners.get(new MachineKey(type, id)));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        for (var entry : states.entrySet()) {
            var entryTag = new CompoundTag();
            entryTag.putString(TAG_TYPE, entry.getKey().type().name());
            entryTag.putUUID(TAG_ID, entry.getKey().id());
            entryTag.put(TAG_STATE, entry.getValue().copy());
            entries.add(entryTag);
        }
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }

    public static ControllerMachineStateSavedData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ControllerMachineStateSavedData();
        var entries = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            if (!entry.hasUUID(TAG_ID) || !entry.contains(TAG_STATE, Tag.TAG_COMPOUND)) continue;
            try {
                var type = MachineType.valueOf(entry.getString(TAG_TYPE));
                data.states.put(new MachineKey(type, entry.getUUID(TAG_ID)),
                        entry.getCompound(TAG_STATE).copy());
            } catch (IllegalArgumentException ignored) {
                // A removed machine type must not make the remaining world data unreadable.
            }
        }
        return data;
    }
}
