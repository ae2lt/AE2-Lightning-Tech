package com.moakiee.ae2lt.crafting.matrix.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/**
 * Pure batch crafting engine: assembles one copy of a pattern, aggregates all accepted outputs in
 * one pending buffer, and flushes that buffer on a shared five-tick cadence.
 *
 * <p>This class deliberately does NOT manage thread capacity, energy or input scaling. Those are
 * the responsibility of the caller (a rate limiter that implements
 * {@link com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider}). The engine simply assembles
 * and delivers; {@link #liveThreads()} exposes state so the limiter can budget.
 */
public final class CraftingCore implements Sweepable {
    public static final int FLUSH_INTERVAL_TICKS = 5;

    private static final String NBT_PENDING = "pending";
    private static final String NBT_NEXT_FLUSH = "nextFlush";
    private static final String NBT_COPIES = "n";
    private static final String NBT_OUTPUTS = "out";
    private static final String NBT_KEY = "k";
    private static final String NBT_AMOUNT = "v";

    private final CraftingCoreHost host;
    private final CopyAssembler assembler;
    private final CraftingCoreRegistry registry;
    private final PendingBatch pending = new PendingBatch();
    private final Map<IPatternDetails, Map<InputSignature, CachedAssembly>> assemblyCache = new IdentityHashMap<>();
    private long threadsInFlight;
    private long nextFlushTick = Long.MIN_VALUE;

    public CraftingCore(CraftingCoreHost host, CopyAssembler assembler, CraftingCoreRegistry registry) {
        this.host = host;
        this.assembler = assembler;
        this.registry = registry;
    }

    /**
     * Assemble one copy of {@code details} from the single-copy input template and schedule
     * {@code copies} of its output for delivery at the next five-tick flush boundary.
     *
     * <p>The engine does not enforce any capacity, energy or scaling: the caller (rate limiter)
     * must have already decided {@code copies}. Inputs are a single-copy template (NOT multiplied
     * by {@code copies}); materials are assumed to have been extracted upstream already.
     */
    public long pushBatch(IPatternDetails details, KeyCounter[] oneCopyTemplate, long copies) {
        if (copies <= 0 || oneCopyTemplate == null) return 0;
        if (!(details instanceof IMolecularAssemblerSupportedPattern)) return 0;

        long now = host.getGameTime();
        flushIfDue(now);

        long accepted = appendableCopies(copies);
        if (accepted <= 0) {
            return 0;
        }

        CopyAssembler.AssembledCopy assembled;
        try {
            assembled = assembleOneCopyCached(details, oneCopyTemplate);
        } catch (Throwable t) {
            appeng.core.AELog.warn("[ae2lt] batch crafting core assemble failed for %s; dropping %d copies. %s",
                    details, copies, t);
            return 0;
        }

        if (assembled == null || assembled.output() == null || assembled.outputCount() <= 0) {
            return 0;
        }

        long sharedOutput = details instanceof com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern shared
                ? Math.min(assembled.outputCount(), Math.max(0L,
                        shared.sharedBatchOutputAmount(assembled.output())))
                : 0L;
        boolean wasEmpty = threadsInFlight == 0;
        accumulate(pending, assembled.output(), saturatedAdd(
                sharedOutput,
                saturatedMultiply(assembled.outputCount() - sharedOutput, accepted)));
        if (assembled.remainders() != null) {
            for (var remainder : assembled.remainders()) {
                if (remainder != null) {
                    accumulate(pending, remainder.key(), saturatedMultiply(remainder.count(), accepted));
                }
            }
        }
        if (assembled.sharedRemainders() != null) {
            for (var remainder : assembled.sharedRemainders()) {
                if (remainder != null) {
                    accumulate(pending, remainder.key(), remainder.count());
                }
            }
        }

        pending.copies = saturatedAdd(pending.copies, accepted);
        threadsInFlight = saturatedAdd(threadsInFlight, accepted);
        if (wasEmpty) {
            nextFlushTick = nextBoundaryAfter(now);
        }
        registry.markActive(this);
        return accepted;
    }

