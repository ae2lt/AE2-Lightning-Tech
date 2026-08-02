package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderTarget;

/**
 * One provider-owned physical target.
 *
 * <p>This object owns facts about the physical machine (resolved adapters,
 * storage facades and the last accepted pattern). Dispatch policy such as
 * cooldowns, probes, penalties and fairness remains outside this class.</p>
 */
public class ProviderTarget extends TargetAddress {
    private static final int STORAGE_TARGET_CACHE_TTL = 20;
    private static final int BATCH_HISTORY_TTL = 100;

    private final ProviderTargetRuntime runtime =
            new ProviderTargetRuntime();

    public ProviderTarget(
            ResourceKey<Level> dimension,
            BlockPos pos,
            Direction boundFace) {
        super(dimension, pos, boundFace);
    }

    @Nullable
    public final BlockEntity resolveBlockEntity(ServerLevel level) {
        if (!dimension().equals(level.dimension()) || !level.isLoaded(pos())) {
            invalidatePhysicalState();
            return null;
        }
        var current = level.getBlockEntity(pos());
        if (current == null) {
            invalidatePhysicalState();
            return null;
        }
        if (runtime.blockEntityRef == null
                || runtime.blockEntityRef.get() != current) {
            invalidatePhysicalState();
            runtime.blockEntityRef = new WeakReference<>(current);
        }
        return current;
    }

    @Nullable
    public final MachineAdapter resolveAdapter(ServerLevel level) {
        var blockEntity = resolveBlockEntity(level);
        if (blockEntity == null) {
            return null;
        }
        if (!runtime.adapterResolved) {
            runtime.adapter = MachineAdapterRegistry.find(level, pos());
            runtime.adapterResolved = true;
        }
        return runtime.adapter;
    }

    @Nullable
    public final PatternProviderTarget resolveStorageTarget(
            ServerLevel level,
            IActionSource source) {
        return resolveStorageTarget(level, boundFace(), source);
    }

    @Nullable
    public final PatternProviderTarget resolveStorageTarget(
            ServerLevel level,
            Direction face,
            IActionSource source) {
        var blockEntity = resolveBlockEntity(level);
        if (blockEntity == null) {
            return null;
        }
        long gameTick = level.getGameTime();
        int faceIndex = face.get3DDataValue();
        var cached = runtime.storageTargets[faceIndex];
        if (cached != null && cached.isValid(blockEntity, gameTick)) {
            return cached.target;
        }
        var resolved = PatternProviderTarget.get(
                level, pos(), blockEntity, face, source);
        if (resolved == null) {
            runtime.storageTargets[faceIndex] = null;
        } else {
            runtime.storageTargets[faceIndex] =
                    new CachedStorageTarget(blockEntity, resolved, gameTick);
        }
        return resolved;
    }

    private boolean isBlocked(
            ServerLevel level,
            @Nullable PatternProviderTarget target,
            IPatternDetails pattern,
            boolean craftingLocked,
            boolean blockingEnabled,
            boolean samePatternMode,
            Set<AEKey> patternInputs) {
        if (craftingLocked) {
            return true;
        }
        if (!blockingEnabled) {
            return false;
        }

        var blockEntity = resolveBlockEntity(level);
        if (blockEntity == null) {
            return true;
        }
        if (samePatternMode
                && BatchBlockingPolicy.samePattern(
                        runtime.lastSuccessfulPattern, pattern)) {
            return false;
        }
        if (target == null) {
            return false;
        }

        long gameTick = level.getGameTime();
        if (runtime.blockedGameTick != gameTick) {
            runtime.blockedThisTick.clear();
            runtime.blockedGameTick = gameTick;
        } else if (runtime.blockedThisTick.contains(target)) {
            return true;
        }

        boolean blocked = target.containsPatternInput(patternInputs);
        if (blocked) {
            runtime.blockedThisTick.add(target);
        }
        return blocked;
    }

