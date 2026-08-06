package com.moakiee.ae2lt.item.railgun;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.celestweave.ArmorEnergyModuleItem;

public final class RailgunEnergyModuleStorage {
    private RailgunEnergyModuleStorage() {
    }

    public static long capacityFe(ItemStack railgun) {
        long capacity = 0L;
        for (ItemStack module : RailgunModuleStorage.INSTANCE.listEntries(railgun)) {
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
