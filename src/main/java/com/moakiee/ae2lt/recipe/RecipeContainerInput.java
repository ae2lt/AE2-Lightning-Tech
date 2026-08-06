package com.moakiee.ae2lt.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// Shared Container scaffolding for AE2LT recipe inputs. Domain logic lives in
// concrete subclasses; this base only satisfies the vanilla Container contract
// required by Recipe<C extends Container>.
public abstract class RecipeContainerInput implements Container {
    public abstract int size();

    @Override
    public abstract ItemStack getItem(int slot);

    @Override
    public abstract boolean isEmpty();

    @Override
    public final int getContainerSize() {
        return size();
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
    }
}