    public final boolean isBlocked(
            ServerLevel level,
            IActionSource source,
            IPatternDetails pattern,
            boolean craftingLocked,
            boolean blockingEnabled,
            boolean samePatternMode,
            Set<AEKey> patternInputs) {
        return isBlocked(
                level,
                resolveStorageTarget(level, source),
                pattern,
                craftingLocked,
                blockingEnabled,
                samePatternMode,
                patternInputs);
    }

    public final boolean canAccept(
            ServerLevel level, IPatternDetails pattern) {
        var resolvedAdapter = resolveAdapter(level);
        return resolvedAdapter != null && resolvedAdapter.canAccept(
                level, pos(), boundFace(), pattern);
    }

    public final boolean supportsBatch(
            ServerLevel level, IPatternDetails pattern) {
        var resolvedAdapter = resolveAdapter(level);
        return resolvedAdapter != null && resolvedAdapter.supportsBatch(
                level, pos(), boundFace(), pattern);
    }

    public final PushResult pushCopies(
            ServerLevel level,
            IPatternDetails pattern,
            KeyCounter[] inputs,
            int copies,
            Set<AEKey> patternInputs,
            IActionSource source) {
        var resolvedAdapter = resolveAdapter(level);
        if (resolvedAdapter == null) {
            return PushResult.REJECTED;
        }
        return resolvedAdapter.pushCopies(
                level,
                pos(),
                boundFace(),
                pattern,
                inputs,
                copies,
                false,
                patternInputs,
                source,
                resolveStorageTarget(level, source));
    }

    public final void markPatternDispatched(
            ServerLevel level, IPatternDetails pattern) {
        if (resolveBlockEntity(level) != null) {
            runtime.lastSuccessfulPattern = pattern;
        }
    }

    public final void setDirectionalOverflow(RoutedPatternOverflow overflow) {
        runtime.directionalOverflow = overflow.isEmpty() ? null : overflow;
    }

    @Nullable
    public final RoutedPatternOverflow directionalOverflow() {
        return runtime.directionalOverflow;
    }

    public final void clearDirectionalOverflow() {
        runtime.directionalOverflow = null;
    }

    @Nullable
    protected final WirelessOverflowQueue.Bucket wirelessOverflow() {
        return runtime.wirelessOverflow;
    }

    protected final void setWirelessOverflow(
            @Nullable WirelessOverflowQueue.Bucket overflow) {
        runtime.wirelessOverflow = overflow;
    }

