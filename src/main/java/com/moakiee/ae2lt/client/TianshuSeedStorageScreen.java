package com.moakiee.ae2lt.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.menu.TianshuSeedStorageMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class TianshuSeedStorageScreen extends AEBaseScreen<TianshuSeedStorageMenu> {
    public TianshuSeedStorageScreen(
            TianshuSeedStorageMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }
}
