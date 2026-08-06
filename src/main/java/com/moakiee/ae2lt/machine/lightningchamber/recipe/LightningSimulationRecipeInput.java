package com.moakiee.ae2lt.machine.lightningchamber.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;

public final class LightningSimulationRecipeInput implements RecipeInput {
    private final List<SlotStack> slotStacks;
    private final List<ItemStack> displayStacks;

    private LightningSimulationRecipeInput(List<SlotStack> slotStacks) {
        this.slotStacks = List.copyOf(slotStacks);
        this.displayStacks = this.slotStacks.stream()
                .map(SlotStack::stack)
                .toList();
    }

    public static LightningSimulationRecipeInput fromInventory(LightningSimulationChamberInventory inventory) {
        List<SlotStack> slotStacks = new ArrayList<>(3);
        for (int slot = LightningSimulationChamberInventory.SLOT_INPUT_0;
             slot <= LightningSimulationChamberInventory.SLOT_INPUT_2;
             slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                slotStacks.add(new SlotStack(slot, stack.copy()));
            }
        }
        return new LightningSimulationRecipeInput(slotStacks);
    }

    public List<SlotStack> slotStacks() {
        return slotStacks;
    }

    public boolean isEmpty() {
        return slotStacks.isEmpty();
    }

    @Override
    public ItemStack getItem(int index) {
        return displayStacks.get(index);
    }

    @Override
    public int size() {
        return displayStacks.size();
    }

    public record SlotStack(int slot, ItemStack stack) {
        public SlotStack {
            if (slot < LightningSimulationChamberInventory.SLOT_INPUT_0
                    || slot > LightningSimulationChamberInventory.SLOT_INPUT_2) {
                throw new IllegalArgumentException("slot must be one of the three input slots");
            }
            if (stack.isEmpty()) {
                throw new IllegalArgumentException("stack cannot be empty");
            }
            stack = stack.copy();
        }
    }
}