    /**
     * Executes the provider's bounded batch ramp for this physical target.
     * Each canonical pattern remembers its last non-tail chunk that was
     * inserted in full without overflow. A later call starts from that chunk,
     * repeats it once, and then doubles ({@code H, H, 2H, 4H, ...}). A clean
     * rejection of the initial chunk halves the candidate until one succeeds;
     * that first recovery success ends the current call.
     *
     * <p>Dispatch decides the target allowance; the target owns this physical
     * acceptance history and how that allowance is attempted.</p>
     */
    public final BatchDispatchResult pushPattern(
            IPatternDetails pattern,
            long maxCopies,
            boolean batchSupported,
            BooleanSupplier blocked,
            IntFunction<BatchChunk> pushChunk) {
        if (maxCopies <= 0L) {
            return BatchDispatchResult.EMPTY;
        }
        if (!batchSupported) {
            if (blocked.getAsBoolean()) {
                return BatchDispatchResult.EMPTY;
            }
            var single = pushChunk.apply(1);
            return new BatchDispatchResult(
                    single.ownedCopies(), single.globalAbort());
        }

        int rememberedChunk = runtime.batchChunks.getOrDefault(pattern, 1);
        int nextChunk = rememberedChunk;
        long ownedCopies = 0L;
        boolean fullChunkAccepted = false;
        boolean backingOff = false;
        while (ownedCopies < maxCopies) {
            if (blocked.getAsBoolean()) {
                break;
            }

            long remaining = maxCopies - ownedCopies;
            int chunkCopies = (int) Math.min(
                    Math.min((long) nextChunk, remaining),
                    Integer.MAX_VALUE);
            boolean requestLimited = chunkCopies < nextChunk;
            var chunk = pushChunk.apply(chunkCopies);
            if (chunk.globalAbort()) {
                return new BatchDispatchResult(ownedCopies, true);
            }
            if (chunk.ownedCopies() <= 0L) {
                if (!fullChunkAccepted) {
                    if (chunkCopies <= 1) {
                        runtime.batchChunks.put(pattern, 1);
                        break;
                    }
                    nextChunk = Math.max(1, chunkCopies / 2);
                    runtime.batchChunks.put(pattern, nextChunk);
                    backingOff = true;
                    continue;
                }
                break;
            }

            ownedCopies += chunk.ownedCopies();
            if (chunk.ownedCopies() != chunkCopies
                    || !chunk.fullyInserted()) {
                if (!fullChunkAccepted) {
                    runtime.batchChunks.put(
                            pattern, Math.max(1, chunkCopies / 2));
                }
                break;
            }

            fullChunkAccepted = true;
            if (!requestLimited) {
                runtime.batchChunks.put(pattern, chunkCopies);
            }
            if (backingOff) {
                break;
            }
            if (chunkCopies == Integer.MAX_VALUE) {
                break;
            }
            nextChunk = (int) Math.min(ownedCopies, Integer.MAX_VALUE);
        }
        return new BatchDispatchResult(ownedCopies, false);
    }

    /**
     * Attempts exactly one adaptive batch chunk for a wireless scheduling
     * opportunity. The safe cold sequence is {@code 1, 1, 2, 4, ...}; a
     * rejected growth probe returns to the last proven chunk for a later
     * opportunity. Before any chunk is proven, rejection halves the cold
     * candidate instead of probing repeatedly in the same target visit.
     */
    public final BatchStepResult pushPatternStep(
            IPatternDetails pattern,
            long maxCopies,
            long gameTick,
            boolean batchSupported,
            BooleanSupplier blocked,
            IntFunction<BatchChunk> pushChunk) {
        return pushPatternStep(
                pattern,
                maxCopies,
                gameTick,
                batchSupported,
                false,
                blocked,
                pushChunk);
    }

    /**
     * Executes one wireless batch step. An exploratory cadence probe is sent
     * one tick before the learned refill interval; its rejection describes
     * timing, not a reduction in the target's proven chunk capacity.
     */
    public final BatchStepResult pushPatternStep(
            IPatternDetails pattern,
            long maxCopies,
            long gameTick,
            boolean batchSupported,
            boolean preserveBatchHistoryOnRejection,
            BooleanSupplier blocked,
            IntFunction<BatchChunk> pushChunk) {
        if (maxCopies <= 0L || blocked.getAsBoolean()) {
            return BatchStepResult.EMPTY;
        }
        if (!batchSupported) {
            var single = pushChunk.apply(1);
            return BatchStepResult.from(single, 1, false);
        }

        var state = runtime.batchSteps.computeIfAbsent(
                pattern, ignored -> new BatchStepState());
        state.expireIfIdle(gameTick);

        int attemptedCopies = (int) Math.min(
                Math.min((long) state.nextChunk, maxCopies),
                Integer.MAX_VALUE);
        boolean requestLimited = attemptedCopies < state.nextChunk;
        var chunk = pushChunk.apply(attemptedCopies);
        if (chunk.globalAbort()) {
            return BatchStepResult.from(
                    chunk, attemptedCopies, requestLimited);
        }
        if (chunk.ownedCopies() <= 0L) {
            if (!preserveBatchHistoryOnRejection) {
                state.reject(attemptedCopies, requestLimited);
            }
            return BatchStepResult.from(
                    chunk, attemptedCopies, requestLimited);
        }

        state.lastSuccessfulTick = gameTick;
        if (!requestLimited) {
            if (chunk.ownedCopies() == attemptedCopies
                    && chunk.fullyInserted()) {
                state.advance(attemptedCopies);
            } else {
                state.reject(attemptedCopies, false);
            }
        }
        return BatchStepResult.from(
                chunk, attemptedCopies, requestLimited);
    }

