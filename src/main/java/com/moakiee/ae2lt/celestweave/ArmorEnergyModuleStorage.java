package com.moakiee.ae2lt.celestweave;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;

public final class ArmorEnergyModuleStorage {
    private ArmorEnergyModuleStorage() {
    }

    public static long capacityFe(ItemStack armor, HolderLookup.Provider registries) {
        // Cache hit: skip module NBT reparse entirely (tick-path hot loop). The cache is
        // written on every saveModuleStacks, so it stays fresh across install/uninstall.
        if (CelestweaveArmorState.hasCachedEnergyModuleCapacityFe(armor)) {
            return CelestweaveArmorState.getCachedEnergyModuleCapacityFe(armor);
        }
        if (registries == null) {
            return CelestweaveArmorState.getCachedEnergyModuleCapacityFe(armor);
        }
        long capacity = 0L;
        for (ItemStack module : CelestweaveArmorState.loadModuleStacks(armor, registries)) {
            if (module.getItem() instanceof ArmorEnergyModuleItem energyModule) {
                capacity = Math.max(capacity, energyModule.armorCapacityFe());
                continue;
            }
            if (module.getItem() instanceof OverloadDeviceModuleItem provider) {
                for (var capability : provider.capabilities(module.copyWithCount(1))) {
                    if (capability instanceof DeviceCapability.EnergyCapacity energyCapacity) {
                        capacity = Math.max(capacity, energyCapacity.fe());
                    }
                }
            }
        }
        capacity = Math.max(0L, capacity);
        CelestweaveArmorState.setCachedEnergyModuleCapacityFe(armor, capacity);
        return capacity;
    }
}
