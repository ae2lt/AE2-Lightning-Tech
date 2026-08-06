package com.moakiee.ae2lt.menu.railgun;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.locator.ItemMenuHostLocator;

import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.item.railgun.RailgunModuleEntries;
import com.moakiee.ae2lt.item.railgun.RailgunModuleStorage;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.registry.ModDataComponents;

public class RailgunHost extends ItemMenuHost<ElectromagneticRailgunItem> {
    public RailgunHost(ElectromagneticRailgunItem item, Player player, ItemMenuHostLocator locator) {
        super(item, player, locator);
    }

    public RailgunModuleEntries getModules() {
        return RailgunModuleStorage.entryData(getItemStack());
    }

    public void setModules(RailgunModuleEntries m) {
        RailgunModuleStorage.setEntries(getItemStack(), m);
    }

    public RailgunSettings getSettings() {
        return getItemStack().getOrDefault(ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
    }

    public void setSettings(RailgunSettings s) {
        getItemStack().set(ModDataComponents.RAILGUN_SETTINGS.get(), s);
    }

    public ItemStack getStack() {
        return getItemStack();
    }
}