    private CopyAssembler.AssembledCopy assembleOneCopyCached(IPatternDetails details,
                                                              KeyCounter[] oneCopyTemplate) {
        var signature = InputSignature.capture(oneCopyTemplate);
        var byInput = assemblyCache.computeIfAbsent(details, ignored -> new HashMap<>());
        var cached = byInput.get(signature);
        if (cached != null) {
            return cached.cacheable() ? cached.assembled() : assembler.assembleOneCopy(details, oneCopyTemplate);
        }

        var assembled = assembler.assembleOneCopy(details, oneCopyTemplate);
        byInput.put(signature, isExpectedAssembly(details, assembled)
                ? CachedAssembly.cacheable(assembled)
                : CachedAssembly.uncacheable());
        return assembled;
    }

    private static boolean isExpectedAssembly(IPatternDetails details, CopyAssembler.AssembledCopy assembled) {
        if (assembled == null || assembled.output() == null || assembled.outputCount() <= 0) {
            return false;
        }

        for (var output : details.getOutputs()) {
            if (assembled.output().equals(output.what()) && assembled.outputCount() == output.amount()) {
                return true;
            }
        }
        return false;
    }

    /** Flush at a due boundary, then report total buffered copies (for the rate limiter). */
    public long liveThreads() {
        flushIfDue(host.getGameTime());
        return threadsInFlight;
    }

    public long threadsInFlight() {
        return threadsInFlight;
    }

    @Override
    public boolean sweepTick() {
        if (host.isRemoved()) {
            drainAll(true);
            return false;
        }
        flushIfDue(host.getGameTime());
        return threadsInFlight > 0;
    }

    public void drainAll(boolean forceSpawn) {
        if (drainPending(forceSpawn)) {
            nextFlushTick = Long.MIN_VALUE;
        } else {
            nextFlushTick = nextBoundaryAfter(host.getGameTime());
        }
    }

    /**
     * Detaches a persisted engine mirror without delivering or spawning its queued outputs. The
     * authoritative copy can then be restored into a newly linked host.
     */
    public void suspend() {
        registry.markInactive(this);
        reset();
        assemblyCache.clear();
    }

    public void writeTo(CompoundTag tag, HolderLookup.Provider registries) {
        if (threadsInFlight <= 0) return;

        var pendingTag = new CompoundTag();
        pendingTag.putLong(NBT_COPIES, pending.copies);
        pendingTag.putLong(NBT_NEXT_FLUSH, nextFlushTick);
        var outputs = writeOutputs(pending, registries);
        if (!outputs.isEmpty()) {
            pendingTag.put(NBT_OUTPUTS, outputs);
            tag.put(NBT_PENDING, pendingTag);
        }
    }

    public void readFrom(CompoundTag tag, HolderLookup.Provider registries) {
        registry.markInactive(this);
        reset();
        if (!tag.contains(NBT_PENDING, Tag.TAG_COMPOUND)) return;
        CompoundTag pendingTag = tag.getCompound(NBT_PENDING);
        readBatch(pendingTag, registries);
        long restoredNextFlush = pendingTag.getLong(NBT_NEXT_FLUSH);

        if (threadsInFlight > 0) {
            long now = host.getGameTime();
            nextFlushTick = restoredNextFlush > now
                    ? restoredNextFlush : nextBoundaryAfter(now);
            registry.markActive(this);
        }
    }

    private void flushIfDue(long now) {
        if (threadsInFlight <= 0 || now < nextFlushTick) return;
        nextFlushTick = drainPending(false)
                ? Long.MIN_VALUE : nextBoundaryAfter(now);
    }

    private boolean drainPending(boolean forceSpawn) {
        if (pending.copies <= 0) return true;

        if (forceSpawn) {
            for (Object2LongMap.Entry<AEKey> entry : pending.outputs.object2LongEntrySet()) {
                if (entry.getLongValue() > 0) {
                    host.spawnToWorld(entry.getKey(), entry.getLongValue());
                }
            }
            releasePending();
            return true;
        }

        if (!host.isConnected()) {
            return false;
        }

        boolean anyLeft = false;
        var iter = pending.outputs.object2LongEntrySet().fastIterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            long amount = entry.getLongValue();
            if (amount <= 0) {
                iter.remove();
                continue;
            }
            long inserted = host.insertToNetwork(entry.getKey(), amount);
            long leftover = amount - inserted;
            if (leftover > 0) {
                entry.setValue(leftover);
                anyLeft = true;
            } else {
                iter.remove();
            }
        }

