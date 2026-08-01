package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

class OverloadedProviderPatternCatalogTest {

    @Test
    void identityIndexDoesNotInvokePatternEquality() {
        var canonical = new ExplosiveEqualityPattern();
        var equivalentExecutionDetails =
                new ExplosiveEqualityPattern();
        var catalog = new OverloadedProviderPatternCatalog();

        catalog.register(canonical);

        assertSame(canonical, catalog.resolve(canonical));
        assertNull(catalog.resolve(equivalentExecutionDetails));
        assertTrue(canonical.hashCalls > 0);
        assertTrue(equivalentExecutionDetails.hashCalls > 0);
    }

    private static final class ExplosiveEqualityPattern
            implements IPatternDetails {
        private int hashCalls;

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
        public boolean equals(Object other) {
            throw new AssertionError("third-party equality must not run");
        }

        @Override
        public int hashCode() {
            hashCalls++;
            return 31;
        }
    }
}
