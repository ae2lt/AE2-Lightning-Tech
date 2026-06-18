package com.moakiee.ae2lt.celestweave.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.celestweave.ArmorEnergyModuleItem;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.module.CelestweaveArmorSubmoduleItem;
import com.moakiee.ae2lt.registry.ModDataComponents;

public final class ArmorPersistentData {
    private static final int MAX_MODULE_TYPES = 32;
    private static final String TAG_ROOT = "CelestweaveArmor";
    private static final String TAG_ARMOR_ID = "ArmorId";
    private static final String TAG_INSTALLED_SUBMODULES = "InstalledSubmodules";
    private static final String TAG_SUBMODULE_DATA = "SubmoduleData";
    private static final String TAG_FEATURE_TOGGLES = "FeatureToggles";
    private static final String TAG_ENERGY_MODULE_CAPACITY_FE = "EnergyModuleCapacityFe";

    private ArmorPersistentData() {
    }

    public static UUID ensureArmorId(ItemStack armor) {
        Optional<UUID> existing = armorId(armor);
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID created = UUID.randomUUID();
        updateArmorTag(armor, armorTag -> armorTag.putUUID(TAG_ARMOR_ID, created));
        return created;
    }

    public static Optional<UUID> armorId(ItemStack armor) {
        if (armor == null || armor.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag root = rootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag armorTag = root.getCompound(TAG_ROOT);
        return armorTag.hasUUID(TAG_ARMOR_ID) ? Optional.of(armorTag.getUUID(TAG_ARMOR_ID)) : Optional.empty();
    }

    public static long getCachedEnergyModuleCapacityFe(ItemStack armor) {
        CompoundTag root = rootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return 0L;
        }
        return Math.max(0L, root.getCompound(TAG_ROOT).getLong(TAG_ENERGY_MODULE_CAPACITY_FE));
    }

