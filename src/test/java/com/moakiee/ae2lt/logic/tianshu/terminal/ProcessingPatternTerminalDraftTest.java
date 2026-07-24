package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;

        private TestKey(String id) {
            this.id = id;
        }

        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            return new CompoundTag();
        }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("ae2lt_test", id);
        }
        @Override public void writeToPacket(RegistryFriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() { return Component.literal(id); }
        @Override public void addDrops(
                long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean hasComponents() { return false; }
        @Override public boolean equals(Object other) {
            return other instanceof TestKey key && id.equals(key.id);
        }
        @Override public int hashCode() { return id.hashCode(); }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("ae2lt_test", "processing_draft"),
                    TestKey.class, Component.literal("test"));
        }

        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
