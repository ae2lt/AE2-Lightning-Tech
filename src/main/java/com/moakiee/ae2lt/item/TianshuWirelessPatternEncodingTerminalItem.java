package com.moakiee.ae2lt.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.core.AEConfig;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessPatternEncodingTermMenuHost;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;

/**
 * Hand-held Tianshu pattern encoding terminal.
 *
 * <p>The base class is AE2's own {@link WirelessTerminalItem} so the item works
 * without ae2wtlib. Pattern logic and Tianshu authoring state live in the
 * item's NBT, so drafts survive closing and reopening.</p>
 *
 * <p>1.20.1 note: this class must not reference any ae2wtlib type, even under
 * an {@code isLoaded} guard — the class-file verifier resolves bytecode-level
 * references while the item is being loaded during registration, and a missing
 * ae2wtlib would crash the game before the guard could run.</p>
 */
public class TianshuWirelessPatternEncodingTerminalItem extends WirelessTerminalItem {
    private static final String DESCRIPTION_ID =
            "item.ae2lt.wireless_tianshu_pattern_encoding_terminal";

    public TianshuWirelessPatternEncodingTerminalItem() {
        super(AEConfig.instance().getWirelessTerminalBattery(), new net.minecraft.world.item.Item.Properties().stacksTo(1));
    }

    /**
     * The no-arg variant is what AE2's own {@link WirelessTerminalItem#use}
     * opens; the inherited default returns MEStorageMenu.WIRELESS_TYPE, so it
     * must point at the Tianshu terminal instead.
     */
    @Override
    public MenuType<?> getMenuType() {
        return TianshuWirelessPatternEncodingTermMenu.TYPE;
    }

    @Override
    public String getDescriptionId() {
        return DESCRIPTION_ID;
    }

    @Nullable
    @Override
    public ItemMenuHost getMenuHost(Player player, int slot, ItemStack stack, @Nullable BlockPos pos) {
        return new TianshuWirelessPatternEncodingTermMenuHost(player, slot, stack,
                (p, subMenu) -> tryOpen(player, MenuLocators.forInventorySlot(slot), stack, true));
    }

    private static boolean tryOpen(Player player, MenuLocator locator, ItemStack stack,
            boolean returningFromSubmenu) {
        return MenuOpener.open(TianshuWirelessPatternEncodingTermMenu.TYPE,
                player, locator, returningFromSubmenu);
    }
}
