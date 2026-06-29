package com.moakiee.ae2lt.logic.craft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

public final class MolecularCopyAssembler implements CopyAssembler {
    private final Supplier<Level> levelSupplier;

    public MolecularCopyAssembler(Level level) {
        this(() -> level);
    }

    public MolecularCopyAssembler(Supplier<Level> levelSupplier) {
        this.levelSupplier = Objects.requireNonNull(levelSupplier);
    }

    @Override
    public AssembledCopy assembleOneCopy(IPatternDetails details, KeyCounter[] oneCopyInputs) {
        if (!(details instanceof IMolecularAssemblerSupportedPattern pattern)) {
            return null;
        }

        Level level = levelSupplier.get();
        if (level == null) {
            return null;
        }

        CraftingInput input = buildCraftingInput(pattern, oneCopyInputs);
        ItemStack output = pattern.assemble(input, level);
        if (output.isEmpty()) {
            return null;
        }

        var remainders = new ArrayList<Stack>();
        NonNullList<ItemStack> remainingItems = pattern.getRemainingItems(input);
        if (remainingItems != null) {
            for (var remainder : remainingItems) {
                if (!remainder.isEmpty()) {
                    remainders.add(new Stack(AEItemKey.of(remainder), remainder.getCount()));
                }
            }
        }

        return new AssembledCopy(AEItemKey.of(output), output.getCount(), List.copyOf(remainders));
    }

    private static CraftingInput buildCraftingInput(IMolecularAssemblerSupportedPattern pattern,
                                                    KeyCounter[] oneCopyInputs) {
        ItemStack[] grid = new ItemStack[9];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = ItemStack.EMPTY;
        }

        pattern.fillCraftingGrid(oneCopyInputs, (slot, stack) -> {
            if (slot >= 0 && slot < grid.length && stack != null) {
                grid[slot] = stack;
            }
        });

        var items = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < grid.length; i++) {
            items.set(i, grid[i]);
        }
        return CraftingInput.of(3, 3, items);
    }
}
