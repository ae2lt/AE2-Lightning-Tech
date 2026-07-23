package com.moakiee.ae2lt.celestweave.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.celestweave.ArmorEnergyModuleItem;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.module.CelestweaveArmorSubmoduleItem;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Persistent state for one Celestweave armor piece. Backed by the immutable
 * {@link CelestweaveModuleContainer} data component (auto-synced + saved with
 * the stack); the structural core and energy buffer live in their own
 * components. Public method signatures are kept stable so submodules and the
 * GUI need no changes.
 */
public final class ArmorPersistentData {
    private static final int MAX_MODULE_TYPES = 32;

    private ArmorPersistentData() {
    }

    public static UUID ensureArmorId(ItemStack armor) {
        CelestweaveModuleContainer container = container(armor);
        if (container.armorId().isPresent()) {
            return container.armorId().get();
        }
        UUID created = UUID.randomUUID();
        setContainer(armor, container.withArmorId(created));
        return created;
    }

    public static Optional<UUID> armorId(ItemStack armor) {
        return container(armor).armorId();
    }

    public static long getCachedEnergyModuleCapacityFe(ItemStack armor) {
        return Math.max(0L, container(armor).energyModuleCapacityFe().orElse(0L));
    }

    // Optional presence distinguishes "computed, value is 0" from "never computed" (legacy stacks).
    public static boolean hasCachedEnergyModuleCapacityFe(ItemStack armor) {
        return container(armor).energyModuleCapacityFe().isPresent();
    }

    public static void setCachedEnergyModuleCapacityFe(ItemStack armor, long capacityFe) {
        if (armor == null || armor.isEmpty()) {
            return;
        }
        // Always store (even 0) so the cache reliably hits next tick.
        setContainer(armor, container(armor).withCapacity(Optional.of(Math.max(0L, capacityFe))));
    }

    /**
     * Returns the installed structural core as a plain ItemStack.
     * The data component stores a {@link StructuralCoreWrapper} so that
     * {@code equals} uses {@code ItemStack.matches} rather than reference
     * identity (issue #20 — see the wrapper's javadoc).
     */
    public static ItemStack structuralCore(ItemStack armor) {
        return armor.getOrDefault(ModDataComponents.CELESTWEAVE_STRUCTURAL_CORE.get(), StructuralCoreWrapper.EMPTY).stack().copyWithCount(1);
    }

    /**
     * Sets the structural core. The core is wrapped in a {@link StructuralCoreWrapper}
     * before storage so that data-component equality checks use content-based
     * comparison ({@code ItemStack.matches}) rather than reference identity.
     */
    public static void setStructuralCore(ItemStack armor, ItemStack stack) {
        if (armor == null || armor.isEmpty()) {
            return;
        }
        if (stack == null || stack.isEmpty()) {
            armor.remove(ModDataComponents.CELESTWEAVE_STRUCTURAL_CORE.get());
        } else {
            armor.set(ModDataComponents.CELESTWEAVE_STRUCTURAL_CORE.get(), new StructuralCoreWrapper(stack));
        }
    }

    public static boolean hasStructuralCore(ItemStack armor) {
        return !structuralCore(armor).isEmpty();
    }

    public static List<ItemStack> loadModuleStacks(ItemStack armor, HolderLookup.Provider registries) {
        List<ItemStack> modules = container(armor).modules();
        if (modules.isEmpty()) {
            return List.of();
        }
        // Hand out copies so callers can mutate (grow/shrink) without aliasing the stored stacks.
        List<ItemStack> result = new ArrayList<>(modules.size());
        for (ItemStack stack : modules) {
            if (!stack.isEmpty()) {
                result.add(stack.copy());
            }
        }
        return List.copyOf(result);
    }

    public static boolean hasInstalledSubmodule(ItemStack armor, String submoduleId) {
        if (submoduleId == null || submoduleId.isBlank()) {
            return false;
        }
        for (ItemStack stack : container(armor).modules()) {
            if (submoduleId.equals(resolveModuleTypeId(stack))) {
                return true;
            }
        }
        return false;
    }

    public static void saveModuleStacks(ItemStack armor, HolderLookup.Provider registries, List<ItemStack> stacks) {
        if (armor == null || armor.isEmpty()) {
            return;
        }
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
        List<ItemStack> out = new ArrayList<>();
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
            out.add(writtenStack);
            energyCapacityFe = Math.max(energyCapacityFe, energyCapacityFe(writtenStack));
            writtenTypes++;
            writtenUnits += count;
        }
        // No modules -> clear the capacity cache (empty) too; otherwise always cache (even 0).
        Optional<Long> capacity = out.isEmpty() ? Optional.empty() : Optional.of(energyCapacityFe);
        setContainer(armor, container(armor).withModules(out, capacity));
    }

    public static boolean getToggle(ItemStack armor, String key, boolean defaultValue) {
        Boolean value = container(armor).toggles().get(key);
        return value == null ? defaultValue : value;
    }

    public static void setToggle(ItemStack armor, String key, boolean value, boolean defaultValue) {
        if (key == null || key.isBlank()) {
            return;
        }
        CelestweaveModuleContainer container = container(armor);
        Map<String, Boolean> toggles = new LinkedHashMap<>(container.toggles());
        if (value == defaultValue) {
            toggles.remove(key);
        } else {
            toggles.put(key, value);
        }
        setContainer(armor, container.withToggles(toggles));
    }

    public static CompoundTag getSubmoduleData(ItemStack armor, String submoduleId) {
        CompoundTag data = container(armor).submoduleData().get(submoduleId);
        return data == null ? new CompoundTag() : data.copy();
    }

    public static void setSubmoduleData(ItemStack armor, String submoduleId, CompoundTag data) {
        CelestweaveModuleContainer container = container(armor);
        Map<String, CompoundTag> allData = new LinkedHashMap<>(container.submoduleData());
        if (data == null || data.isEmpty()) {
            allData.remove(submoduleId);
        } else {
            allData.put(submoduleId, data.copy());
        }
        setContainer(armor, container.withSubmoduleData(allData));
    }

    private static CelestweaveModuleContainer container(ItemStack armor) {
        if (armor == null || armor.isEmpty()) {
            return CelestweaveModuleContainer.EMPTY;
        }
        return armor.getOrDefault(ModDataComponents.CELESTWEAVE_MODULES.get(), CelestweaveModuleContainer.EMPTY);
    }

    private static void setContainer(ItemStack armor, CelestweaveModuleContainer container) {
        if (armor == null || armor.isEmpty()) {
            return;
        }
        armor.set(ModDataComponents.CELESTWEAVE_MODULES.get(), container);
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
}
