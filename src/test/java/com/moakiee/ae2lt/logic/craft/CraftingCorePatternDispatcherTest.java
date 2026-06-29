package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

class CraftingCorePatternDispatcherTest {
    @Test
    void rejectsPatternsThatAreNotLoadedByTheProvider() {
        var loaded = new CraftingPattern();
        var other = new CraftingPattern();
        var core = new FakeBatchCore();
        var dispatcher = new CraftingCorePatternDispatcher(() -> true, pattern -> pattern == loaded, core::pushBatch);

        int leftover = dispatcher.pushBatch(other, emptyInputs(), 7);

        assertEquals(7, leftover);
        assertEquals(0, core.calls);
    }

    @Test
    void rejectsPlainProcessingPatternsBeforeTheyReachTheCore() {
        var pattern = new PlainPattern();
        var core = new FakeBatchCore();
        var dispatcher = new CraftingCorePatternDispatcher(() -> true, p -> p == pattern, core::pushBatch);

        int leftover = dispatcher.pushBatch(pattern, emptyInputs(), 7);

        assertEquals(7, leftover);
        assertEquals(0, core.calls);
    }

    @Test
    void delegatesLoadedCraftingPatternsToTheCore() {
        var pattern = new CraftingPattern();
        var core = new FakeBatchCore();
        core.leftover = 2;
        var dispatcher = new CraftingCorePatternDispatcher(() -> true, p -> p == pattern, core::pushBatch);

        int leftover = dispatcher.pushBatch(pattern, emptyInputs(), 7);

        assertEquals(2, leftover);
        assertEquals(1, core.calls);
        assertEquals(7, core.maxCraft);
    }

    @Test
    void inactiveHostsRejectWithoutCallingTheCore() {
        var pattern = new CraftingPattern();
        var core = new FakeBatchCore();
        BooleanSupplier inactive = () -> false;
        Predicate<IPatternDetails> loaded = p -> p == pattern;
        var dispatcher = new CraftingCorePatternDispatcher(inactive, loaded, core::pushBatch);

        int leftover = dispatcher.pushBatch(pattern, emptyInputs(), 7);

        assertEquals(7, leftover);
        assertEquals(0, core.calls);
    }

    private static KeyCounter[] emptyInputs() {
        return new KeyCounter[0];
    }

    private static final class FakeBatchCore {
        int calls;
        int maxCraft;
        int leftover;

        int pushBatch(IPatternDetails details, KeyCounter[] scaledInputs, int maxCraft) {
            calls++;
            this.maxCraft = maxCraft;
            return leftover;
        }
    }

    private static class PlainPattern implements IPatternDetails {
        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }
    }

    private static final class CraftingPattern extends PlainPattern implements IMolecularAssemblerSupportedPattern {
        @Override
        public ItemStack assemble(CraftingInput input, Level level) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isItemValid(int slotIndex, AEItemKey key, Level level) {
            return true;
        }

        @Override
        public boolean isSlotEnabled(int slot) {
            return true;
        }

        @Override
        public void fillCraftingGrid(KeyCounter[] inputHolder, CraftingGridAccessor accessor) {
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            return NonNullList.create();
        }
    }
}
