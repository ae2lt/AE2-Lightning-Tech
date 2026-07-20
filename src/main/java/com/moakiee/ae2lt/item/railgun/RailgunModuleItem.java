package com.moakiee.ae2lt.item.railgun;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.energy.LightningCompensationPolicy;
import com.moakiee.ae2lt.device.module.ModuleTooltip;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;

public class RailgunModuleItem extends Item implements OverloadDeviceModuleItem {
    private static final Set<DeviceKind> RAILGUN_ONLY = Set.of(DeviceKind.RAILGUN);
    private static final Set<DeviceKind> CORE_ACCEPTS = Set.of(DeviceKind.RAILGUN, DeviceKind.CELESTWEAVE_CORE);

    private final RailgunModuleType type;

    public RailgunModuleItem(Properties properties, RailgunModuleType type) {
        super(properties);
        this.type = type;
    }

    public RailgunModuleType moduleType() {
        return type;
    }

    public int getMaxInstallAmount() {
        return maxInstallAmount(type);
    }

    static int maxInstallAmount(RailgunModuleType type) {
        return switch (type) {
            case CORE, OVERLOAD_EXECUTION -> 1;
            case COMPUTE, ACCELERATION -> 2;
        };
    }

    @Override
    public Set<DeviceKind> acceptableDevices() {
        return type == RailgunModuleType.CORE ? CORE_ACCEPTS : RAILGUN_ONLY;
    }

    @Override
    public DeviceSlotType acceptableSlot() {
        return switch (type) {
            case CORE -> DeviceSlotType.CORE;
            case COMPUTE -> DeviceSlotType.COMPUTE;
            case ACCELERATION -> DeviceSlotType.ACCELERATION;
            case OVERLOAD_EXECUTION -> DeviceSlotType.OVERLOAD_EXECUTION;
        };
    }

    @Override
    public boolean accepts(DeviceKind deviceKind, DeviceSlotType slotType) {
        return accepts(type, deviceKind, slotType);
    }

    static boolean accepts(RailgunModuleType type, DeviceKind deviceKind, DeviceSlotType slotType) {
        if (type == RailgunModuleType.CORE && deviceKind == DeviceKind.CELESTWEAVE_CORE) {
            return slotType == DeviceSlotType.CHEST_MODULE;
        }
        return deviceKind == DeviceKind.RAILGUN && slotType == switch (type) {
            case CORE -> DeviceSlotType.CORE;
            case COMPUTE -> DeviceSlotType.COMPUTE;
            case ACCELERATION -> DeviceSlotType.ACCELERATION;
            case OVERLOAD_EXECUTION -> DeviceSlotType.OVERLOAD_EXECUTION;
        };
    }

    @Override
    public String moduleTypeId(ItemStack stack) {
        return type.getSerializedName();
    }

    @Override
    public List<DeviceCapability> capabilities(ItemStack stack) {
        return capabilitiesFor(type);
    }

    static List<DeviceCapability> capabilitiesFor(RailgunModuleType type) {
        // Per-stack contribution. Aggregation (count of N modules) is done by services
        // iterating the resolver output — the same way the legacy *Count() helpers worked.
        return switch (type) {
            case CORE -> List.of(new DeviceCapability.LightningCompensation(
                    LightningCompensationPolicy.DEFAULT_HIGH_VOLTAGE_PER_EXTREME_HIGH_VOLTAGE));
            case COMPUTE -> List.of(
                    new DeviceCapability.ChainTuning(2, 1, 0),
                    new DeviceCapability.PulseTuning(1.5D, 1.0D));
            case ACCELERATION -> List.of(new DeviceCapability.AccelerationFactor(0.30D));
            case OVERLOAD_EXECUTION -> List.of(new DeviceCapability.OverloadExecutionTuning(0.02D, 200, 8));
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ModuleTooltip.appendInstallInfo(this, tooltip);
    }
}
