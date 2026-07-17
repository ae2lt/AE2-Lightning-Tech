package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ClosedLoopPatternPayloadTagCodecTest {
    @Test
    void legacyMemberWithoutSeedWaveKeepsAtomicCopies() {
        var tag = memberTag(100);

        var member = ClosedLoopPatternPayloadTagCodec.readMember(tag);

        assertEquals(100L, member.copiesPerCycle());
        assertEquals(100L, member.seedWaveCopies());
        assertEquals(1L, member.seedWaveRepetitions());
    }

    @Test
    void explicitSeedWaveSurvivesMemberDecoding() {
        var tag = memberTag(200);
        tag.putLong("SeedWaveCopies", 2L);

        var member = ClosedLoopPatternPayloadTagCodec.readMember(tag);

        assertEquals(200L, member.copiesPerCycle());
        assertEquals(2L, member.seedWaveCopies());
        assertEquals(100L, member.seedWaveRepetitions());
    }

    @Test
    void malformedSeedWaveFailsClosed() {
        var tag = memberTag(3);
        tag.putLong("SeedWaveCopies", 2L);

        assertThrows(IllegalArgumentException.class,
                () -> ClosedLoopPatternPayloadTagCodec.readMember(tag));
    }

    @Test
    void legacySeedMultiplierPreservesOldExecutionAndStorageBehavior() {
        var tag = new CompoundTag();
        tag.putInt("SeedMultiplier", 8);

        var multipliers = ClosedLoopPatternPayloadTagCodec.readSeedMultipliers(tag);

        assertEquals(8, multipliers.executionSeedMultiplier());
        assertEquals(1, multipliers.storedTaskMultiplier());
    }

    @Test
    void explicitSplitMultipliersOverrideLegacyAliasIndependently() {
        var tag = new CompoundTag();
        tag.putInt("SeedMultiplier", 99);
        tag.putInt("ExecutionSeedMultiplier", 4);
        tag.putInt("StoredTaskMultiplier", 7);

        var multipliers = ClosedLoopPatternPayloadTagCodec.readSeedMultipliers(tag);

        assertEquals(4, multipliers.executionSeedMultiplier());
        assertEquals(7, multipliers.storedTaskMultiplier());
    }

    @Test
    void missingOrInvalidMultipliersFallBackToOne() {
        var tag = new CompoundTag();
        tag.putInt("ExecutionSeedMultiplier", 0);
        tag.putInt("StoredTaskMultiplier", -5);

        var multipliers = ClosedLoopPatternPayloadTagCodec.readSeedMultipliers(tag);

        assertEquals(1, multipliers.executionSeedMultiplier());
        assertEquals(1, multipliers.storedTaskMultiplier());
    }

    private static CompoundTag memberTag(long copies) {
        var snapshot = new SourcePatternSnapshot(
                ResourceLocation.fromNamespaceAndPath("ae2lt_test", "wave_codec"),
                null, null);
        var tag = new CompoundTag();
        tag.put("Pattern", snapshot.toTag());
        tag.putLong("Copies", copies);
        return tag;
    }
}
