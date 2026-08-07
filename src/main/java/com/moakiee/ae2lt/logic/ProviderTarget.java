package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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

import com.moakiee.thunderbolt.CoreConfig;

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
        if (runtime.blockEntityRef == null) {
            // A freshly reconstructed provider target may already carry
            // persisted adaptive history. Establishing its first live block
            // entity reference must not erase that history.
            invalidateResolvedPhysicalCaches();
            runtime.blockEntityRef = new WeakReference<>(current);
        } else if (runtime.blockEntityRef.get() != current) {
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

    public final boolean canAccept(
            ServerLevel level,
            IPatternDetails pattern,
            IActionSource source) {
        var resolvedAdapter = resolveAdapter(level);
        return resolvedAdapter != null && resolvedAdapter.canAccept(
                level,
                pos(),
                boundFace(),
                pattern,
                resolveStorageTarget(level, source));
    }

    public final boolean supportsBatch(
            ServerLevel level, IPatternDetails pattern) {
        var resolvedAdapter = resolveAdapter(level);
        return resolvedAdapter != null && resolvedAdapter.supportsBatch(
                level, pos(), boundFace(), pattern);
    }

    /**
     * Returns the configured per-call copy limit for this physical machine.
     * The wildcard rules are evaluated only when the target block or rule version changes.
     */
    public final long batchCopyLimit(ServerLevel level) {
        var blockEntity = resolveBlockEntity(level);
        if (blockEntity == null) {
            return Long.MAX_VALUE;
        }
        var rules = CoreConfig.batchCopyLimitRules();
        if (runtime.batchLimitRulesVersion != rules.version()) {
            var blockId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
            runtime.batchCopyLimit = blockId != null
                    ? rules.limit(blockId.toString())
                    : Long.MAX_VALUE;
            runtime.batchLimitRulesVersion = rules.version();
        }
        return runtime.batchCopyLimit;
    }

    public final PushResult pushCopies(
            ServerLevel level,
            IPatternDetails pattern,
            KeyCounter[] inputs,
            int copies,
            Set<AEKey> patternInputs,
            IActionSource source) {
        return pushCopies(
                level,
                pattern,
                inputs,
                copies,
                PatternInputAcceptance.COMPLETE_BATCH,
                patternInputs,
                source);
    }

    public final PushResult pushCopies(
            ServerLevel level,
            IPatternDetails pattern,
            KeyCounter[] inputs,
            int copies,
            PatternInputAcceptance inputAcceptance,
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
                inputAcceptance,
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
                        rememberBatchChunk(pattern, 1);
                        break;
                    }
                    nextChunk = Math.max(1, chunkCopies / 2);
                    rememberBatchChunk(pattern, nextChunk);
                    backingOff = true;
                    continue;
                }
                break;
            }

            ownedCopies += chunk.ownedCopies();
            if (chunk.ownedCopies() != chunkCopies
                    || !chunk.fullyInserted()) {
                if (!fullChunkAccepted) {
                    rememberBatchChunk(
                            pattern, Math.max(1, chunkCopies / 2));
                }
                break;
            }

            fullChunkAccepted = true;
            if (!requestLimited) {
                rememberBatchChunk(pattern, chunkCopies);
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

    private void rememberBatchChunk(
            IPatternDetails pattern, int chunkCopies) {
        int sanitized = Math.max(1, chunkCopies);
        var previous = runtime.batchChunks.put(pattern, sanitized);
        if (previous == null || previous != sanitized) {
            runtime.batchHistoryDirty = true;
        }
    }

    /**
     * Attempts one regular refill chunk, or a complete same-tick growth ramp.
     * Growth physically dispatches {@code H, H, 2H, 4H, 8H, ...} in this
     * visit; proofs from earlier ticks never satisfy either of the two prefix
     * chunks, while later growth levels do not need to be repeated.
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
        var previousState = state.snapshot();
        state.expireIfIdle(
                gameTick, preserveBatchHistoryOnRejection);
        state.lastAttemptTick = gameTick;

        BatchStepResult result;
        if (!state.backingOff) {
            result = pushSameTickRamp(
                    state,
                    maxCopies,
                    gameTick,
                    preserveBatchHistoryOnRejection,
                    blocked,
                    pushChunk);
        } else {
            result = pushBackoffStep(
                    state,
                    maxCopies,
                    gameTick,
                    preserveBatchHistoryOnRejection,
                    pushChunk);
        }
        if (!previousState.equals(state.snapshot())) {
            runtime.batchHistoryDirty = true;
        }
        return result;
    }

    private static BatchStepResult pushBackoffStep(
            BatchStepState state,
            long maxCopies,
            long gameTick,
            boolean preserveBatchHistoryOnRejection,
            IntFunction<BatchChunk> pushChunk) {
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

        if (!requestLimited) {
            if (chunk.ownedCopies() == attemptedCopies
                    && chunk.fullyInserted()) {
                state.advance(attemptedCopies);
            } else {
                state.reject(attemptedCopies, false);
            }
        }
        state.lastSuccessfulTick = gameTick;
        return BatchStepResult.from(
                chunk, attemptedCopies, requestLimited);
    }

    private static BatchStepResult pushSameTickRamp(
            BatchStepState state,
            long maxCopies,
            long gameTick,
            boolean preserveBatchHistoryOnRejection,
            BooleanSupplier blocked,
            IntFunction<BatchChunk> pushChunk) {
        int baseline = Math.max(1, state.provenChunk);
        int nextChunk = baseline;
        int baselineSuccesses = 0;
        boolean allowGrowth = !state.growthCapped;
        long ownedCopies = 0L;
        long attemptedCopies = 0L;
        boolean allFullyInserted = true;

        while (ownedCopies < maxCopies) {
            if (blocked.getAsBoolean()) {
                break;
            }
            long remaining = maxCopies - ownedCopies;
            int desiredCopies = nextChunk;
            int attemptedChunk = (int) Math.min(
                    Math.min((long) desiredCopies, remaining),
                    Integer.MAX_VALUE);
            boolean requestLimited = attemptedChunk < desiredCopies;
            attemptedCopies = Math.min(
                    Integer.MAX_VALUE,
                    attemptedCopies + attemptedChunk);
            var chunk = pushChunk.apply(attemptedChunk);
            if (chunk.globalAbort()) {
                return new BatchStepResult(
                        ownedCopies,
                        (int) attemptedCopies,
                        false,
                        true,
                        requestLimited,
                        BaselineStatus.NONE);
            }
            if (chunk.ownedCopies() <= 0L) {
                BaselineStatus stopStatus = baselineSuccesses == 1
                        ? BaselineStatus.PREFIX_COMPLETE
                        : BaselineStatus.NONE;
                if (!preserveBatchHistoryOnRejection) {
                    if (baselineSuccesses == 0) {
                        state.reject(attemptedChunk, requestLimited);
                    } else if (baselineSuccesses >= 2) {
                        state.growthCapped = true;
                        state.nextChunk = Math.max(1, state.provenChunk);
                    }
                }
                if (ownedCopies > 0L) {
                    state.lastSuccessfulTick = gameTick;
                }
                return new BatchStepResult(
                        ownedCopies,
                        (int) attemptedCopies,
                        false,
                        false,
                        requestLimited,
                        stopStatus);
            }

            ownedCopies += chunk.ownedCopies();
            boolean fullyInserted = chunk.ownedCopies() == attemptedChunk
                    && chunk.fullyInserted();
            if (!fullyInserted) {
                BaselineStatus stopStatus = baselineSuccesses == 1
                        ? BaselineStatus.PREFIX_COMPLETE
                        : BaselineStatus.NONE;
                allFullyInserted = false;
                if (!preserveBatchHistoryOnRejection) {
                    if (baselineSuccesses == 0) {
                        state.reject(attemptedChunk, requestLimited);
                    } else if (baselineSuccesses >= 2) {
                        state.growthCapped = true;
                        state.nextChunk = Math.max(1, state.provenChunk);
                    }
                }
                state.lastSuccessfulTick = gameTick;
                return new BatchStepResult(
                        ownedCopies,
                        (int) attemptedCopies,
                        false,
                        false,
                        requestLimited,
                        stopStatus);
            }
            if (requestLimited) {
                state.lastSuccessfulTick = gameTick;
                return new BatchStepResult(
                        ownedCopies,
                        (int) attemptedCopies,
                        allFullyInserted,
                        false,
                        true,
                        BaselineStatus.NONE);
            }

            if (baselineSuccesses < 2) {
                baselineSuccesses++;
                state.provenChunk = Math.max(state.provenChunk, baseline);
                state.nextChunk = baseline;
                if (baselineSuccesses < 2) {
                    continue;
                }
                if (!allowGrowth) {
                    state.lastSuccessfulTick = gameTick;
                    return new BatchStepResult(
                            ownedCopies,
                            (int) attemptedCopies,
                            true,
                            false,
                            false,
                            BaselineStatus.COMPLETE);
                }
                nextChunk = saturatingDouble(baseline);
            } else {
                state.provenChunk = desiredCopies;
                state.nextChunk = saturatingDouble(desiredCopies);
                state.provenSuccesses = 1;
                state.repeatCurrent = false;
                state.growthCapped = false;
                state.backingOff = false;
                nextChunk = state.nextChunk;
            }
            state.lastSuccessfulTick = gameTick;
            if (desiredCopies == Integer.MAX_VALUE) {
                break;
            }
        }

        return new BatchStepResult(
                ownedCopies,
                (int) attemptedCopies,
                allFullyInserted,
                false,
                false,
                baselineSuccesses >= 2
                        ? BaselineStatus.GROWTH_COMPLETE
                        : BaselineStatus.NONE);
    }

    private static int saturatingDouble(int value) {
        return value >= Integer.MAX_VALUE / 2
                ? Integer.MAX_VALUE
                : value * 2;
    }

    /** Returns the next physical chunk without advancing its adaptive state. */
    final int batchStepCandidate(
            IPatternDetails pattern, long maxCopies, long gameTick) {
        if (maxCopies <= 0L) {
            return 0;
        }
        var state = runtime.batchSteps.computeIfAbsent(
                pattern, ignored -> new BatchStepState());
        var previousState = state.snapshot();
        state.expireIfIdle(gameTick, true);
        if (!previousState.equals(state.snapshot())) {
            runtime.batchHistoryDirty = true;
        }
        return (int) Math.min(
                Math.min((long) state.nextChunk, maxCopies),
                Integer.MAX_VALUE);
    }

    /**
     * Fair allowance available to the complete same-tick growth ramp.
     * Dispatch supplies an equal-share upper bound, so the target may continue
     * from {@code H, H, 2H} directly to {@code 4H, 8H, ...} without skewing a
     * finite job toward the first targets in the pass.
     */
    final long batchStepRampAllowance(
            IPatternDetails pattern, long maxCopies, long gameTick) {
        if (maxCopies <= 0L) {
            return 0L;
        }
        var state = runtime.batchSteps.computeIfAbsent(
                pattern, ignored -> new BatchStepState());
        state.expireIfIdle(gameTick, true);
        if (state.growthCapped) {
            return batchStepCandidate(pattern, maxCopies, gameTick);
        }
        return maxCopies;
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
            boolean requestLimited,
            BaselineStatus baselineStatus) {
        private static final BatchStepResult EMPTY =
                new BatchStepResult(
                        0L, 0, false, false, false, BaselineStatus.NONE);

        private static BatchStepResult from(
                BatchChunk chunk,
                int attemptedCopies,
                boolean requestLimited) {
            return from(
                    chunk,
                    attemptedCopies,
                    requestLimited,
                    BaselineStatus.NONE);
        }

        private static BatchStepResult from(
                BatchChunk chunk,
                int attemptedCopies,
                boolean requestLimited,
                BaselineStatus baselineStatus) {
            return new BatchStepResult(
                    chunk.ownedCopies(),
                    attemptedCopies,
                    chunk.fullyInserted(),
                    chunk.globalAbort(),
                    requestLimited,
                    baselineStatus);
        }

        public boolean acceptedFullChunk() {
            return ownedCopies == attemptedCopies && fullyInserted;
        }
    }

    public enum BaselineStatus {
        NONE,
        PREFIX_COMPLETE,
        COMPLETE,
        GROWTH_COMPLETE
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
        if (!runtime.batchChunks.isEmpty()
                || !runtime.batchSteps.isEmpty()) {
            runtime.batchHistoryDirty = true;
        }
        runtime.batchChunks.clear();
        runtime.batchSteps.clear();
    }

    final Map<IPatternDetails, AdaptiveBatchSnapshot>
            adaptiveBatchSnapshots() {
        var result = new IdentityHashMap<
                IPatternDetails, AdaptiveBatchSnapshot>();
        runtime.batchChunks.forEach((pattern, chunk) -> result.put(
                pattern,
                new AdaptiveBatchSnapshot(chunk, null)));
        runtime.batchSteps.forEach((pattern, state) -> result.merge(
                pattern,
                new AdaptiveBatchSnapshot(0, state.snapshot()),
                (left, right) -> new AdaptiveBatchSnapshot(
                        left.rememberedChunk(),
                        right.step())));
        return result;
    }

    final void restoreAdaptiveBatchSnapshot(
            IPatternDetails pattern,
            AdaptiveBatchSnapshot snapshot) {
        if (pattern == null || snapshot == null
                || !snapshot.isValid()) {
            return;
        }
        if (snapshot.rememberedChunk() > 0) {
            runtime.batchChunks.put(
                    pattern, snapshot.rememberedChunk());
        }
        if (snapshot.step() != null) {
            runtime.batchSteps.put(
                    pattern, new BatchStepState(snapshot.step()));
        }
        runtime.batchHistoryDirty = false;
    }

    final boolean consumeAdaptiveBatchHistoryDirty() {
        boolean dirty = runtime.batchHistoryDirty;
        runtime.batchHistoryDirty = false;
        return dirty;
    }

    record AdaptiveBatchSnapshot(
            int rememberedChunk,
            @Nullable BatchStepSnapshot step) {
        boolean isValid() {
            return rememberedChunk >= 0
                    && (rememberedChunk > 0 || step != null)
                    && (step == null || step.isValid());
        }
    }

    record BatchStepSnapshot(
            int nextChunk,
            int provenChunk,
            int provenSuccesses,
            boolean repeatCurrent,
            boolean growthCapped,
            boolean backingOff,
            long lastSuccessfulTick,
            long lastAttemptTick) {
        boolean isValid() {
            return nextChunk > 0
                    && provenChunk >= 0
                    && provenSuccesses >= 0
                    && (provenChunk > 0
                            || nextChunk == 1 && !backingOff);
        }
    }

    private void invalidatePhysicalState() {
        invalidateResolvedPhysicalCaches();
        clearBatchHistory();
    }

    private void invalidateResolvedPhysicalCaches() {
        runtime.blockEntityRef = null;
        runtime.adapter = null;
        runtime.adapterResolved = false;
        Arrays.fill(runtime.storageTargets, null);
        runtime.blockedThisTick.clear();
        runtime.blockedGameTick = Long.MIN_VALUE;
        runtime.lastOutputReturnScanTick = Long.MIN_VALUE;
        runtime.lastSuccessfulPattern = null;
        runtime.batchLimitRulesVersion = Long.MIN_VALUE;
        runtime.batchCopyLimit = Long.MAX_VALUE;
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
        private boolean batchHistoryDirty;
        private long batchLimitRulesVersion = Long.MIN_VALUE;
        private long batchCopyLimit = Long.MAX_VALUE;
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
        private long lastAttemptTick = Long.MIN_VALUE;

        private BatchStepState() {
        }

        private BatchStepState(BatchStepSnapshot snapshot) {
            this.nextChunk = snapshot.nextChunk();
            this.provenChunk = snapshot.provenChunk();
            this.provenSuccesses = snapshot.provenSuccesses();
            this.repeatCurrent = snapshot.repeatCurrent();
            this.growthCapped = snapshot.growthCapped();
            this.backingOff = snapshot.backingOff();
            this.lastSuccessfulTick = snapshot.lastSuccessfulTick();
            this.lastAttemptTick = snapshot.lastAttemptTick();
        }

        private BatchStepSnapshot snapshot() {
            return new BatchStepSnapshot(
                    nextChunk,
                    provenChunk,
                    provenSuccesses,
                    repeatCurrent,
                    growthCapped,
                    backingOff,
                    lastSuccessfulTick,
                    lastAttemptTick);
        }

        private void expireIfIdle(
                long gameTick, boolean preserveAttemptHistory) {
            long referenceTick = preserveAttemptHistory
                    ? lastAttemptTick
                    : lastSuccessfulTick;
            if (referenceTick == Long.MIN_VALUE) {
                return;
            }
            boolean expired = preserveAttemptHistory
                    ? gameTick < referenceTick
                            || gameTick - referenceTick > BATCH_HISTORY_TTL
                    : gameTick < referenceTick
                            || gameTick - referenceTick >= BATCH_HISTORY_TTL;
            if (expired) {
                nextChunk = 1;
                provenChunk = 0;
                provenSuccesses = 0;
                repeatCurrent = true;
                growthCapped = false;
                backingOff = false;
                lastSuccessfulTick = Long.MIN_VALUE;
                lastAttemptTick = Long.MIN_VALUE;
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

