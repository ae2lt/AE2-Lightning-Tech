package com.moakiee.ae2lt.celestweave;

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

public final class ArmorEnergyModuleItem extends Item implements OverloadDeviceModuleItem {
    private static final Set<DeviceKind> ACCEPTS = RailgunEnergyModuleRules.acceptedDeviceKinds();
    public static final String MODULE_TYPE_ID = "energy";

    private final long armorCapacityFe;
    private final long legacyCapacityFe;

    public ArmorEnergyModuleItem(Properties properties, long armorCapacityFe, long legacyCapacityFe) {
        super(properties);
        this.armorCapacityFe = Math.max(0L, armorCapacityFe);
        this.legacyCapacityFe = Math.max(0L, legacyCapacityFe);
    }

    public long armorCapacityFe() {
        return armorCapacityFe;
    }

    public long capacityFe() {
        return legacyCapacityFe;
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
        return List.of(new DeviceCapability.EnergyCapacity(legacyCapacityFe));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ModuleTooltip.appendInstallInfo(this, tooltip);
    }
}
