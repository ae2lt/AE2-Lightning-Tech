package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;

/**
 * Snapshot of the machine state that's fed into {@link CrystalCatalyzerRecipe#matches}.
 *
 * <p>Only the catalyst slot is relevant now — the fluid requirement is no longer
 * part of the recipe; the machine drains a fixed water cost per cycle independent
 * of the selected recipe.</p>
 */
public final class CrystalCatalyzerRecipeInput implements RecipeInput {
    private final ItemStack catalyst;

    public CrystalCatalyzerRecipeInput(ItemStack catalyst) {
        this.catalyst = catalyst == null ? ItemStack.EMPTY : catalyst.copy();
    }

    public static CrystalCatalyzerRecipeInput fromMachine(CrystalCatalyzerInventory inventory) {
        return new CrystalCatalyzerRecipeInput(
                inventory.getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST));
    }

    public ItemStack catalyst() {
        return catalyst;
    }

    @Override
    public ItemStack getItem(int slotIndex) {
        return slotIndex == 0 ? catalyst : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 1;
    }
}
