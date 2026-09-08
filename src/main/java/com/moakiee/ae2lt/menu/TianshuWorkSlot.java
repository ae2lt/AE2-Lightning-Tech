package com.moakiee.ae2lt.menu;

import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Keeps vanilla result callbacks attached to their original menu and inventory. */
final class TianshuWorkSlot extends Slot {
    private final Slot delegate;
    private final BooleanSupplier enabled;
    final boolean result;

    TianshuWorkSlot(Slot delegate, BooleanSupplier enabled, boolean result) {
        super(delegate.container, delegate.getContainerSlot(), 0, 0);
        this.delegate = delegate;
        this.enabled = enabled;
        this.result = result;
    }

    @Override public boolean isActive() { return enabled.getAsBoolean(); }
    @Override public boolean mayPlace(ItemStack stack) { return isActive() && delegate.mayPlace(stack); }
    @Override public boolean mayPickup(Player player) { return isActive() && delegate.mayPickup(player); }
    @Override public int getMaxStackSize() { return delegate.getMaxStackSize(); }
    @Override public int getMaxStackSize(ItemStack stack) { return delegate.getMaxStackSize(stack); }
    @Override public void setChanged() { delegate.setChanged(); }
    @Override public ItemStack remove(int amount) { return delegate.remove(amount); }
    @Override public void onTake(Player player, ItemStack stack) { delegate.onTake(player, stack); }
    @Override public void onQuickCraft(ItemStack current, ItemStack original) { delegate.onQuickCraft(current, original); }
}
