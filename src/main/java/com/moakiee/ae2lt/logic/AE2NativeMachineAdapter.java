package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.parts.automation.StackWorldBehaviors;

import com.google.common.util.concurrent.Runnables;

/**
 * Built-in fallback {@link MachineAdapter} that handles any target reachable
 * through AE2's native APIs:
 * <ol>
 *   <li>{@link ICraftingMachine} — dedicated crafting machines (all-or-nothing plans).</li>
 *   <li>{@link PatternProviderTarget} — generic inventories via ME-storage or
 *       platform external-storage capabilities.</li>
 * </ol>
 * <p>
 * Registered as the lowest-priority (last) adapter in {@link MachineAdapterRegistry}.
 * Stateless singleton — all world context is passed through method parameters.
 */
final class AE2NativeMachineAdapter implements MachineAdapter {

    static final AE2NativeMachineAdapter INSTANCE = new AE2NativeMachineAdapter();

    /**
     * How many ticks a cached wrapper (facade) set stays valid before
     * being rebuilt from the strategy's {@code BlockCapabilityCache}.
     * The wrapper holds a handler reference captured at creation time;
     * periodic refresh ensures we pick up handler replacements that
     * don't trigger a block-entity swap (rare, but some mods do this).
     */
    private static final int WRAPPER_REFRESH_TICKS = 20;

    private final Map<TargetFaceKey, StorageCacheEntry> storageCache = new HashMap<>();

    /** Sweep interval for dropping cache entries whose block entity is gone. */
    private static final int SWEEP_INTERVAL_TICKS = 600;
    /** Negative init so the first call sweeps immediately without long overflow. */
    private long lastSweepTick = -SWEEP_INTERVAL_TICKS;

    private AE2NativeMachineAdapter() {}

    private record TargetFaceKey(ResourceKey<Level> dimension, long posLong, Direction face) {}

    /**
     * Per-target cache entry.  Caches both the {@link ExternalStorageStrategy}
     * map (stable, holds internal {@code BlockCapabilityCache}) and the
     * MEStorage wrappers (facades) derived from them.
     * <p>
     * Wrappers are lazily created and kept alive until either:
     * <ul>
     *   <li>the block entity at the target is replaced, or</li>
     *   <li>{@link #WRAPPER_REFRESH_TICKS} have elapsed (staleness guard).</li>
     * </ul>
     */
    private static final class StorageCacheEntry {
        private final WeakReference<BlockEntity> blockEntityRef;
        private final Map<AEKeyType, ExternalStorageStrategy> strategies;
        private Map<AEKeyType, MEStorage> wrappers;
        private long wrapperCreatedTick;

        StorageCacheEntry(BlockEntity be, Map<AEKeyType, ExternalStorageStrategy> strategies) {
            this.blockEntityRef = new WeakReference<>(be);
            this.strategies = strategies;
        }

        boolean isValid(BlockEntity currentBE) {
            return blockEntityRef.get() == currentBE;
        }

        /**
         * Return the cached wrapper map, rebuilding if stale.
         * The map is never empty when non-null.
         */
        @Nullable
        Map<AEKeyType, MEStorage> getWrappers(long gameTick) {
            if (wrappers == null || gameTick - wrapperCreatedTick >= WRAPPER_REFRESH_TICKS) {
                rebuildWrappers(gameTick);
            }
            return wrappers;
        }

        private void rebuildWrappers(long gameTick) {
            var map = new IdentityHashMap<AEKeyType, MEStorage>(strategies.size());
            for (var entry : strategies.entrySet()) {
                var wrapper = entry.getValue().createWrapper(false, Runnables.doNothing());
                if (wrapper != null) {
                    map.put(entry.getKey(), wrapper);
                }
            }
            wrappers = map.isEmpty() ? null : map;
            wrapperCreatedTick = gameTick;
        }
    }

    // ---- supports ---------------------------------------------------------------

