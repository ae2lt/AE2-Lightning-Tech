package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.thunderbolt.ae2.api.crafting.IProviderLookupPattern;

class OverloadedProviderPatternCatalogTest {

    @Test
    void coldEquivalentDetailsAreCanonicalizedThenCachedByIdentity() {
        var equalityCalls = new AtomicInteger();
        var canonical = new LogicalPattern("same", equalityCalls);
        var equivalentExecutionDetails = new LogicalPattern("same", equalityCalls);
        var catalog = new OverloadedProviderPatternCatalog();

        catalog.register(canonical);

        assertSame(canonical, catalog.resolve(canonical));
        assertSame(canonical, catalog.resolve(equivalentExecutionDetails));
        assertTrue(equalityCalls.get() > 0);

        int coldEqualityCalls = equalityCalls.get();
        equivalentExecutionDetails.throwOnEquality = true;
        assertSame(canonical, catalog.resolve(equivalentExecutionDetails));
        assertEquals(coldEqualityCalls, equalityCalls.get());
    }

    @Test
    void hashCollisionStillUsesPatternSemanticsOnColdMiss() {
        var equalityCalls = new AtomicInteger();
        var canonical = new LogicalPattern("registered", equalityCalls);
        var collision = new LogicalPattern("other", equalityCalls);
        var catalog = new OverloadedProviderPatternCatalog();

        catalog.register(canonical);

        assertNull(catalog.resolve(collision));
        assertTrue(equalityCalls.get() > 0);
    }

    @Test
    void providerLookupWrapperIsCachedWithoutWrapperEquality() {
        var canonical = new ExplosiveEqualityPattern();
        var wrapper = new ProviderLookupWrapper(canonical);
        var catalog = new OverloadedProviderPatternCatalog();

        catalog.register(canonical);

        assertSame(canonical, catalog.resolve(wrapper));
        assertSame(canonical, catalog.resolve(wrapper));
        assertEquals(0, wrapper.hashCalls);
    }

    private static class ExplosiveEqualityPattern
            implements IPatternDetails {
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
            return 31;
        }
    }

    private static final class LogicalPattern implements IPatternDetails {
        private final String id;
        private final AtomicInteger equalityCalls;
        private boolean throwOnEquality;

        private LogicalPattern(String id, AtomicInteger equalityCalls) {
            this.id = id;
            this.equalityCalls = equalityCalls;
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
        public List<GenericStack> getOutputs() {
            return List.of();
        }

        @Override
        public boolean equals(Object other) {
            equalityCalls.incrementAndGet();
            if (throwOnEquality) {
                throw new AssertionError("cached identity must not re-run equality");
            }
            return other instanceof LogicalPattern pattern && id.equals(pattern.id);
        }

        @Override
        public int hashCode() {
            return 31;
        }
    }

    private static final class ProviderLookupWrapper
            extends ExplosiveEqualityPattern
            implements IProviderLookupPattern {
        private final IPatternDetails delegate;
        private int hashCalls;

        private ProviderLookupWrapper(IPatternDetails delegate) {
            this.delegate = delegate;
        }

        @Override
        public IPatternDetails providerLookupPattern() {
            return delegate;
        }

        @Override
        public int hashCode() {
            hashCalls++;
            throw new AssertionError("provider wrapper hashCode must not run");
        }
    }
}
