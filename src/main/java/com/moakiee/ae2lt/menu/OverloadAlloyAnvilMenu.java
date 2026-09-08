package com.moakiee.ae2lt.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Uses the native anvil recipe, level cost, repair penalty and NeoForge callbacks. */
public final class OverloadAlloyAnvilMenu extends AnvilMenu {
    public OverloadAlloyAnvilMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(id, inventory, access);
    }
}
