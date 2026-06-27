package com.moakiee.ae2lt.machine.firmament.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import com.moakiee.ae2lt.machine.firmament.FirmamentConversionInventory;

public final class FirmamentConversionRecipeInput implements RecipeInput {
    private final List<SlotStack> slotStacks;
    private final List<ItemStack> displayStacks;

    private FirmamentConversionRecipeInput(List<SlotStack> slotStacks) {
        this.slotStacks = List.copyOf(slotStacks);
        this.displayStacks = this.slotStacks.stream()
                .map(SlotStack::stack)
                .toList();
    }

    public static FirmamentConversionRecipeInput fromInventory(FirmamentConversionInventory inventory) {
        List<SlotStack> slotStacks = new ArrayList<>(3);
        for (int slot = FirmamentConversionInventory.SLOT_INPUT_0;
             slot <= FirmamentConversionInventory.SLOT_INPUT_2;
             slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                slotStacks.add(new SlotStack(slot, stack.copy()));
            }
        }
        return new FirmamentConversionRecipeInput(slotStacks);
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
            if (slot < FirmamentConversionInventory.SLOT_INPUT_0
                    || slot > FirmamentConversionInventory.SLOT_INPUT_2) {
                throw new IllegalArgumentException("slot must be one of the three input slots");
            }
            if (stack.isEmpty()) {
                throw new IllegalArgumentException("stack cannot be empty");
            }
            stack = stack.copy();
        }
    }
}
