package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.me.storage.CompositeStorage;
import appeng.parts.automation.StackWorldBehaviors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class AdjacentItemAutoExportHelper {
    private static final long WRAPPER_REFRESH_TICKS = 20L;

    private AdjacentItemAutoExportHelper() {
    }

    public static final class DirectionalTargetCache {
        private final EnumMap<Direction, CacheEntry> cache = new EnumMap<>(Direction.class);

        @Nullable
        public CompositeStorage resolve(ServerLevel level, BlockPos origin, @Nullable Direction direction) {
            if (direction == null) {
                return null;
            }

            var targetPos = origin.relative(direction);
            var targetBlockEntity = level.getBlockEntity(targetPos);
            if (targetBlockEntity == null) {
                cache.remove(direction);
                return null;
            }

            var cached = cache.get(direction);
            if (cached == null || !cached.isValid(targetBlockEntity)) {
                var strategies = StackWorldBehaviors.createExternalStorageStrategies(
                        level,
                        targetPos,
                        direction.getOpposite());
                if (strategies.isEmpty()) {
                    cache.remove(direction);
                    return null;
                }

                cached = new CacheEntry(targetBlockEntity, strategies);
                cache.put(direction, cached);
            }

            return cached.getCompositeStorage(level.getGameTime());
        }

        public void invalidate() {
            cache.clear();
        }
    }

    private static final class CacheEntry {
        private final WeakReference<BlockEntity> blockEntityRef;
        private final Map<AEKeyType, ExternalStorageStrategy> strategies;
        private Map<AEKeyType, MEStorage> wrappers;
        private CompositeStorage compositeStorage;
        private long wrapperCreatedTick;

        private CacheEntry(BlockEntity blockEntity, Map<AEKeyType, ExternalStorageStrategy> strategies) {
            this.blockEntityRef = new WeakReference<>(blockEntity);
            this.strategies = strategies;
        }

        private boolean isValid(BlockEntity currentBlockEntity) {
            return blockEntityRef.get() == currentBlockEntity;
        }

        @Nullable
        private CompositeStorage getCompositeStorage(long gameTick) {
            if (wrappers == null || gameTick - wrapperCreatedTick >= WRAPPER_REFRESH_TICKS) {
                rebuildWrappers(gameTick);
            }

            return compositeStorage;
        }

        private void rebuildWrappers(long gameTick) {
            var rebuiltWrappers = new IdentityHashMap<AEKeyType, MEStorage>(strategies.size());
            for (var entry : strategies.entrySet()) {
                var wrapper = entry.getValue().createWrapper(false, () -> {});
                if (wrapper != null) {
                    rebuiltWrappers.put(entry.getKey(), wrapper);
                }
            }

            wrappers = rebuiltWrappers.isEmpty() ? null : rebuiltWrappers;
            compositeStorage = wrappers == null ? null : new CompositeStorage(wrappers);
            wrapperCreatedTick = gameTick;
        }
    }

    @FunctionalInterface
    public interface SlotStackReader {
        ItemStack getStack(int slot);
    }

    @FunctionalInterface
    public interface SlotExtractor {
        ItemStack extract(int slot, int amount);
    }

    @FunctionalInterface
    public interface RemainderInserter {
        void insert(ItemStack stack);
    }

    @FunctionalInterface
    public interface TargetResolver {
        @Nullable CompositeStorage resolve(Direction direction);
    }

    public static boolean hasAnyOutput(boolean autoExport, int firstSlot, int slotCount, SlotStackReader stackReader) {
        if (!autoExport) {
            return false;
        }

        for (int slot = firstSlot; slot < firstSlot + slotCount; slot++) {
            if (!stackReader.getStack(slot).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    public static boolean pushOutResult(
            IActionHost host,
            @Nullable BlockOrientation orientation,
            Set<RelativeSide> allowedOutputs,
            int firstSlot,
            int slotCount,
            SlotStackReader stackReader,
            SlotExtractor extractor,
            RemainderInserter remainderInserter,
            TargetResolver targetResolver) {
        if (orientation == null || allowedOutputs.isEmpty()) {
            return false;
        }

        var actionSource = IActionSource.ofMachine(host);
        for (var side : allowedOutputs) {
            var direction = orientation.getSide(side);
            if (direction == null) {
                continue;
            }

            var target = targetResolver.resolve(direction);
            if (target == null) {
                continue;
            }

            for (int slot = firstSlot; slot < firstSlot + slotCount; slot++) {
                ItemStack output = stackReader.getStack(slot);
                if (output.isEmpty()) {
                    continue;
                }

                var key = AEItemKey.of(output);
                if (key == null) {
                    continue;
                }

                ItemStack extracted = extractor.extract(slot, output.getCount());
                if (extracted.isEmpty()) {
                    continue;
                }

                long inserted = target.insert(key, extracted.getCount(), Actionable.MODULATE, actionSource);
                if (inserted < extracted.getCount()) {
                    remainderInserter.insert(extracted.copyWithCount((int) (extracted.getCount() - inserted)));
                }

                if (inserted > 0L) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
    public static CompositeStorage resolveTarget(ServerLevel level, BlockPos origin, Direction direction) {
        if (direction == null) {
            return null;
        }

        var externalStorages = new IdentityHashMap<AEKeyType, MEStorage>(2);
        var strategies = StackWorldBehaviors.createExternalStorageStrategies(
                level,
                origin.relative(direction),
                direction.getOpposite());
        for (var entry : strategies.entrySet()) {
            var wrapper = entry.getValue().createWrapper(false, () -> {});
            if (wrapper != null) {
                externalStorages.put(entry.getKey(), wrapper);
            }
        }

        return externalStorages.isEmpty() ? null : new CompositeStorage(externalStorages);
    }
}
