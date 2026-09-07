package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

class CanonicalPatternIdentityTest {
    @Test
    void runtimePatternMapsNeverInvokeThirdPartyHashingOrEquality() {
        var map = CanonicalPatternMaps.<Integer>create();
        var patterns = new IPatternDetails[1024];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = new OpaquePattern();
            map.put(patterns[i], i);
        }
        for (int i = 0; i < patterns.length; i++) {
            assertEquals(i, map.get(patterns[i]));
            assertTrue(map.containsKey(patterns[i]));
            assertEquals(i, map.remove(patterns[i]));
        }
        assertTrue(map.isEmpty());
    }

    @Test
    void compoundKeyKeepsTargetValueEqualityAndPatternIdentity() {
        var pattern = new OpaquePattern();
        var first = new TargetPatternKey<>(new String("target"), pattern);
        var equivalent = new TargetPatternKey<>(new String("target"), pattern);
        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertNotEquals(first, new TargetPatternKey<>("target", new OpaquePattern()));
        var map = new HashMap<TargetPatternKey<String>, String>();
        map.put(first, "owned");
        assertEquals("owned", map.get(equivalent));
        assertEquals(new TargetPatternKey<>("target", null), new TargetPatternKey<>("target", null));
    }

    private static final class OpaquePattern implements IPatternDetails {
        public AEItemKey getDefinition() { return null; }
        public IInput[] getInputs() { return new IInput[0]; }
        public List<GenericStack> getOutputs() { return List.of(); }
        public boolean equals(Object other) { throw new AssertionError("third-party equality called"); }
        public int hashCode() { throw new AssertionError("third-party hashCode called"); }
    }
}
