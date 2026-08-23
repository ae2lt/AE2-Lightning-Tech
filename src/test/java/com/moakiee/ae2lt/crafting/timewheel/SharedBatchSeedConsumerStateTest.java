package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SharedBatchSeedConsumerStateTest {
    @Test
    void unmarkedConsumerKeepsOrdinaryPerCopyExpansionDemand() {
        var state = new SharedBatchSeedConsumerState();
        var consumer = UUID.randomUUID();

        assertEquals(6L, state.pendingDemand(consumer, 1L, 6L));
        assertEquals(6L, state.pendingDemand(null, 1L, 6L));
        assertEquals(Long.MAX_VALUE,
                state.pendingDemand(consumer, Long.MAX_VALUE, 2L));
    }

    @Test
    void successfulSharedBatchStopsExpansionOnlyForThatConsumer() {
        var state = new SharedBatchSeedConsumerState();
        var sharedConsumer = UUID.randomUUID();
        var ordinaryConsumer = UUID.randomUUID();

        state.recordSuccessfulBatch(sharedConsumer);

        assertEquals(0L, state.pendingDemand(sharedConsumer, 1L, 6L));
        assertEquals(6L, state.pendingDemand(ordinaryConsumer, 1L, 6L));
    }

    @ParameterizedTest
    @MethodSource("batchSplits")
    void sharedConsumerNeedsNoAdditionalFinalOutputRetentionAfterFirstBatch(int[] batches) {
        var state = new SharedBatchSeedConsumerState();
        var consumer = UUID.randomUUID();
        long remaining = 10L;

        assertEquals(10L, state.pendingDemand(consumer, 1L, remaining));
        for (int batch : batches) {
            state.recordSuccessfulBatch(consumer);
            remaining -= batch;
            assertEquals(0L, state.pendingDemand(consumer, 1L, remaining));
        }
        assertEquals(0L, remaining);
    }

    @Test
    void successfulSharedBatchStateSurvivesNbtRoundTrip() {
        var sharedConsumer = UUID.randomUUID();
        var ordinaryConsumer = UUID.randomUUID();
        var saved = new CompoundTag();
        var original = new SharedBatchSeedConsumerState();
        original.recordSuccessfulBatch(sharedConsumer);
        original.writeToNBT(saved);

        var restored = new SharedBatchSeedConsumerState();
        restored.readFromNBT(saved);

        assertEquals(0L, restored.pendingDemand(sharedConsumer, 1L, 6L));
        assertEquals(6L, restored.pendingDemand(ordinaryConsumer, 1L, 6L));
    }

    private static Stream<Arguments> batchSplits() {
        return Stream.of(
                Arguments.of((Object) new int[] { 10 }),
                Arguments.of((Object) new int[] { 4, 3, 3 }),
                Arguments.of((Object) new int[] { 3, 3, 2, 1, 1 }),
                Arguments.of((Object) new int[] { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 }));
    }
}
