package com.moakiee.ae2lt.menu;

import appeng.menu.implementations.MenuTypeBuilder;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** Wireless variant of the Tianshu pattern-encoding terminal menu. */
public final class TianshuWirelessPatternEncodingTermMenu extends TianshuPatternEncodingTermMenu {
    private static final MenuTypeBuilder.MenuFactory<
            TianshuWirelessPatternEncodingTermMenu, TianshuPatternTerminalHost> FACTORY =
                    TianshuWirelessPatternEncodingTermMenu::new;

    public static final MenuType<TianshuWirelessPatternEncodingTermMenu> TYPE = Ae2ltMenuBuilder
            .buildUnregistered(
                    MenuTypeBuilder.create(
                            FACTORY, TianshuPatternTerminalHost.class),
                    new ResourceLocation(AE2LightningTech.MODID, "wireless_tianshu_pattern_encoding_terminal"));

    public TianshuWirelessPatternEncodingTermMenu(
            int id, Inventory inventory, TianshuPatternTerminalHost host) {
        super(TYPE, id, inventory, host);
    }

    public boolean isWUT() {
        return tianshuHost.isUniversalWirelessTerminal();
    }
}