    @Override
    public boolean supports(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) != null;
    }

    // ---- canAccept --------------------------------------------------------------

    @Override
    public boolean canAccept(ServerLevel level, BlockPos pos, Direction face,
                             IPatternDetails pattern) {
        var machine = ICraftingMachine.of(level, pos, face);
        if (machine != null) {
            return machine.acceptsPlans();
        }
        return pattern.supportsPushInputsToExternalInventory();
    }

    // ---- pushCopies -------------------------------------------------------------

    @Override
    public PushResult pushCopies(ServerLevel level, BlockPos pos, Direction face,
                                 IPatternDetails pattern, KeyCounter[] inputs, int maxCopies,
                                 boolean blocking, Set<AEKey> patternInputs,
                                 IActionSource source,
                                 @Nullable PatternProviderTarget cachedTarget) {
        // 1. ICraftingMachine path — all-or-nothing
        var machine = ICraftingMachine.of(level, pos, face);
        if (machine != null && machine.acceptsPlans()) {
            if (machine.pushPattern(pattern, inputs, face)) {
                return new PushResult(1, List.of());
            }
            return PushResult.REJECTED;
        }

        // 2. Generic inventory path
        if (!pattern.supportsPushInputsToExternalInventory()) {
            return PushResult.REJECTED;
        }

        // 优先用调用方预取的 target，避免重复触发 BlockCapability 查询
        final PatternProviderTarget target;
        if (cachedTarget != null) {
            target = cachedTarget;
        } else {
            var be = level.getBlockEntity(pos);
            target = PatternProviderTarget.get(level, pos, be, face, source);
        }
        if (target == null) {
            return PushResult.REJECTED;
        }

        if (blocking && target.containsPatternInput(patternInputs)) {
            return PushResult.REJECTED;
        }

        if (!adapterAcceptsAll(target, inputs)) {
            return PushResult.REJECTED;
        }

        var overflow = new ArrayList<GenericStack>();
        pattern.pushInputsToExternalInventory(inputs, (what, amount) -> {
            var inserted = target.insert(what, amount, Actionable.MODULATE);
            if (inserted < amount) {
                overflow.add(new GenericStack(what, amount - inserted));
            }
        });

        return new PushResult(1, overflow);
    }

    // ---- extractOutputs ---------------------------------------------------------

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos, Direction face,
                                             AllowedOutputFilter allowedOutputs, IActionSource source) {
        var cached = resolveCache(level, pos, face);
        if (cached == null) return List.of();

        var wrappers = cached.getWrappers(level.getGameTime());
        if (wrappers == null) return List.of();

        var extracted = new ArrayList<GenericStack>();

        for (var wrapper : wrappers.values()) {
            // Fresh counter per scan: KeyCounter.reset() keeps zeroed keys forever,
            // so a reused buffer accumulates every key ever scanned.
            var scanBuffer = new KeyCounter();
            wrapper.getAvailableStacks(scanBuffer);

            for (var entry : scanBuffer) {
                var key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0 || !allowedOutputs.matches(key)) continue;

                long taken = wrapper.extract(key, amount, Actionable.MODULATE, source);
                if (taken > 0) {
                    extracted.add(new GenericStack(key, taken));
                }
            }
        }
        return extracted;
    }

    // ---- helpers ----------------------------------------------------------------

    private static boolean adapterAcceptsAll(PatternProviderTarget target, KeyCounter[] inputHolder) {
        for (var inputList : inputHolder) {
            for (var input : inputList) {
                if (target.insert(input.getKey(), input.getLongValue(), Actionable.SIMULATE) == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Look up or create the cache entry for the target position.
     * Strategy objects are created once per (pos, face) and reused
     * until the block entity at that position is replaced.
     */
    @Nullable
    private StorageCacheEntry resolveCache(ServerLevel level, BlockPos pos, Direction face) {
        sweepStaleEntries(level.getGameTime());

        var cacheKey = new TargetFaceKey(level.dimension(), pos.asLong(), face);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            storageCache.remove(cacheKey);
            return null;
        }

        var cached = storageCache.get(cacheKey);

        if (cached == null || !cached.isValid(blockEntity)) {
            var strategies = StackWorldBehaviors.createExternalStorageStrategies(level, pos, face);
            if (strategies.isEmpty()) {
                storageCache.remove(cacheKey);
                return null;
            }
            cached = new StorageCacheEntry(blockEntity, strategies);
            storageCache.put(cacheKey, cached);
        }

        return cached;
    }

    /**
     * Periodically drop cache entries whose target block entity was removed
     * or unloaded; otherwise stale strategies for dismantled machines would be
     * retained forever.
     */
    private void sweepStaleEntries(long gameTick) {
        if (gameTick >= lastSweepTick && gameTick - lastSweepTick < SWEEP_INTERVAL_TICKS) return;
        lastSweepTick = gameTick;
        storageCache.values().removeIf(entry -> {
            var be = entry.blockEntityRef.get();
            return be == null || be.isRemoved();
        });
    }
}
