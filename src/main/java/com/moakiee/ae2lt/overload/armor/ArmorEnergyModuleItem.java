package com.moakiee.ae2lt.overload.armor;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.ModuleTooltip;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.item.railgun.RailgunEnergyModuleRules;
import com.moakiee.ae2lt.util.EnergyText;

public final class ArmorEnergyModuleItem extends Item implements OverloadDeviceModuleItem {
    private static final Set<DeviceKind> ACCEPTS = RailgunEnergyModuleRules.acceptedDeviceKinds();
    public static final String MODULE_TYPE_ID = "energy";

    private final long capacityFe;

    public ArmorEnergyModuleItem(Properties properties, long capacityFe) {
        super(properties);
        this.capacityFe = Math.max(0L, capacityFe);
    }

    public long capacityFe() {
        return capacityFe;
    }

    @Override
    public Set<DeviceKind> acceptableDevices() {
        return ACCEPTS;
    }

    @Override
    public DeviceSlotType acceptableSlot() {
        return DeviceSlotType.OVERLOAD_EXECUTION;
    }

    public static DeviceSlotType acceptableSlotFor(DeviceKind kind) {
        return switch (kind) {
            case CELESTWEAVE_OCULUS -> DeviceSlotType.HEAD_MODULE;
            case CELESTWEAVE_CORE -> DeviceSlotType.CHEST_MODULE;
            case CELESTWEAVE_CONDUIT -> DeviceSlotType.LEGS_MODULE;
            case CELESTWEAVE_STRIDE -> DeviceSlotType.FEET_MODULE;
            case RAILGUN -> DeviceSlotType.OVERLOAD_EXECUTION;
        };
    }

    @Override
    public List<DeviceCapability> capabilities(ItemStack stack) {
        return List.of(new DeviceCapability.EnergyCapacity(capacityFe));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(EnergyText.capacityFe(capacityFe));
        ModuleTooltip.appendInstallInfo(this, tooltip);
    }
}
