package com.moakiee.ae2lt.machine.firmament.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.machine.firmament.FirmamentConversionInventory;

/**
 * Read-only recipe input for firmament conversion. 1.20.1 has no
 * {@code net.minecraft.world.item.crafting.RecipeInput} (added in 1.21), so this
 * implements {@link Container} to satisfy {@code Recipe<C extends Container>};
 * mutation methods are no-ops by design.
 */
public final class FirmamentConversionRecipeInput implements Container {
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
    public int getContainerSize() {
        return displayStacks.size();
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
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