    // Tag presence distinguishes "computed, value is 0" from "never computed" (legacy stacks).
    public static boolean hasCachedEnergyModuleCapacityFe(ItemStack armor) {
        CompoundTag root = rootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return false;
        }
        return root.getCompound(TAG_ROOT).contains(TAG_ENERGY_MODULE_CAPACITY_FE, Tag.TAG_LONG);
    }

    public static void setCachedEnergyModuleCapacityFe(ItemStack armor, long capacityFe) {
        if (armor == null || armor.isEmpty()) {
            return;
        }
        // Always store (even 0) so the cache reliably hits next tick.
        updateArmorTag(armor, armorTag -> armorTag.putLong(TAG_ENERGY_MODULE_CAPACITY_FE, Math.max(0L, capacityFe)));
    }

    public static ItemStack structuralCore(ItemStack armor) {
        return armor.getOrDefault(ModDataComponents.CELESTWEAVE_STRUCTURAL_CORE.get(), ItemStack.EMPTY).copyWithCount(1);
    }

    public static void setStructuralCore(ItemStack armor, ItemStack stack) {
        if (armor == null || armor.isEmpty()) {
            return;
        }
        if (stack == null || stack.isEmpty()) {
            armor.remove(ModDataComponents.CELESTWEAVE_STRUCTURAL_CORE.get());
        } else {
            armor.set(ModDataComponents.CELESTWEAVE_STRUCTURAL_CORE.get(), stack.copyWithCount(1));
        }
    }

    public static boolean hasStructuralCore(ItemStack armor) {
        return !structuralCore(armor).isEmpty();
    }

    public static List<ItemStack> loadModuleStacks(ItemStack armor, HolderLookup.Provider registries) {
        CompoundTag root = rootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return List.of();
        }
        CompoundTag armorTag = root.getCompound(TAG_ROOT);
        if (!armorTag.contains(TAG_INSTALLED_SUBMODULES, CompoundTag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = armorTag.getList(TAG_INSTALLED_SUBMODULES, CompoundTag.TAG_COMPOUND);
        List<ItemStack> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i).copy());
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        return List.copyOf(result);
    }

    public static boolean hasInstalledSubmodule(ItemStack armor, String submoduleId) {
        if (submoduleId == null || submoduleId.isBlank()) {
            return false;
        }
        CompoundTag root = rootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag armorTag = root.getCompound(TAG_ROOT);
        if (!armorTag.contains(TAG_INSTALLED_SUBMODULES, CompoundTag.TAG_LIST)) {
            return false;
        }
        ListTag list = armorTag.getList(TAG_INSTALLED_SUBMODULES, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (submoduleId.equals(persistedModuleTypeId(list.getCompound(i)))) {
                return true;
            }
        }
        return false;
    }

    public static void saveModuleStacks(ItemStack armor, HolderLookup.Provider registries, List<ItemStack> stacks) {
        updateArmorTag(armor, armorTag -> {
            ListTag out = new ListTag();
            LinkedHashMap<String, ItemStack> merged = new LinkedHashMap<>();
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                String id = resolveModuleTypeId(stack);
                if (id.isBlank()) {
                    continue;
                }
                merged.compute(id, (ignored, existing) -> {
                    if (existing == null) {
                        return stack.copy();
                    }
                    existing.grow(stack.getCount());
                    return existing;
                });
            }
            int writtenTypes = 0;
            int writtenUnits = 0;
            long energyCapacityFe = 0L;
            int maxUnits = armorPart(armor).moduleSlotCount();
            for (ItemStack stack : merged.values()) {
                if (writtenTypes >= MAX_MODULE_TYPES || writtenUnits >= maxUnits) {
                    break;
                }
                int count = Math.min(Math.max(1, stack.getCount()), maxUnits - writtenUnits);
                ItemStack writtenStack = stack.copyWithCount(count);
                out.add(writtenStack.saveOptional(registries));
                energyCapacityFe = Math.max(energyCapacityFe, energyCapacityFe(writtenStack));
                writtenTypes++;
                writtenUnits += count;
            }
            if (out.isEmpty()) {
                armorTag.remove(TAG_INSTALLED_SUBMODULES);
                armorTag.remove(TAG_ENERGY_MODULE_CAPACITY_FE);
            } else {
                armorTag.put(TAG_INSTALLED_SUBMODULES, out);
                // Always cache (even 0) so the tick path hits the cache instead of reparsing.
                armorTag.putLong(TAG_ENERGY_MODULE_CAPACITY_FE, energyCapacityFe);
            }
        });
    }

    public static boolean getToggle(ItemStack armor, String key, boolean defaultValue) {
        CompoundTag armorTag = armorTag(rootTag(armor));
        if (!armorTag.contains(TAG_FEATURE_TOGGLES, CompoundTag.TAG_COMPOUND)) {
            return defaultValue;
        }
        CompoundTag toggles = armorTag.getCompound(TAG_FEATURE_TOGGLES);
        return toggles.contains(key, Tag.TAG_BYTE) ? toggles.getBoolean(key) : defaultValue;
    }

    public static void setToggle(ItemStack armor, String key, boolean value, boolean defaultValue) {
        if (key == null || key.isBlank()) {
            return;
        }
        updateArmorTag(armor, armorTag -> {
            CompoundTag toggles = armorTag.contains(TAG_FEATURE_TOGGLES, CompoundTag.TAG_COMPOUND)
                    ? armorTag.getCompound(TAG_FEATURE_TOGGLES)
                    : new CompoundTag();
            if (value == defaultValue) {
                toggles.remove(key);
            } else {
                toggles.putBoolean(key, value);
            }
            if (toggles.isEmpty()) {
                armorTag.remove(TAG_FEATURE_TOGGLES);
            } else {
                armorTag.put(TAG_FEATURE_TOGGLES, toggles);
            }
        });
    }

    public static CompoundTag getSubmoduleData(ItemStack armor, String submoduleId) {
        CompoundTag root = rootTag(armor);
        if (!root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        CompoundTag armorTag = root.getCompound(TAG_ROOT);
        if (!armorTag.contains(TAG_SUBMODULE_DATA, CompoundTag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        CompoundTag data = armorTag.getCompound(TAG_SUBMODULE_DATA);
        return data.contains(submoduleId, CompoundTag.TAG_COMPOUND)
                ? data.getCompound(submoduleId).copy()
                : new CompoundTag();
    }

    public static void setSubmoduleData(ItemStack armor, String submoduleId, CompoundTag data) {
        updateArmorTag(armor, armorTag -> {
            CompoundTag allData = armorTag.contains(TAG_SUBMODULE_DATA, CompoundTag.TAG_COMPOUND)
                    ? armorTag.getCompound(TAG_SUBMODULE_DATA)
                    : new CompoundTag();
            if (data == null || data.isEmpty()) {
                allData.remove(submoduleId);
            } else {
                allData.put(submoduleId, data.copy());
            }
            if (allData.isEmpty()) {
                armorTag.remove(TAG_SUBMODULE_DATA);
            } else {
                armorTag.put(TAG_SUBMODULE_DATA, allData);
            }
        });
    }

    private static void updateArmorTag(ItemStack armor, Consumer<CompoundTag> update) {
        if (armor == null || armor.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, armor, root -> {
            CompoundTag armorTag = armorTag(root);
            update.accept(armorTag);
            root.put(TAG_ROOT, armorTag);
        });
    }

    private static String resolveModuleTypeId(ItemStack stack) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof ArmorEnergyModuleItem) {
            return ArmorEnergyModuleItem.MODULE_TYPE_ID;
        }
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof CelestweaveArmorSubmoduleItem provider)) {
            return "";
        }
        String[] ref = {""};
        provider.collectSubmodules(stack, submodule -> {
            if (submodule != null && !submodule.id().isBlank() && ref[0].isEmpty()) {
                ref[0] = submodule.id();
            }
        });
        return ref[0];
    }

    private static String persistedModuleTypeId(CompoundTag stackTag) {
        String itemId = stackTag.getString("id");
        return switch (itemId) {
            case "ae2lt:energy_module_t1",
                 "ae2lt:energy_module_t2",
                 "ae2lt:energy_module_t3" -> ArmorEnergyModuleItem.MODULE_TYPE_ID;
            case "ae2lt:module_night_vision" -> "night_vision";
            case "ae2lt:module_water_breathing" -> "water_breathing";
            case "ae2lt:module_reach_extension" -> "reach_extension";
            case "ae2lt:module_matrix_shield" -> "matrix_shield";
            case "ae2lt:module_phase_shield" -> "phase_shield";
            case "ae2lt:module_reflect" -> "reflect";
            case "ae2lt:module_undying" -> "undying";
            case "ae2lt:module_dash" -> "dash";
            case "ae2lt:module_creative_flight" -> "flight";
            case "ae2lt:module_purification" -> "purification";
            case "ae2lt:module_saturation" -> "saturation";
            case "ae2lt:module_dig_affinity" -> "dig_affinity";
            case "ae2lt:module_phase_flight" -> "phase_flight";
            default -> "";
        };
    }

    private static long energyCapacityFe(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0L;
        }
        if (stack.getItem() instanceof ArmorEnergyModuleItem energyModule) {
            return energyModule.armorCapacityFe();
        }
        if (stack.getItem() instanceof OverloadDeviceModuleItem provider) {
            long capacity = 0L;
            for (DeviceCapability capability : provider.capabilities(stack.copyWithCount(1))) {
                if (capability instanceof DeviceCapability.EnergyCapacity energyCapacity) {
                    capacity = Math.max(capacity, energyCapacity.fe());
                }
            }
            return capacity;
        }
        return 0L;
    }

    private static ArmorPart armorPart(ItemStack armor) {
        if (armor != null && armor.getItem() instanceof BaseCelestweaveArmorItem item) {
            return item.armorPart();
        }
        return ArmorPart.CHEST;
    }

    private static CompoundTag rootTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static CompoundTag armorTag(CompoundTag root) {
        return root.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                ? root.getCompound(TAG_ROOT)
                : new CompoundTag();
    }
}
