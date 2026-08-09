package com.moakiee.ae2lt.integration.ae2wtlib;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import de.mari_023.ae2wtlib.terminal.ItemWT;

import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;

/**
 * ae2wtlib-aware variant of the wireless Tianshu terminal item.
 *
 * <p>Only loaded and registered when AE2WTLib is present. Extending {@link ItemWT} directly keeps
 * its access-point and quantum-bridge lookup without constructing an unregistered helper item.</p>
 */
public final class TianshuWTItem extends ItemWT {
    private static final String DESCRIPTION_ID =
            "item.ae2lt.wireless_tianshu_pattern_encoding_terminal";

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
    public String getDescriptionId() {
        return DESCRIPTION_ID;
    }
}
