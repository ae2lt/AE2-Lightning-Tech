package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Per-menu storage view that adds the bound Tianshu's physical closed-loop patterns to the
 * normal network inventory shown by the Tianshu pattern encoding terminal.
 *
 * <p>The warehouse side is deliberately extraction-only. Returning or replacing a pattern still
 * goes through the terminal's explicit upload action, so ordinary ME insertion cannot silently
 * claim warehouse capacity.</p>
 */
public final class TianshuPatternTerminalStorage implements MEStorage {
    private final Supplier<MEStorage> networkStorage;
    private final Supplier<List<AEKey>> warehouseKeys;
    private final Predicate<AEKey> extractWarehouseKey;
    @Nullable private TianshuTerminalTarget target;

    public TianshuPatternTerminalStorage(TianshuPatternTerminalHost host) {
        this.networkStorage = () -> {
            var node = host.getActionableNode();
            var grid = node != null ? node.getGrid() : null;
            return grid != null ? grid.getStorageService().getInventory() : null;
        };
        this.warehouseKeys = () -> visibleWarehouseKeys(host);
        this.extractWarehouseKey = key -> extractOneWarehouseKey(host, key);
    }

    TianshuPatternTerminalStorage(
            Supplier<MEStorage> networkStorage,
            Supplier<List<AEKey>> warehouseKeys,
            Predicate<AEKey> extractWarehouseKey) {
        this.networkStorage = networkStorage;
        this.warehouseKeys = warehouseKeys;
        this.extractWarehouseKey = extractWarehouseKey;
    }

    public void setTarget(@Nullable TianshuTerminalTarget target) {
        this.target = target;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        var network = networkStorage.get();
        return network != null && network.isPreferredStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        var network = networkStorage.get();
        return network != null ? network.insert(what, amount, mode, source) : 0L;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (amount == 0L) return 0L;

        long extracted = 0L;
        var network = networkStorage.get();
        if (network != null) {
            extracted = network.extract(what, amount, mode, source);
        }
        long remaining = amount - extracted;
        if (remaining <= 0L) return extracted;

        if (mode == Actionable.SIMULATE) {
            long available = warehouseKeys.get().stream()
                    .filter(what::equals)
                    .limit(remaining)
                    .count();
            return saturatingAdd(extracted, available);
        }

        while (remaining > 0L
                && warehouseKeys.get().stream().anyMatch(what::equals)
                && extractWarehouseKey.test(what)) {
            extracted = saturatingAdd(extracted, 1L);
            remaining--;
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var network = networkStorage.get();
        if (network != null) network.getAvailableStacks(out);
        for (var key : warehouseKeys.get()) {
            if (key != null) out.add(key, 1L);
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("block.ae2lt.closed_loop_pattern_storage");
    }

    private List<AEKey> visibleWarehouseKeys(TianshuPatternTerminalHost host) {
        var port = host.resolveTianshuTarget(target);
        var repository = port != null ? port.getClosedLoopPatternRepository() : null;
        if (port == null || repository == null || port.getLevel() == null) return List.of();
        var item = (ClosedLoopPatternItem) ModItems.CLOSED_LOOP_PATTERN.get();
        return repository.activePatterns().stream()
                .map(payload -> AEItemKey.of(item.createStack(payload, port.getLevel().registryAccess())))
                .filter(java.util.Objects::nonNull)
                .map(AEKey.class::cast)
                .toList();
    }

    private boolean extractOneWarehouseKey(TianshuPatternTerminalHost host, AEKey requested) {
        var port = host.resolveTianshuTarget(target);
        var repository = port != null ? port.getClosedLoopPatternRepository() : null;
        if (port == null || repository == null || port.getLevel() == null) return false;
        var item = (ClosedLoopPatternItem) ModItems.CLOSED_LOOP_PATTERN.get();
        for (var payload : repository.activePatterns()) {
            var key = AEItemKey.of(item.createStack(payload, port.getLevel().registryAccess()));
            if (requested.equals(key)) {
                if (!repository.remove(payload.patternId())) return false;
                port.closedLoopPatternsChanged();
                return true;
            }
        }
        return false;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
