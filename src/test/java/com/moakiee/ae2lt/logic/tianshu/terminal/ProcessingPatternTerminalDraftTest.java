package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ProcessingPatternTerminalDraftTest {
    @Test
    void draftRemainsBoundToSlotKeysButAllowsAmountChanges() {
        var iron = new TestKey("iron");
        var plate = new TestKey("plate");
        var draft = ProcessingPatternTerminalDraft.advanced(
                Arrays.asList(new GenericStack(iron, 1), null),
                List.of(new GenericStack(plate, 1)),
                new ProcessingPatternEncodingType.AdvancedConfig(new int[] {2, 0}));

        assertTrue(draft.matches(
                Arrays.asList(new GenericStack(iron, 64), null),
                List.of(new GenericStack(plate, 3))));
        assertFalse(draft.matches(
                Arrays.asList(new GenericStack(new TestKey("gold"), 1), null),
                List.of(new GenericStack(plate, 1))));
    }

    @Test
    void configurationArraysAreDefensivelyCopied() {
        var directions = new int[] {2};
        var config = new ProcessingPatternEncodingType.AdvancedConfig(directions);
        directions[0] = 5;
        var exposed = config.directions();
        exposed[0] = 6;

        assertEquals(2, config.direction(0));
    }

    @Test
    void overloadDraftRejectsOutOfRangeSlots() {
        assertThrows(IllegalArgumentException.class, () ->
                ProcessingPatternTerminalDraft.overload(
                        List.of(new GenericStack(new TestKey("iron"), 1)),
                        List.of(new GenericStack(new TestKey("plate"), 1)),
                        new ProcessingPatternEncodingType.OverloadConfig(
                                new int[] {1}, new int[0])));
    }

    @Test
    void fullExtendedProcessingInventoriesAreAccepted() {
        var inputs = Collections.<GenericStack>nCopies(81, null);
        var outputs = Collections.<GenericStack>nCopies(27, null);

        assertDoesNotThrow(() -> ProcessingPatternTerminalDraft.advanced(
                inputs, outputs,
                new ProcessingPatternEncodingType.AdvancedConfig(new int[81])));
        assertDoesNotThrow(() -> ProcessingPatternTerminalDraft.overload(
                inputs, outputs,
                new ProcessingPatternEncodingType.OverloadConfig(
                        new int[] {80}, new int[] {26})));
    }

    @Test
    void processingInventoriesRejectSizesBeyondAe2Limits() {
        var validInputs = Collections.<GenericStack>nCopies(81, null);
        var validOutputs = Collections.<GenericStack>nCopies(27, null);
        var config = new ProcessingPatternEncodingType.AdvancedConfig(new int[0]);

        assertThrows(IllegalArgumentException.class, () ->
                ProcessingPatternTerminalDraft.advanced(
                        Collections.nCopies(82, null), validOutputs, config));
        assertThrows(IllegalArgumentException.class, () ->
                ProcessingPatternTerminalDraft.advanced(
                        validInputs, Collections.nCopies(28, null), config));
    }

    @Test
    void advancedAndOverloadConfigurationsCanCoexist() {
        var advanced = new ProcessingPatternEncodingType.AdvancedConfig(new int[] {2});
        var overload = new ProcessingPatternEncodingType.OverloadConfig(
                new int[] {0}, new int[] {0});
        var draft = ProcessingPatternTerminalDraft.configured(
                List.of(new GenericStack(new TestKey("iron"), 1)),
                List.of(new GenericStack(new TestKey("plate"), 1)),
                advanced, overload);

        assertEquals(ProcessingPatternEncodingType.ADVANCED_OVERLOAD, draft.type());
        assertTrue(draft.type().includes(ProcessingPatternEncodingType.ADVANCED));
        assertTrue(draft.type().includes(ProcessingPatternEncodingType.OVERLOAD));
        assertEquals(2, draft.advancedConfig().direction(0));
        assertTrue(draft.overloadConfig().isInputIdOnly(0));
        assertTrue(draft.overloadConfig().isOutputIdOnly(0));
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;

        private TestKey(String id) {
            this.id = id;
        }

        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag() {
            return new CompoundTag();
        }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() {
            return new ResourceLocation("ae2lt_test", id);
        }
        @Override public void writeToPacket(FriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() { return Component.literal(id); }
        @Override public void addDrops(
                long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean equals(Object other) {
            return other instanceof TestKey key && id.equals(key.id);
        }
        @Override public int hashCode() { return id.hashCode(); }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(new ResourceLocation("ae2lt_test", "processing_draft"),
                    TestKey.class, Component.literal("test"));
        }

        @Override public AEKey readFromPacket(FriendlyByteBuf input) { return null; }
        @Override public AEKey loadKeyFromTag(CompoundTag tag) { return null; }
    }
}
