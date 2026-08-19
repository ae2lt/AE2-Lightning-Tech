package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.Direction;
import net.pedroksl.advanced_ae.common.patterns.AdvPatternDetails;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AdvancedAECompatTest {
    @BeforeAll
    static void enableAdvancedAeTestDouble() throws ReflectiveOperationException {
        var loaded = AdvancedAECompat.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.set(null, true);
    }

    @AfterAll
    static void resetAdvancedAeDetection() throws ReflectiveOperationException {
        var loaded = AdvancedAECompat.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.set(null, null);
    }

    @Test
    void resolvesDirectionsThroughPatternWrappers() {
        IPatternDetails wrapped = new Wrapper(new DirectionalPattern());

        assertEquals(Direction.NORTH, AdvancedAECompat.getDirectionForKey(wrapped, null));
    }

    private static final class Wrapper implements IPatternDetails, IWrappedPatternDetails {
        private final IPatternDetails delegate;

        private Wrapper(IPatternDetails delegate) {
            this.delegate = delegate;
        }

        @Override
        public IPatternDetails wrappedPatternDetails() {
            return delegate;
        }

        @Override
        public AEItemKey getDefinition() {
            return delegate.getDefinition();
        }

        @Override
        public IInput[] getInputs() {
            return delegate.getInputs();
        }

        @Override
        public GenericStack[] getOutputs() {
            return delegate.getOutputs();
        }
    }

    private static final class DirectionalPattern implements IPatternDetails, AdvPatternDetails {
        @Override
        public boolean directionalInputsSet() {
            return true;
        }

        @Override
        public HashMap<AEKey, Direction> getDirectionMap() {
            return new HashMap<>();
        }

        @Override
        public Direction getDirectionSideForInputKey(AEKey key) {
            return Direction.NORTH;
        }

        @Override
        public void pushInputsToExternalInventory(
                KeyCounter[] inputs, PatternInputSink inputSink) {
        }

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public GenericStack[] getOutputs() {
            return new GenericStack[0];
        }
    }
}
