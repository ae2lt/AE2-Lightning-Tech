package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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

import com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails;

class BatchPatternEligibilityTest {
    @Test
    void acceptsProcessingPatternsThatPushInputsToExternalInventories() {
        assertTrue(BatchPatternEligibility.isEligible(new ProcessingPattern()));
    }

    @Test
    void stillAcceptsMolecularAssemblerPatterns() {
        assertTrue(BatchPatternEligibility.isEligible(new CraftingPattern()));
    }

    @Test
    void rejectsUnsupportedPlainPatterns() {
        assertFalse(BatchPatternEligibility.isEligible(new PlainPattern()));
    }

    @Test
    void unwrapsNestedPatternAdapters() {
        var wrapped = new PatternWrapper(new PatternWrapper(new ProcessingPattern()));

        assertTrue(BatchPatternEligibility.isEligible(wrapped));
    }

    @Test
    void rejectsCyclicPatternAdapters() {
        var wrapped = new SelfWrappingPattern();

        assertFalse(BatchPatternEligibility.isEligible(wrapped));
    }

    @Test
    void preservesClosedLoopBatchSafetyGate() {
        var unsafeLoop = new ProcessingPattern();
        var safeLoop = new ProcessingPattern();
        var closedLoops = List.<IPatternDetails>of(unsafeLoop, safeLoop);

        assertFalse(BatchPatternEligibility.isEligible(
                unsafeLoop, closedLoops::contains, candidate -> candidate == safeLoop));
        assertTrue(BatchPatternEligibility.isEligible(
                safeLoop, closedLoops::contains, candidate -> candidate == safeLoop));
        assertFalse(BatchPatternEligibility.isEligible(
                new PatternWrapper(unsafeLoop),
                closedLoops::contains,
                candidate -> candidate == safeLoop));
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

        @Override
        public boolean supportsPushInputsToExternalInventory() {
            return false;
        }
    }

    private static final class ProcessingPattern extends PlainPattern {
        @Override
        public boolean supportsPushInputsToExternalInventory() {
            return true;
        }
    }

    private static final class CraftingPattern extends PlainPattern
            implements IMolecularAssemblerSupportedPattern {
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

    private static class PatternWrapper extends PlainPattern implements IWrappedPatternDetails {
        private final IPatternDetails wrapped;

        private PatternWrapper(IPatternDetails wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public IPatternDetails wrappedPatternDetails() {
            return wrapped;
        }
    }

    private static final class SelfWrappingPattern extends PlainPattern
            implements IWrappedPatternDetails {
        @Override
        public IPatternDetails wrappedPatternDetails() {
            return this;
        }
    }

}
