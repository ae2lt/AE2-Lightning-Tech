package com.moakiee.ae2lt.item.railgun;

import java.util.List;
import java.util.Set;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;

public class RailgunModuleItem extends Item implements OverloadDeviceModuleItem {

    private static final Set<DeviceKind> ACCEPTS = Set.of(DeviceKind.RAILGUN);

    private final RailgunModuleType type;

    public RailgunModuleItem(Properties properties, RailgunModuleType type) {
        super(properties);
        this.type = type;
    }

    public RailgunModuleType moduleType() {
        return type;
    }

    @Override
    public Set<DeviceKind> acceptableDevices() {
        return ACCEPTS;
    }

    @Override
    public DeviceSlotType acceptableSlot() {
        return switch (type) {
            case CORE -> DeviceSlotType.CORE;
            case COMPUTE -> DeviceSlotType.COMPUTE;
            case ACCELERATION -> DeviceSlotType.ACCELERATION;
            case ENERGY -> DeviceSlotType.ENERGY;
        };
    }

    @Override
    public List<DeviceCapability> capabilities(ItemStack stack) {
        // Per-stack contribution. Aggregation (count of N modules) is done by services
        // iterating the resolver output — the same way the legacy *Count() helpers worked.
        return switch (type) {
            case CORE -> List.of();
            case COMPUTE -> List.of(
                    new DeviceCapability.ChainTuning(2, 1, 0),
                    new DeviceCapability.PulseTuning(1.5D, 1.0D));
            case ACCELERATION -> List.of(new DeviceCapability.AccelerationFactor(0.30D));
            case ENERGY -> List.of(new DeviceCapability.EnergyTuning(0.50D, 0L));
        };
    }
}
