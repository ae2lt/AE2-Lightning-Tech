package com.moakiee.ae2lt.integration.ae2wtlib;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import de.mari_023.ae2wtlib.terminal.ItemWT;
import de.mari_023.ae2wtlib.wut.ItemWUT;

import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;

/**
 * ae2wtlib-aware variant of the wireless Tianshu terminal item.
 *
 * <p>Only loaded and registered when AE2WTLib is present. Extending {@link ItemWT} directly keeps
 * its access-point and quantum-bridge lookup without constructing an unregistered helper item.</p>
 */
public final class TianshuWTItem extends ItemWT {
    private static final String DESCRIPTION_ID =
            Ae2wtlibIntegration.TIANSHU_TERMINAL_DESCRIPTION_ID;

    public TianshuWTItem() {
        super();
    }

    @Override
    public MenuType<?> getMenuType() {
        return TianshuWirelessPatternEncodingTermMenu.TYPE;
    }

    @Override
    public MenuType<?> getMenuType(ItemStack stack) {
        return TianshuWirelessPatternEncodingTermMenu.TYPE;
    }

    @Override
    public boolean checkUniversalPreconditions(ItemStack stack, Player player) {
        if (stack.isEmpty()
                || player.level().isClientSide()
                || (stack.getItem() != this && !(stack.getItem() instanceof ItemWUT))) {
            return false;
        }

        // A resolved frequency route is authoritative. In particular, do not open against an
        // unrelated native wireless link when the selected frequency network is out of power.
        var frequencyNode = WirelessTerminalFrequencyLink.resolve(player, stack);
        if (frequencyNode != null) {
            return WirelessTerminalFrequencyLink.isNetworkPowered(frequencyNode);
        }
        return super.checkUniversalPreconditions(stack, player);
    }

    @Override
    public String getDescriptionId() {
        return DESCRIPTION_ID;
    }
}
