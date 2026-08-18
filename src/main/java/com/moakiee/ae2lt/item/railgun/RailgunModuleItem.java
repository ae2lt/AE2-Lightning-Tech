package com.moakiee.ae2lt.item.railgun;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.ModuleTooltip;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;

public class RailgunModuleItem extends Item implements OverloadDeviceModuleItem {
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
        return RailgunModuleRules.maxInstallAmount(type);
    }

    @Override
    public Set<DeviceKind> acceptableDevices() {
        return RailgunModuleRules.acceptableDevices(type);
    }

    @Override
    public DeviceSlotType acceptableSlot() {
        return RailgunModuleRules.acceptableSlot(type);
    }

    @Override
    public boolean accepts(DeviceKind deviceKind, DeviceSlotType slotType) {
        return accepts(type, deviceKind, slotType);
    }

    static boolean accepts(RailgunModuleType type, DeviceKind deviceKind, DeviceSlotType slotType) {
        return RailgunModuleRules.accepts(type, deviceKind, slotType);
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
        return RailgunModuleRules.capabilitiesFor(type);
    }

    @Override
    // 1.20.1 hover text signature: the TooltipContext argument is replaced by a nullable Level.
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        ModuleTooltip.appendInstallInfo(this, tooltip);
    }
}
