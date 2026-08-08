package com.moakiee.ae2lt.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.fml.ModList;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.core.AEConfig;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.integration.ae2wtlib.Ae2wtlibIntegration;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessPatternEncodingTermMenuHost;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;

/**
 * Hand-held Tianshu pattern encoding terminal.
 *
 * <p>The base class is AE2's own {@link WirelessTerminalItem} so the item works
 * without ae2wtlib. When ae2wtlib is loaded at runtime the registered item
 * instance is {@code TianshuWTItem} (an ae2wtlib-aware subclass used by
 * {@link Ae2wtlibIntegration} for the Wireless Universal Terminal), and
 * {@link #getMenuHost} dispatches to the ae2wtlib menu-host variant so the
 * frequency-card remote link keeps working. Pattern logic and Tianshu
 * authoring state live in the item's NBT, so drafts survive closing and
 * reopening.</p>
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
        if (ModList.get().isLoaded("ae2wtlib")) {
            return Ae2wtlibIntegration.createMenuHost(player, slot, stack);
        }
        return new TianshuWirelessPatternEncodingTermMenuHost(player, slot, stack,
                (p, subMenu) -> tryOpen(player, MenuLocators.forInventorySlot(slot), stack, true));
    }

    private static boolean tryOpen(Player player, MenuLocator locator, ItemStack stack,
            boolean returningFromSubmenu) {
        return MenuOpener.open(TianshuWirelessPatternEncodingTermMenu.TYPE,
                player, locator, returningFromSubmenu);
    }
}