        if (!anyLeft) {
            releasePending();
            return true;
        }
        return false;
    }

    private void releasePending() {
        pending.outputs.clear();
        pending.copies = 0;
        threadsInFlight = 0;
    }

    private void reset() {
        pending.outputs.clear();
        pending.copies = 0;
        threadsInFlight = 0;
        nextFlushTick = Long.MIN_VALUE;
    }

    private long appendableCopies(long requested) {
        long globalSpace = Long.MAX_VALUE - threadsInFlight;
        return Math.min(requested, globalSpace);
    }

    private static long nextBoundaryAfter(long now) {
        long delta = FLUSH_INTERVAL_TICKS - Math.floorMod(now, FLUSH_INTERVAL_TICKS);
        return saturatedAdd(now, delta);
    }

    private static ListTag writeOutputs(PendingBatch batch, HolderLookup.Provider registries) {
        var outputs = new ListTag();
        for (Object2LongMap.Entry<AEKey> entry : batch.outputs.object2LongEntrySet()) {
            if (entry.getKey() == null || entry.getLongValue() <= 0) continue;
            var outputTag = new CompoundTag();
            outputTag.put(NBT_KEY, entry.getKey().toTagGeneric());
            outputTag.putLong(NBT_AMOUNT, entry.getLongValue());
            outputs.add(outputTag);
        }
        return outputs;
    }

    private void readBatch(CompoundTag batchTag, HolderLookup.Provider registries) {
        long copies = batchTag.getLong(NBT_COPIES);
        if (copies <= 0) return;
        boolean restoredOutput = false;
        ListTag outputs = batchTag.getList(NBT_OUTPUTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < outputs.size(); i++) {
            CompoundTag outputTag = outputs.getCompound(i);
            long amount = outputTag.getLong(NBT_AMOUNT);
            if (amount <= 0) continue;
            AEKey key = AEKey.fromTagGeneric(outputTag.getCompound(NBT_KEY));
            if (key != null) {
                accumulate(pending, key, amount);
                restoredOutput = true;
            }
        }
        if (!restoredOutput) return;
        pending.copies = saturatedAdd(pending.copies, copies);
        threadsInFlight = saturatedAdd(threadsInFlight, copies);
    }

    private static long saturatedMultiply(long amount, long copies) {
        if (amount <= 0 || copies <= 0) return 0L;
        if (amount > Long.MAX_VALUE / copies) return Long.MAX_VALUE;
        return amount * copies;
    }

    private static long saturatedAdd(long left, long right) {
        if (right <= 0) return left;
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static void accumulate(PendingBatch batch, AEKey key, long amount) {
        if (key != null && amount > 0) {
            batch.outputs.put(key, saturatedAdd(batch.outputs.getLong(key), amount));
        }
    }

    private record CachedAssembly(boolean cacheable, CopyAssembler.AssembledCopy assembled) {
        private static CachedAssembly cacheable(CopyAssembler.AssembledCopy assembled) {
            var remainders = assembled.remainders() != null ? List.copyOf(assembled.remainders()) : List.<CopyAssembler.Stack>of();
            var sharedRemainders = assembled.sharedRemainders() != null
                    ? List.copyOf(assembled.sharedRemainders())
                    : List.<CopyAssembler.Stack>of();
            return new CachedAssembly(
                    true,
                    new CopyAssembler.AssembledCopy(
                            assembled.output(), assembled.outputCount(), remainders, sharedRemainders));
        }

        private static CachedAssembly uncacheable() {
            return new CachedAssembly(false, null);
        }
    }

    private record InputSignature(List<List<InputEntry>> slots) {
        private static InputSignature capture(KeyCounter[] inputs) {
            var slots = new ArrayList<List<InputEntry>>(inputs.length);
            for (var input : inputs) {
                var entries = new ArrayList<InputEntry>();
                if (input != null) {
                    for (var entry : input) {
                        if (entry.getKey() != null && entry.getLongValue() > 0) {
                            entries.add(new InputEntry(entry.getKey(), entry.getLongValue()));
                        }
                    }
                }
                entries.sort(Comparator
                        .comparingInt((InputEntry entry) -> entry.key().hashCode())
                        .thenComparing(entry -> entry.key().toString()));
                slots.add(List.copyOf(entries));
            }
            return new InputSignature(List.copyOf(slots));
        }
    }

    private record InputEntry(AEKey key, long amount) {
    }
}
