package com.moakiee.ae2lt.item.staff;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;

public final class StaffModuleItem extends Item implements com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem {
    private final StaffModule module;

    public StaffModuleItem(Properties properties, StaffModule module) {
        super(properties.stacksTo(1));
        this.module = module;
    }

    public StaffModule module() { return module; }
    @Override public java.util.Set<com.moakiee.ae2lt.device.DeviceKind> acceptableDevices() {
        return java.util.Set.of(com.moakiee.ae2lt.device.DeviceKind.MIMICRY_STAFF);
    }
    @Override public com.moakiee.ae2lt.device.DeviceSlotType acceptableSlot() { return com.moakiee.ae2lt.device.DeviceSlotType.STAFF_MODULE; }
    @Override public int getMaxInstallAmount() { return 1; }
    @Override public String moduleTypeId(ItemStack stack) { return StaffModuleStorage.typeId(stack); }
    @Override public java.util.List<com.moakiee.ae2lt.device.capability.DeviceCapability> capabilities(ItemStack stack) { return java.util.List.of(); }


    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return StaffEnchantments.accepts(module, enchantment);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("ae2lt.staff.module."
                + module.name().toLowerCase(java.util.Locale.ROOT)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
