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

    public record BatchDispatchResult(
            long ownedCopies, boolean globalAbort) {
        private static final BatchDispatchResult EMPTY =
                new BatchDispatchResult(0L, false);
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

    public final boolean returnOutputs(
            ServerLevel level,
            AllowedOutputFilter allowedOutputs,
            IActionSource source,
            MachineAdapter.OutputSink sink) {
        var resolvedAdapter = resolveAdapter(level);
        return resolvedAdapter != null && resolvedAdapter.extractOutputs(
                level,
                pos(),
                boundFace(),
                allowedOutputs,
                source,
                sink);
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
    }

    private void invalidatePhysicalState() {
        runtime.blockEntityRef = null;
        runtime.adapter = null;
        runtime.adapterResolved = false;
        Arrays.fill(runtime.storageTargets, null);
        runtime.blockedThisTick.clear();
        runtime.blockedGameTick = Long.MIN_VALUE;
        runtime.lastSuccessfulPattern = null;
        runtime.batchChunks.clear();
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
        private final IdentityHashMap<IPatternDetails, Integer> batchChunks =
                new IdentityHashMap<>();
        @Nullable
        private IPatternDetails lastSuccessfulPattern;
        @Nullable
        private RoutedPatternOverflow directionalOverflow;
        @Nullable
        private WirelessOverflowQueue.Bucket wirelessOverflow;
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
