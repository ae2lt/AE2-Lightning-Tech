package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.api.inventories.InternalInventory;
import appeng.items.contents.StackDependentSupplier;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.parts.reporting.CraftingTerminalPart;
import appeng.util.inv.SupplierInternalInventory;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Shares AE2WTLib's ordinary crafting component when installed in a universal terminal. */
public final class TianshuWirelessCraftingTermMenuHost extends WTMenuHost
        implements TianshuCraftingTerminalHost, IViewCellStorage {
    private final InternalInventory craftingGrid;

    public TianshuWirelessCraftingTermMenuHost(ItemWT item, Player player, ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
        craftingGrid = new SupplierInternalInventory<>(new StackDependentSupplier<>(this::getItemStack,
                stack -> createInv(player, stack, AEComponents.CRAFTING_INV, 9)));
    }

    @Nullable @Override public InternalInventory getSubInventory(ResourceLocation id) {
        return CraftingTerminalPart.INV_CRAFTING.equals(id) ? craftingGrid : super.getSubInventory(id);
    }
}
