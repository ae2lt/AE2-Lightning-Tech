package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.integration.modules.curios.CuriosIntegration;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WUTHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Uses the library's inventory/Curios locator, including a Tianshu module inside a WUT. */
public final class TianshuWirelessIngredientSource {
    private TianshuWirelessIngredientSource() {}

    public static List<ItemMenuHostLocator> locate(Player player) {
        var result = new ArrayList<ItemMenuHostLocator>();
        var curios = player.getCapability(CuriosIntegration.ITEM_HANDLER);
        if (curios != null) {
            for (int i = 0; i < curios.getSlots(); i++) {
                if (isTianshu(curios.getStackInSlot(i))) result.add(MenuLocators.forCurioSlot(i));
            }
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isTianshu(player.getInventory().getItem(i))) result.add(MenuLocators.forInventorySlot(i));
        }
        return result;
    }

    private static boolean isTianshu(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemWT)) return false;
        for (var name : List.of(Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME, Ae2wtlibIntegration.TIANSHU_TERMINAL_NAME)) {
            if (WTDefinition.exists(name) && WUTHandler.hasTerminal(stack, WTDefinition.of(name))) return true;
        }
        return false;
    }

    @Nullable
    public static TianshuWirelessCraftingTermMenuHost open(Player player, ItemMenuHostLocator locator) {
        var stack = locator.locateItem(player);
        if (!(stack.getItem() instanceof ItemWT item)) return null;
        if (!isTianshu(stack)) return null;
        var host = new TianshuWirelessCraftingTermMenuHost(item, player, locator, (p, menu) -> {});
        host.updateConnectedAccessPoint();
        host.updateLinkStatus();
        var node = host.getActionableNode();
        return host.isValid() && host.getLinkStatus().connected() && node != null
                && node.getGrid().getEnergyService().isNetworkPowered() ? host : null;
    }
}
