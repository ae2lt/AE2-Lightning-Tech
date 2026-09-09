package com.moakiee.ae2lt.item.staff;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Dedicated, recipe-free handle. Supported staff behavior reads the private original via services. */
public final class StaffProjectionItem extends MimicryStaffItem {
    public StaffProjectionItem(Properties properties) { super(properties); }
    @Override public String getDescriptionId() { return "item.ae2lt.celestweave_mimicry_staff"; }
    @Override public boolean canFitInsideContainerItems() { return false; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("ae2lt.staff.projection"));
    }
}
