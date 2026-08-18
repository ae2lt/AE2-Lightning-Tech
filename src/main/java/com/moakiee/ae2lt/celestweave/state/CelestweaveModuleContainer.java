package com.moakiee.ae2lt.celestweave.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Immutable per-stack persistent state for one Celestweave armor piece. Backs a
 * data component, so it is auto-synced to clients and saved with the stack.
 * Mutations return a new instance (Mekanism-style), which keeps callers from
 * aliasing the stored map and forces a {@code stack.set} to re-sync.
 *
 * <p>{@code energyModuleCapacityFe} empty means "never computed" (legacy stacks);
 * present (even 0) means the cache is valid.
 */
public record CelestweaveModuleContainer(
        Optional<UUID> armorId,
        List<ItemStack> modules,
        Map<String, Boolean> toggles,
        Map<String, CompoundTag> submoduleData,
        Optional<Long> energyModuleCapacityFe) {

    public static final CelestweaveModuleContainer EMPTY = new CelestweaveModuleContainer(
            Optional.empty(), List.of(), Map.of(), Map.of(), Optional.empty());

    // 1.20.1 移植：无 ItemStack Codec/StreamCodec（CODEC/STREAM_CODEC 已删除），改用手动 NBT 往返。
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        armorId.ifPresent(id -> tag.putUUID("armor_id", id));
        ListTag moduleList = new ListTag();
        for (ItemStack stack : modules) {
            moduleList.add(stack.save(new CompoundTag()));
        }
        tag.put("modules", moduleList);
        CompoundTag togglesTag = new CompoundTag();
        toggles.forEach(togglesTag::putBoolean);
        tag.put("toggles", togglesTag);
        CompoundTag dataTag = new CompoundTag();
        submoduleData.forEach(dataTag::put);
        tag.put("submodule_data", dataTag);
        energyModuleCapacityFe.ifPresent(v -> tag.putLong("energy_capacity_fe", v));
        return tag;
    }

    public static CelestweaveModuleContainer load(CompoundTag tag) {
        Optional<UUID> armorId = tag.hasUUID("armor_id")
                ? Optional.of(tag.getUUID("armor_id"))
                : Optional.empty();
        List<ItemStack> moduleStacks = new ArrayList<>();
        ListTag moduleList = tag.getList("modules", Tag.TAG_COMPOUND);
        for (int i = 0; i < moduleList.size(); i++) {
            ItemStack stack = ItemStack.of(moduleList.getCompound(i));
            if (!stack.isEmpty()) {
                moduleStacks.add(stack);
            }
        }
        Map<String, Boolean> toggles = new HashMap<>();
        CompoundTag togglesTag = tag.getCompound("toggles");
        for (String key : togglesTag.getAllKeys()) {
            toggles.put(key, togglesTag.getBoolean(key));
        }
        Map<String, CompoundTag> submoduleData = new HashMap<>();
        CompoundTag dataTag = tag.getCompound("submodule_data");
        for (String key : dataTag.getAllKeys()) {
            submoduleData.put(key, dataTag.getCompound(key).copy());
        }
        Optional<Long> capacity = tag.contains("energy_capacity_fe")
                ? Optional.of(tag.getLong("energy_capacity_fe"))
                : Optional.empty();
        return new CelestweaveModuleContainer(armorId, moduleStacks, toggles, submoduleData, capacity);
    }

    public CelestweaveModuleContainer {
        modules = copyModules(modules);
        toggles = toggles == null ? Map.of() : Map.copyOf(toggles);
        submoduleData = copySubmoduleData(submoduleData);
    }

    @Override
    public List<ItemStack> modules() {
        return copyModules(modules);
    }

    @Override
    public Map<String, CompoundTag> submoduleData() {
        return copySubmoduleData(submoduleData);
    }

    public CelestweaveModuleContainer withArmorId(UUID id) {
        return new CelestweaveModuleContainer(Optional.ofNullable(id), modules, toggles, submoduleData, energyModuleCapacityFe);
    }

    /** Replace the module list and the (re-derived) capacity cache together; they always change as a unit. */
    public CelestweaveModuleContainer withModules(List<ItemStack> newModules, Optional<Long> capacityFe) {
        return new CelestweaveModuleContainer(armorId, newModules, toggles, submoduleData, capacityFe);
    }

    public CelestweaveModuleContainer withToggles(Map<String, Boolean> newToggles) {
        return new CelestweaveModuleContainer(armorId, modules, newToggles, submoduleData, energyModuleCapacityFe);
    }

    public CelestweaveModuleContainer withSubmoduleData(Map<String, CompoundTag> newData) {
        return new CelestweaveModuleContainer(armorId, modules, toggles, newData, energyModuleCapacityFe);
    }

    public CelestweaveModuleContainer withCapacity(Optional<Long> capacityFe) {
        return new CelestweaveModuleContainer(armorId, modules, toggles, submoduleData, capacityFe);
    }

    private static List<ItemStack> copyModules(List<ItemStack> modules) {
        if (modules == null || modules.isEmpty()) {
            return List.of();
        }
        return modules.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    private static Map<String, CompoundTag> copySubmoduleData(Map<String, CompoundTag> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, CompoundTag> copy = new LinkedHashMap<>();
        for (var entry : data.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            copy.put(entry.getKey(), entry.getValue().copy());
        }
        return Map.copyOf(copy);
    }
}
