package com.moakiee.ae2lt.client;

import appeng.client.gui.me.items.CraftingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Standard AE2 crafting-terminal presentation for the Pigmee station. */
public final class PigmeeSynthesisStationScreen
        extends CraftingTermScreen<PigmeeSynthesisStationMenu> {
    public PigmeeSynthesisStationScreen(
            PigmeeSynthesisStationMenu menu,
            Inventory playerInventory,
            Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
