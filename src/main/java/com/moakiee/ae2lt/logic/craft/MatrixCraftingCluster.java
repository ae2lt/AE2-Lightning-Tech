package com.moakiee.ae2lt.logic.craft;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/**
 * Multiblock-side rate limiter that wraps a shared {@link CraftingCore} engine: it aggregates the
 * pattern units and thread units of a formed structure, caps batch copies by the structure's thread
 * budget, and schedules them on the engine. The engine itself stays a pure assembler + time wheel.
 */
public final class MatrixCraftingCluster {
    private static final String NBT_HEAT = "heat";
    private static final String NBT_LAST_LIMITER_TICK = "lastLimiterTick";

    private final BooleanSupplier formed;
    private final List<MatrixPatternCore> patternCores;
    private final List<MatrixCraftCore> craftCores;
    private final MatrixHost host;
    private final CraftingCore engine;
    private double heat;
    private long lastLimiterTick = Long.MIN_VALUE;
    private int limiterRemaining;
    private MatrixCraftingMath.Snapshot lastLimiterSnapshot = MatrixCraftingMath.idleSnapshot(0.0D, 0.0D);

    public MatrixCraftingCluster(BooleanSupplier formed,
                                 List<? extends MatrixPatternCore> patternCores,
                                 List<? extends MatrixCraftCore> craftCores,
                                 CraftingCoreHost host,
                                 CopyAssembler assembler,
                                 CraftingCoreRegistry registry) {
        this.formed = Objects.requireNonNull(formed);
        this.patternCores = new ArrayList<>(patternCores);
        this.craftCores = new ArrayList<>(craftCores);
        this.host = new MatrixHost(Objects.requireNonNull(host));
        this.engine = new CraftingCore(this.host, assembler, registry);
    }

    public void addPatternCore(MatrixPatternCore core) {
        if (core != null && !patternCores.contains(core)) {
            patternCores.add(core);
        }
    }

    public void removePatternCore(MatrixPatternCore core) {
        patternCores.remove(core);
    }

    public void addCraftCore(MatrixCraftCore core) {
        if (core != null && !craftCores.contains(core)) {
            craftCores.add(core);
        }
    }

    public void removeCraftCore(MatrixCraftCore core) {
        craftCores.remove(core);
    }

    public List<IPatternDetails> getAvailablePatterns() {
        if (!formed.getAsBoolean()) return List.of();

        var seen = new IdentityHashMap<IPatternDetails, Boolean>();
        var result = new ArrayList<IPatternDetails>();
        for (var core : patternCores) {
            for (var pattern : core.getAvailablePatterns()) {
                if (MatrixPatternRepository.isSupportedPattern(pattern) && !seen.containsKey(pattern)) {
                    seen.put(pattern, Boolean.TRUE);
                    result.add(pattern);
                }
            }
        }
        return List.copyOf(result);
    }

    public boolean hasPattern(IPatternDetails details) {
        if (!formed.getAsBoolean() || !MatrixPatternRepository.isSupportedPattern(details)) return false;
        for (var core : patternCores) {
            for (var pattern : core.getAvailablePatterns()) {
                if (pattern == details) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getBatchCapacity(IPatternDetails details) {
        if (!hasPattern(details)) return 0;
        return availableCapacity();
    }

    /**
     * Rate limiter entry point: cap copies by the structure's free thread budget and schedule them
     * on the shared engine with the structure's delay. Inputs are a single-copy template.
     */
    public int pushBatch(IPatternDetails details, KeyCounter[] oneCopyTemplate, int maxCraft) {
        if (!hasPattern(details)) return maxCraft;
        int copies = Math.min(maxCraft, availableCapacity());
        if (copies <= 0) return maxCraft;
        int accepted = engine.pushBatch(details, oneCopyTemplate, copies, MatrixCraftingMath.MATRIX_DELAY_TICKS);
        limiterRemaining = Math.max(0, limiterRemaining - accepted);
        return maxCraft - accepted;
    }

    public boolean isBusy() {
        return !formed.getAsBoolean() || availableCapacity() <= 0;
    }

    public int availableCapacity() {
        if (!formed.getAsBoolean()) return 0;
        refreshLimiterBudget();
        return limiterRemaining;
    }

    public int threadsInFlight() {
        return engine.threadsInFlight();
    }

    public MatrixCraftingProfile craftingProfile() {
        if (!formed.getAsBoolean()) return MatrixCraftingProfile.empty();

        var units = new ArrayList<MatrixCraftingUnit>();
        for (var core : craftCores) {
            if (core != null) {
                units.addAll(core.craftingUnits());
            }
        }
        return MatrixCraftingProfile.fromUnits(units);
    }

    public MatrixCraftingMath.Snapshot tickLimiter() {
        refreshLimiterBudget();
        return lastLimiterSnapshot;
    }

    public MatrixCraftingMath.Snapshot previewSnapshot() {
        return craftingProfile().snapshot(heat);
    }

    public double heat() {
        return heat;
    }

    public long lastLimiterTick() {
        return lastLimiterTick;
    }

    public MatrixCraftingMath.Snapshot lastLimiterSnapshot() {
        return lastLimiterSnapshot;
    }

    public void writeEngineTo(CompoundTag tag, HolderLookup.Provider registries) {
        engine.writeTo(tag, registries);
        tag.putDouble(NBT_HEAT, heat);
        tag.putLong(NBT_LAST_LIMITER_TICK, lastLimiterTick);
    }

    public void readEngineFrom(CompoundTag tag, HolderLookup.Provider registries) {
        engine.readFrom(tag, registries);
        heat = tag.contains(NBT_HEAT, Tag.TAG_DOUBLE) ? tag.getDouble(NBT_HEAT) : 0.0D;
        lastLimiterTick = tag.contains(NBT_LAST_LIMITER_TICK, Tag.TAG_LONG) ? tag.getLong(NBT_LAST_LIMITER_TICK) : Long.MIN_VALUE;
        limiterRemaining = 0;
        lastLimiterSnapshot = MatrixCraftingMath.idleSnapshot(heat, 0.0D);
    }

    public int totalThreadCapacity() {
        if (!formed.getAsBoolean()) return 0;
        long total = 0;
        for (var core : craftCores) {
            int capacity = core.threadCapacity();
            if (capacity > 0) {
                total += capacity;
                if (total >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return (int) total;
    }

    private void refreshLimiterBudget() {
        long now = host.getGameTime();
        if (lastLimiterTick == now) return;

        lastLimiterSnapshot = craftingProfile().snapshot(heat);
        heat = lastLimiterSnapshot.heat();
        limiterRemaining = saturateToInt(lastLimiterSnapshot.operationsPerTick());
        lastLimiterTick = now;
    }

    private static int saturateToInt(long value) {
        if (value <= 0L) return 0;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private final class MatrixHost implements CraftingCoreHost {
        private final CraftingCoreHost delegate;

        private MatrixHost(CraftingCoreHost delegate) {
            this.delegate = delegate;
        }

        @Override
        public long getGameTime() {
            return delegate.getGameTime();
        }

        @Override
        public boolean isRemoved() {
            return delegate.isRemoved();
        }

        @Override
        public boolean isConnected() {
            return formed.getAsBoolean() && delegate.isConnected();
        }

        @Override
        public long insertToNetwork(AEKey key, long amount) {
            return delegate.insertToNetwork(key, amount);
        }

        @Override
        public void spawnToWorld(AEKey key, long amount) {
            delegate.spawnToWorld(key, amount);
        }
    }
}