    public record BatchDispatchResult(
            long ownedCopies, boolean globalAbort) {
        private static final BatchDispatchResult EMPTY =
                new BatchDispatchResult(0L, false);
    }

    public record BatchStepResult(
            long ownedCopies,
            int attemptedCopies,
            boolean fullyInserted,
            boolean globalAbort,
            boolean requestLimited) {
        private static final BatchStepResult EMPTY =
                new BatchStepResult(0L, 0, false, false, false);

        private static BatchStepResult from(
                BatchChunk chunk,
                int attemptedCopies,
                boolean requestLimited) {
            return new BatchStepResult(
                    chunk.ownedCopies(),
                    attemptedCopies,
                    chunk.fullyInserted(),
                    chunk.globalAbort(),
                    requestLimited);
        }

        public boolean acceptedFullChunk() {
            return ownedCopies == attemptedCopies && fullyInserted;
        }
    }

    public record BatchChunk(
            long ownedCopies, boolean fullyInserted, boolean globalAbort) {
        public static final BatchChunk REJECTED =
                new BatchChunk(0L, false, false);
        public static final BatchChunk GLOBAL_ABORT =
                new BatchChunk(0L, false, true);
    }

    public final boolean isAlive(ServerLevel level) {
        return resolveBlockEntity(level) != null;
    }

    public final OutputReturnResult returnOutputs(
            ServerLevel level,
            AllowedOutputFilter allowedOutputs,
            IActionSource source,
            MachineAdapter.OutputSink sink) {
        var resolvedAdapter = resolveAdapter(level);
        return resolvedAdapter == null
                ? OutputReturnResult.UNAVAILABLE
                : resolvedAdapter.extractOutputs(
                        level,
                        pos(),
                        boundFace(),
                        allowedOutputs,
                        source,
                        sink);
    }

    /**
     * Claims this target's output-return scan for the current server tick.
     * Periodic and pre-dispatch paths share the same claim, so a target can be
     * considered by several patterns without enumerating its inventory twice.
     */
    public final boolean claimOutputReturnScan(long gameTick) {
        if (runtime.lastOutputReturnScanTick == gameTick) {
            return false;
        }
        runtime.lastOutputReturnScanTick = gameTick;
        return true;
    }

    public final boolean flushOverflow(
            ServerLevel level,
            List<GenericStack> overflow,
            IActionSource source) {
        var resolvedAdapter = resolveAdapter(level);
        return resolvedAdapter != null && resolvedAdapter.flushOverflow(
                level,
                pos(),
                boundFace(),
                overflow,
                source,
                resolveStorageTarget(level, source));
    }

    public final void clearRuntimeState() {
        invalidatePhysicalState();
    }

    final void clearBatchHistory() {
        runtime.batchChunks.clear();
        runtime.batchSteps.clear();
    }

    private void invalidatePhysicalState() {
        runtime.blockEntityRef = null;
        runtime.adapter = null;
        runtime.adapterResolved = false;
        Arrays.fill(runtime.storageTargets, null);
        runtime.blockedThisTick.clear();
        runtime.blockedGameTick = Long.MIN_VALUE;
        runtime.lastOutputReturnScanTick = Long.MIN_VALUE;
        runtime.lastSuccessfulPattern = null;
        runtime.batchChunks.clear();
        runtime.batchSteps.clear();
    }

