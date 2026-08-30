package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TianshuTerminalStateTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // Literal historical keys: do not derive the expected schema from the production codec.
    static Stream<Arguments> formats() {
        return Stream.of(
                Arguments.of(TianshuTerminalState.NbtFormat.PART,
                        "TianshuEncodingMode", "MaintainableView", "ClosedLoopDraft", "ProcessingDraft"),
                Arguments.of(TianshuTerminalState.NbtFormat.WIRELESS,
                        "tianshuMode", "tianshuMaintainableView", "tianshuClosedLoopDraft", "tianshuProcessingDraft"));
    }

    @Test
    void settersOnlyReportActualChangesAndRetainEqualDrafts() {
        var state = new TianshuTerminalState();
        assertEquals(TianshuEncodingMode.CRAFTING, state.getEncodingMode());
        assertFalse(state.isMaintainableView());
        assertNull(state.getClosedLoopDraft());
        assertNull(state.getProcessingDraft());
        assertFalse(state.setEncodingMode(null));
        assertFalse(state.setEncodingMode(TianshuEncodingMode.CRAFTING));
        assertFalse(state.setMaintainableView(false));
        assertFalse(state.setClosedLoopDraft(null));
        assertFalse(state.setProcessingDraft(null));
        assertTrue(state.setEncodingMode(TianshuEncodingMode.CLOSED_LOOP));
        assertTrue(state.setMaintainableView(true));
        assertFalse(state.setEncodingMode(TianshuEncodingMode.CLOSED_LOOP));
        assertFalse(state.setMaintainableView(true));

        var closed = closedLoopDraft();
        var processing = processingDraft();
        assertTrue(state.setClosedLoopDraft(closed));
        assertTrue(state.setProcessingDraft(processing));
        assertFalse(state.setClosedLoopDraft(closedLoopDraft()));
        assertFalse(state.setProcessingDraft(processingDraft()));
        assertSame(closed, state.getClosedLoopDraft());
        assertSame(processing, state.getProcessingDraft());
        assertTrue(state.setClosedLoopDraft(null));
        assertTrue(state.setProcessingDraft(null));
        assertFalse(state.setClosedLoopDraft(null));
        assertFalse(state.setProcessingDraft(null));
    }

    @ParameterizedTest
    @MethodSource("formats")
    void readsLegacyTagsAndWritesExactlyTheSameSchema(TianshuTerminalState.NbtFormat format,
            String modeKey, String viewKey, String closedKey, String processingKey) {
        var legacy = new CompoundTag();
        legacy.putString(modeKey, "CLOSED_LOOP");
        legacy.putBoolean(viewKey, true);
        legacy.put(closedKey, closedLoopDraft().write());
        legacy.put(processingKey, processingDraft().write());
        var state = new TianshuTerminalState();
        var input = legacy.copy();
        state.read(input, format);
        assertEquals(legacy, input, "Reading must not mutate the stored tag");
        assertEquals(TianshuEncodingMode.CLOSED_LOOP, state.getEncodingMode());
        assertTrue(state.isMaintainableView());
        assertTrue(ClosedLoopTerminalDraft.sameState(closedLoopDraft(), state.getClosedLoopDraft()));
        assertTrue(ProcessingPatternTerminalDraft.sameState(processingDraft(), state.getProcessingDraft()));

        var saved = new CompoundTag();
        state.write(saved, format);
        assertEquals(legacy, saved, "Do not rename keys or introduce an extra container tag");
    }

    @ParameterizedTest
    @MethodSource("formats")
    void preservesNativeAndUnknownTagsWhileRemovingClearedDrafts(TianshuTerminalState.NbtFormat format,
            String modeKey, String viewKey, String closedKey, String processingKey) {
        var nativeData = new CompoundTag();
        nativeData.putString("viewcells", "host-owned");
        nativeData.putString("singularity", "host-owned");
        nativeData.putString("craftingGrid", "logic-owned");
        nativeData.putString("foreignKey", "keep");
        var saved = nativeData.copy();
        saved.put(closedKey, closedLoopDraft().write());
        saved.put(processingKey, processingDraft().write());
        new TianshuTerminalState().write(saved, format);
        var expected = nativeData.copy();
        expected.putString(modeKey, "CRAFTING");
        expected.putBoolean(viewKey, false);
        assertEquals(expected, saved);
    }

    @ParameterizedTest
    @MethodSource("formats")
    void missingOrMalformedFieldsRestoreHistoricalDefaults(TianshuTerminalState.NbtFormat format,
            String modeKey, String viewKey, String closedKey, String processingKey) {
        var missing = new CompoundTag();
        var wrongTypes = new CompoundTag();
        wrongTypes.putInt(modeKey, 99);
        wrongTypes.putString(viewKey, "true");
        wrongTypes.putString(closedKey, "not a compound");
        wrongTypes.putString(processingKey, "not a compound");
        var malformed = new CompoundTag();
        malformed.putString(modeKey, "UNKNOWN_MODE");
        malformed.put(closedKey, new CompoundTag());
        malformed.put(processingKey, new CompoundTag());
        for (var tag : List.of(missing, wrongTypes, malformed)) {
            var state = new TianshuTerminalState();
            state.setEncodingMode(TianshuEncodingMode.CLOSED_LOOP);
            state.setMaintainableView(true);
            state.setClosedLoopDraft(closedLoopDraft());
            state.setProcessingDraft(processingDraft());
            state.read(tag, format);
            assertEquals(TianshuEncodingMode.CRAFTING, state.getEncodingMode());
            assertFalse(state.isMaintainableView());
            assertNull(state.getClosedLoopDraft());
            assertNull(state.getProcessingDraft());
        }
    }

    @ParameterizedTest
    @MethodSource("formats")
    void everyEncodingModeRoundTrips(TianshuTerminalState.NbtFormat format,
            String modeKey, String viewKey, String closedKey, String processingKey) {
        for (var mode : TianshuEncodingMode.values()) {
            var state = new TianshuTerminalState();
            state.setEncodingMode(mode);
            var saved = new CompoundTag();
            state.write(saved, format);
            assertEquals(mode.name(), saved.getString(modeKey));
            var restored = new TianshuTerminalState();
            restored.read(saved, format);
            assertEquals(mode, restored.getEncodingMode());
        }
    }

    private static ClosedLoopTerminalDraft closedLoopDraft() {
        var members = new ArrayList<>(Collections.nCopies(ClosedLoopDraftSync.MEMBER_SLOTS, ItemStack.EMPTY));
        members.set(2, new ItemStack(Items.STONE, 3));
        return new ClosedLoopTerminalDraft(new ItemStack(Items.PAPER), members,
                Collections.nCopies(ClosedLoopDraftSync.MEMBER_SLOTS, 5L),
                Collections.nCopies(ClosedLoopDraftSync.OUTPUT_SLOTS, ItemStack.EMPTY),
                Collections.nCopies(ClosedLoopDraftSync.OUTPUT_SLOTS, 2), 3, 7, true);
    }

    private static ProcessingPatternTerminalDraft processingDraft() {
        return ProcessingPatternTerminalDraft.configured(
                Collections.nCopies(3, null), Collections.nCopies(2, null),
                new ProcessingPatternEncodingType.AdvancedConfig(new int[] {2, 0, 6}),
                new ProcessingPatternEncodingType.OverloadConfig(new int[] {2}, new int[] {1}));
    }
}
