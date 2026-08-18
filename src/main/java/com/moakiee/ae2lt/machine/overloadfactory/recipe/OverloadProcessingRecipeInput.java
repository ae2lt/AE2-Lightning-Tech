package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.moakiee.ae2lt.machine.overloadfactory.OverloadProcessingFactoryInventory;
import com.moakiee.ae2lt.recipe.RecipeContainerInput;

public final class OverloadProcessingRecipeInput extends RecipeContainerInput {
    private final List<SlotStack> slotStacks;
    private final FluidStack inputFluid;

    public OverloadProcessingRecipeInput(List<SlotStack> slotStacks, FluidStack inputFluid) {
        this.slotStacks = List.copyOf(slotStacks);
        this.inputFluid = Objects.requireNonNull(inputFluid, "inputFluid");
    }

    public List<SlotStack> slotStacks() {
        return slotStacks;
    }

    public FluidStack inputFluid() {
        return inputFluid;
    }

    public static OverloadProcessingRecipeInput fromInventory(
            OverloadProcessingFactoryInventory inventory,
            FluidStack inputFluid) {
        List<SlotStack> slotStacks = new ArrayList<>(OverloadProcessingFactoryInventory.INPUT_SLOT_COUNT);
        for (int slot = OverloadProcessingFactoryInventory.SLOT_INPUT_0;
             slot <= OverloadProcessingFactoryInventory.SLOT_INPUT_8;
             slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                slotStacks.add(new SlotStack(slot, stack.copy()));
            }
        }
        return new OverloadProcessingRecipeInput(List.copyOf(slotStacks), inputFluid.copy());
    }

    @Override
    public boolean isEmpty() {
        return slotStacks.isEmpty() && inputFluid.isEmpty();
    }

    @Override
    public ItemStack getItem(int index) {
        return slotStacks.get(index).stack();
    }

    @Override
    public int size() {
        return slotStacks.size();
    }

    public record SlotStack(int slot, ItemStack stack) {
        public SlotStack {
            if (slot < OverloadProcessingFactoryInventory.SLOT_INPUT_0
                    || slot > OverloadProcessingFactoryInventory.SLOT_INPUT_8) {
                throw new IllegalArgumentException("slot must be an input slot");
            }
            if (stack.isEmpty()) {
                throw new IllegalArgumentException("stack cannot be empty");
            }
            stack = stack.copy();
        }
    }
}

