package com.moakiee.ae2lt.crafting.matrix.core;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

public final class CraftingCorePatternDispatcher {
    private final BooleanSupplier active;
    private final Predicate<IPatternDetails> loadedPattern;
    private final BatchSink sink;

    public CraftingCorePatternDispatcher(BooleanSupplier active,
                                         Predicate<IPatternDetails> loadedPattern,
                                         BatchSink sink) {
        this.active = Objects.requireNonNull(active);
        this.loadedPattern = Objects.requireNonNull(loadedPattern);
        this.sink = Objects.requireNonNull(sink);
    }

    public long pushBatch(IPatternDetails details, KeyCounter[] scaledInputs, long maxCraft) {
        if (maxCraft <= 0) return 0;
        if (!active.getAsBoolean()) return maxCraft;
        if (!loadedPattern.test(details)) return maxCraft;
        if (!(details instanceof IMolecularAssemblerSupportedPattern)) return maxCraft;
        return sink.pushBatch(details, scaledInputs, maxCraft);
    }

    @FunctionalInterface
    public interface BatchSink {
        long pushBatch(IPatternDetails details, KeyCounter[] scaledInputs, long maxCraft);
    }
}
