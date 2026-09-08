package com.moakiee.ae2lt.item;

import appeng.menu.locator.ItemMenuHostLocator;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWctIntegration;
import com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public final class TianshuWirelessCraftingTerminalItem extends ItemWT {
    @Override public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return TianshuWirelessCraftingTermMenu.TYPE;
    }
    @Override public String getDescriptionId() { return "item.ae2lt.wireless_tianshu_crafting_terminal"; }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (entity instanceof ServerPlayer player && ModList.get().isLoaded("ae2wtlib")) TianshuWctIntegration.tick(player);
    }
}
