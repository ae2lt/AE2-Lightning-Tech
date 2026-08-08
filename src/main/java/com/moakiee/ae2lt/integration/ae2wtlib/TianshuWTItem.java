package com.moakiee.ae2lt.integration.ae2wtlib;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;

import de.mari_023.ae2wtlib.terminal.IUniversalWirelessTerminalItem;
import de.mari_023.ae2wtlib.terminal.ItemWT;

import com.moakiee.ae2lt.item.TianshuWirelessPatternEncodingTerminalItem;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;

/**
 * ae2wtlib-aware variant of the wireless Tianshu terminal item.
 *
 * <p>Registered instead of the base item when ae2wtlib is present at runtime so
 * {@link Ae2wtlibIntegration#registerTerminal()} can hand the exact registered
 * instance to the WUT handler (which matches items by identity). The AP-link
 * / quantum-bridge grid lookup is delegated to a throwaway {@link ItemWT}
 * instance so the same behaviour as the pre-split implementation is kept.
 * Only loaded when ae2wtlib is on the classpath.</p>
 */
public final class TianshuWTItem extends TianshuWirelessPatternEncodingTerminalItem
        implements IUniversalWirelessTerminalItem {

    private final ItemWT linkedGridSupport = new ItemWT() {
        @Override
        public MenuType<?> getMenuType(ItemStack stack) {
            return TianshuWirelessPatternEncodingTermMenu.TYPE;
        }

        @Override
        public IGrid getLinkedGrid(ItemStack stack, Level level, Player player) {
            return null;
        }
    };

    public TianshuWTItem() {
        super();
    }

    @Override
    public MenuType<?> getMenuType(ItemStack stack) {
        return TianshuWirelessPatternEncodingTermMenu.TYPE;
    }

    @Override
    public IGrid getLinkedGrid(ItemStack stack, Level level, Player player) {
        return linkedGridSupport.getLinkedGrid(stack, level, player);
    }
}
