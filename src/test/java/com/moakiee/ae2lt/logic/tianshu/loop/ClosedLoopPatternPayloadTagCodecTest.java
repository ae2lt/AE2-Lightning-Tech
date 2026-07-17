package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ClosedLoopPatternPayloadTagCodecTest {
    @Test
    void newMemberTagsDoNotPersistLegacySeedWaveCopies() {
        var member = new ClosedLoopMemberPattern(snapshot(), 1L);

        var tag = ClosedLoopPatternPayloadTagCodec.writeMember(member);

        assertFalse(tag.contains("SeedWaveCopies"));
        assertEquals(1L, ClosedLoopPatternPayloadTagCodec.readMember(tag).copiesPerCycle());
    }

    @Test
    void memberWithoutSeedWaveDefaultsCompatibilityAliasToCopies() {
        var tag = memberTag(100);

        var member = ClosedLoopPatternPayloadTagCodec.readMember(tag);

        assertEquals(100L, member.copiesPerCycle());
    }

    @Test
    void equalLegacySeedWaveAliasIsAccepted() {
        var tag = memberTag(100);
        tag.putLong("SeedWaveCopies", 100L);

        assertEquals(100L,
                ClosedLoopPatternPayloadTagCodec.readMember(tag).copiesPerCycle());
    }

    @Test
    void explicitNonPrimitiveSeedWaveFailsClosed() {
        var tag = memberTag(200);
        tag.putLong("SeedWaveCopies", 2L);

        assertThrows(IllegalArgumentException.class,
                () -> ClosedLoopPatternPayloadTagCodec.readMember(tag));
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

    @Test
    void oversizedPayloadIsRejectedBeforeDecodingItsMembers() {
        var tag = new CompoundTag();
        tag.putUUID("Id", java.util.UUID.randomUUID());
        var members = new net.minecraft.nbt.ListTag();
        for (int i = 0; i <= ClosedLoopPatternAnalyzer.MAX_MEMBERS; i++) {
            members.add(new CompoundTag());
        }
        tag.put("Members", members);

        assertThrows(IllegalArgumentException.class,
                () -> ClosedLoopPatternPayloadTagCodec.read(tag, null));
    }

    private static CompoundTag memberTag(long copies) {
        var tag = new CompoundTag();
        tag.put("Pattern", snapshot().toTag());
        tag.putLong("Copies", copies);
        return tag;
    }

    private static SourcePatternSnapshot snapshot() {
        return new SourcePatternSnapshot(
                ResourceLocation.fromNamespaceAndPath("ae2lt_test", "wave_codec"),
                null, null);
    }
}
