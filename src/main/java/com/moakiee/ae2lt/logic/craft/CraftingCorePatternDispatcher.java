package com.moakiee.ae2lt.logic.craft;

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

    public int pushBatch(IPatternDetails details, KeyCounter[] scaledInputs, int maxCraft) {
        if (maxCraft <= 0) return 0;
        if (!active.getAsBoolean()) return maxCraft;
        if (!loadedPattern.test(details)) return maxCraft;
        if (!(details instanceof IMolecularAssemblerSupportedPattern)) return maxCraft;
        return sink.pushBatch(details, scaledInputs, maxCraft);
    }

    @FunctionalInterface
    public interface BatchSink {
        int pushBatch(IPatternDetails details, KeyCounter[] scaledInputs, int maxCraft);
    }
}
