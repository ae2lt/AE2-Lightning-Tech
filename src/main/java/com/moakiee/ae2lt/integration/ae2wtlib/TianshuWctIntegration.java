package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.api.upgrades.Upgrades;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import de.mari_023.ae2wtlib.AE2wtlibItems;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.WUTHandler;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Loaded only with the full AE2WTLib mod. */
public final class TianshuWctIntegration {
    private TianshuWctIntegration() {}
    public static TianshuWirelessCraftingTermMenu createMenu(int id, Inventory inventory, TianshuWirelessCraftingTermMenuHost host) {
        return new TianshuEnhancedWirelessCraftingMenu(id, inventory, host);
    }
    public static void registerUpgrades() {
        Upgrades.add(AE2wtlibItems.MAGNET_CARD, ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get(), 1);
    }
    public static boolean hasTianshuCrafting(ItemStack stack) {
        return WTDefinition.exists(Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME)
                && WUTHandler.hasTerminal(stack, WTDefinition.of(Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME));
    }
    public static void tick(ServerPlayer player) {
        // Use the native selected terminal for both settings and execution. Its executor deduplicates per player/tick.
        var selected = CraftingTerminalHandler.getCraftingTerminalHandler(player).getCraftingTerminal();
        if (!selected.isEmpty()) MagnetHandler.handle(player, selected);
    }
}
