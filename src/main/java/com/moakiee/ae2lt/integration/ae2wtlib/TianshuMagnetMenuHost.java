package com.moakiee.ae2lt.integration.ae2wtlib;

import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import de.mari_023.ae2wtlib.wct.WCTMenuHost;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHost;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Binds the native magnet menu to the terminal being edited instead of the global first terminal. */
public final class TianshuMagnetMenuHost extends WCTMenuHost {
    private final MagnetHost magnetHost;

    TianshuMagnetMenuHost(Player player, TianshuWirelessCraftingTermMenuHost terminalHost) {
        super((ItemWT) terminalHost.getItemStack().getItem(), player, terminalHost.getLocator(), (p, menu) -> {});
        magnetHost = new MagnetHost(new CraftingTerminalHandler(player) {
            @Override public ItemStack getCraftingTerminal() { return terminalHost.getItemStack(); }
        });
    }

    public MagnetHost getBoundMagnetHost() { return magnetHost; }
}
