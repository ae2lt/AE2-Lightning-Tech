package com.moakiee.ae2lt.overload.armor;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;

public final class ArmorEnergyModuleStorage {
    private ArmorEnergyModuleStorage() {
    }

    public static long capacityFe(ItemStack armor, HolderLookup.Provider registries) {
        if (registries == null) {
            return 0L;
        }
        long capacity = 0L;
        for (ItemStack module : OverloadArmorState.loadModuleStacks(armor, registries)) {
            if (module.getItem() instanceof ArmorEnergyModuleItem energyModule) {
                capacity = Math.max(capacity, energyModule.capacityFe());
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
        return Math.max(0L, capacity);
    }
}
