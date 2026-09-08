package com.moakiee.ae2lt.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.StonecutterMenu;

/** Identifies the terminal's native stonecutter engine for component-aware input invalidation. */
public final class TianshuStonecutterMenu extends StonecutterMenu {
    public TianshuStonecutterMenu(int id, Inventory inventory) { super(id, inventory); }
}
