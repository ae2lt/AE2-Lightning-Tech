package com.moakiee.ae2lt.event.railgun;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;

import appeng.api.features.IGridLinkableHandler;
import appeng.api.ids.AEComponents;

import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;

/**
 * AE2 wireless link handler — lets the railgun be inserted into a wireless
 * access point's link slot to bind it to that grid (mirrors AE2's standard
 * wireless terminal binding flow).
 */
public final class RailgunGridLinkHandler implements IGridLinkableHandler {

    public static final RailgunGridLinkHandler INSTANCE = new RailgunGridLinkHandler();

    private RailgunGridLinkHandler() {}

    @Override
    public boolean canLink(ItemStack stack) {
        return stack.getItem() instanceof ElectromagneticRailgunItem;
    }

    @Override
    public void link(ItemStack stack, GlobalPos pos) {
        stack.set(AEComponents.WIRELESS_LINK_TARGET, pos);
    }

    @Override
    public void unlink(ItemStack stack) {
        stack.remove(AEComponents.WIRELESS_LINK_TARGET);
    }
}
