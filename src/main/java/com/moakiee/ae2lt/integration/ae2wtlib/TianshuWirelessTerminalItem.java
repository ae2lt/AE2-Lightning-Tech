package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public final class TianshuWirelessTerminalItem extends ItemWT {
    @Override
    public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return TianshuWirelessPatternEncodingTermMenu.TYPE;
    }
}