    /** Mutable state with the same lifetime as this physical target object. */
    private static final class ProviderTargetRuntime {
        @Nullable
        private WeakReference<BlockEntity> blockEntityRef;
        @Nullable
        private MachineAdapter adapter;
        private boolean adapterResolved;
        private final CachedStorageTarget[] storageTargets =
                new CachedStorageTarget[Direction.values().length];
        private final Set<PatternProviderTarget> blockedThisTick =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private long blockedGameTick = Long.MIN_VALUE;
        private long lastOutputReturnScanTick = Long.MIN_VALUE;
        private final IdentityHashMap<IPatternDetails, Integer> batchChunks =
                new IdentityHashMap<>();
        private final IdentityHashMap<IPatternDetails, BatchStepState> batchSteps =
                new IdentityHashMap<>();
        @Nullable
        private IPatternDetails lastSuccessfulPattern;
        @Nullable
        private RoutedPatternOverflow directionalOverflow;
        @Nullable
        private WirelessOverflowQueue.Bucket wirelessOverflow;
    }

    private static final class BatchStepState {
        private int nextChunk = 1;
        private int provenChunk;
        private int provenSuccesses;
        private boolean repeatCurrent = true;
        private boolean growthCapped;
        private boolean backingOff;
        private long lastSuccessfulTick = Long.MIN_VALUE;

        private void expireIfIdle(long gameTick) {
            if (lastSuccessfulTick == Long.MIN_VALUE) {
                return;
            }
            if (gameTick < lastSuccessfulTick
                    || gameTick - lastSuccessfulTick >= BATCH_HISTORY_TTL) {
                nextChunk = 1;
                provenChunk = 0;
                provenSuccesses = 0;
                repeatCurrent = true;
                growthCapped = false;
                backingOff = false;
                lastSuccessfulTick = Long.MIN_VALUE;
            }
        }

        private void advance(int acceptedChunk) {
            if (backingOff && provenChunk > 0) {
                nextChunk = provenChunk;
                repeatCurrent = false;
                backingOff = false;
                return;
            }
            if (acceptedChunk != provenChunk) {
                provenChunk = acceptedChunk;
                provenSuccesses = 1;
            } else if (provenSuccesses < Integer.MAX_VALUE) {
                provenSuccesses++;
            }
            if (growthCapped) {
                nextChunk = provenChunk;
                repeatCurrent = false;
                return;
            }
            if (repeatCurrent) {
                nextChunk = acceptedChunk;
                repeatCurrent = false;
                return;
            }
            nextChunk = acceptedChunk >= Integer.MAX_VALUE / 2
                    ? Integer.MAX_VALUE
                    : acceptedChunk * 2;
        }

        private void reject(int rejectedChunk, boolean requestLimited) {
            if (requestLimited) {
                return;
            }
            if (provenChunk > 0 && rejectedChunk > provenChunk) {
                growthCapped = true;
                nextChunk = provenChunk;
                repeatCurrent = false;
                return;
            }
            if (growthCapped
                    && rejectedChunk >= provenChunk
                    && provenSuccesses < 3) {
                nextChunk = provenChunk;
                repeatCurrent = false;
                return;
            }
            nextChunk = Math.max(1, rejectedChunk / 2);
            if (provenChunk > 0) {
                backingOff = true;
                repeatCurrent = false;
            } else {
                repeatCurrent = true;
            }
        }
    }

    private static final class CachedStorageTarget {
        private final WeakReference<BlockEntity> blockEntity;
        private final PatternProviderTarget target;
        private final long createdTick;

        private CachedStorageTarget(
                BlockEntity blockEntity,
                PatternProviderTarget target,
                long createdTick) {
            this.blockEntity = new WeakReference<>(blockEntity);
            this.target = target;
            this.createdTick = createdTick;
        }

        private boolean isValid(BlockEntity current, long gameTick) {
            return blockEntity.get() == current
                    && gameTick - createdTick < STORAGE_TARGET_CACHE_TTL;
        }
    }
}
