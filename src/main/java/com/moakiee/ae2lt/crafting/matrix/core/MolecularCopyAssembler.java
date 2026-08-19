package com.moakiee.ae2lt.crafting.matrix.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.menu.AutoCraftingMenu;
import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputs;

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

        // AECraftingPattern.fillCraftingGrid consumes entries from the supplied counters, so
        // capture the concrete shared remainder obligations before building the grid.
        var sharedRemaindersLeft = sharedRemainderQuotas(details, oneCopyInputs);
        CraftingContainer input = buildCraftingInput(pattern, oneCopyInputs);
        ItemStack output = pattern.assemble(input, level);
        if (output.isEmpty()) {
            return null;
        }

        var remainders = new ArrayList<Stack>();
        var sharedRemainders = new ArrayList<Stack>();
        NonNullList<ItemStack> remainingItems = pattern.getRemainingItems(input);
        if (remainingItems != null) {
            for (var remainder : remainingItems) {
                if (!remainder.isEmpty()) {
                    var key = AEItemKey.of(remainder);
                    long count = remainder.getCount();
                    long sharedCount = Math.min(count, Math.max(0L,
                            sharedRemaindersLeft.getOrDefault(key, 0L)));
                    if (sharedCount > 0) {
                        sharedRemainders.add(new Stack(key, sharedCount));
                        sharedRemaindersLeft.put(key,
                                sharedRemaindersLeft.getOrDefault(key, 0L) - sharedCount);
                    }
                    if (count > sharedCount) {
                        remainders.add(new Stack(key, count - sharedCount));
                    }
                }
            }
        }

        return new AssembledCopy(AEItemKey.of(output), output.getCount(),
                List.copyOf(remainders), List.copyOf(sharedRemainders));
    }

    private static HashMap<AEKey, Long> sharedRemainderQuotas(
            IPatternDetails details, KeyCounter[] oneCopyInputs) {
        var result = new HashMap<AEKey, Long>();
        var inputs = details.getInputs();
        for (int slot = 0; slot < inputs.length && slot < oneCopyInputs.length; slot++) {
            AEKey concreteKey = selectedKey(oneCopyInputs[slot]);
            if (!SharedBatchInputs.isSharedInput(details, slot, concreteKey)) continue;
            AEKey remaining = inputs[slot].getRemainingKey(concreteKey);
            if (remaining != null) {
                result.merge(remaining, inputs[slot].getMultiplier(),
                        MolecularCopyAssembler::saturatingAdd);
            }
        }
        return result;
    }

    private static AEKey selectedKey(KeyCounter input) {
        if (input == null) return null;
        for (var entry : input) {
            if (entry.getKey() != null && entry.getLongValue() > 0) return entry.getKey();
        }
        return null;
    }

    private static long saturatingAdd(long left, long right) {
        if (left <= 0) return Math.max(0L, right);
        if (right <= 0) return left;
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static CraftingContainer buildCraftingInput(IMolecularAssemblerSupportedPattern pattern,
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

        var container = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        for (int i = 0; i < grid.length; i++) {
            container.setItem(i, grid[i]);
        }
        return container;
    }
}
