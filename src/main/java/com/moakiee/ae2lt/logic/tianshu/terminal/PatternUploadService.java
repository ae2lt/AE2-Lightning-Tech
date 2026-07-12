package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Server-side provider discovery and no-overwrite upload used by the future terminal menu. */
public final class PatternUploadService {
    public static List<PatternUploadTarget> listTargets(IGrid grid) {
        if (grid == null) return List.of();
        var result = new ArrayList<PatternUploadTarget>();
        for (var host : grid.getActiveMachines(PatternProviderLogicHost.class)) {
            var blockEntity = host.getBlockEntity();
            if (blockEntity == null || blockEntity.isRemoved()
                    || !(blockEntity.getLevel() instanceof ServerLevel level)) continue;
            var inventory = host.getTerminalPatternInventory();
            if (inventory == null || inventory.size() <= 0) continue;
            int free = countFreeSlots(inventory);
            result.add(new PatternUploadTarget(
                    level.dimension(), blockEntity.getBlockPos(), host.getTerminalGroup(),
                    free, inventory.size()));
        }
        result.sort(Comparator
                .comparing((PatternUploadTarget target) -> target.dimension().location().toString())
                .thenComparingLong(target -> target.pos().asLong()));
        return List.copyOf(result);
    }

    public static UploadResult uploadFromSlot(
            IGrid grid,
            PatternUploadTarget selected,
            InternalInventory source,
            int sourceSlot) {
        if (grid == null || selected == null || source == null
                || sourceSlot < 0 || sourceSlot >= source.size()) return UploadResult.INVALID_SOURCE;
        var sourceStack = source.getStackInSlot(sourceSlot);
        if (!isOrdinaryUploadablePattern(sourceStack)) return UploadResult.INVALID_PATTERN;

        var host = findHost(grid, selected);
        if (host == null) return UploadResult.TARGET_OFFLINE;
        var target = host.getTerminalPatternInventory();
        int targetSlot = firstFreeValidSlot(target, sourceStack);
        if (targetSlot < 0) return UploadResult.NO_FREE_SLOT;

        var removed = source.extractItem(sourceSlot, 1, false);
        if (removed.isEmpty() || !ItemStack.isSameItemSameComponents(sourceStack, removed)) {
            if (!removed.isEmpty()) source.addItems(removed);
            return UploadResult.INVALID_SOURCE;
        }
        try {
            target.setItemDirect(targetSlot, removed);
            return new UploadResult(Status.SUCCESS, targetSlot);
        } catch (RuntimeException failure) {
            source.addItems(removed);
            return UploadResult.TARGET_OFFLINE;
        }
    }

    private static PatternProviderLogicHost findHost(IGrid grid, PatternUploadTarget selected) {
        for (var host : grid.getActiveMachines(PatternProviderLogicHost.class)) {
            var blockEntity = host.getBlockEntity();
            if (blockEntity == null || blockEntity.isRemoved()
                    || !(blockEntity.getLevel() instanceof ServerLevel level)) continue;
            if (selected.dimension().equals(level.dimension())
                    && selected.pos().equals(blockEntity.getBlockPos())) return host;
        }
        return null;
    }

    private static boolean isOrdinaryUploadablePattern(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && !(stack.getItem() instanceof ClosedLoopPatternItem)
                && PatternDetailsHelper.isEncodedPattern(stack);
    }

    private static int countFreeSlots(InternalInventory inventory) {
        int result = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) result++;
        }
        return result;
    }

    private static int firstFreeValidSlot(InternalInventory inventory, ItemStack stack) {
        if (inventory == null) return -1;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()
                    && inventory.isItemValid(slot, stack)) return slot;
        }
        return -1;
    }

    public record UploadResult(Status status, int targetSlot) {
        private static final UploadResult INVALID_SOURCE = new UploadResult(Status.INVALID_SOURCE, -1);
        private static final UploadResult INVALID_PATTERN = new UploadResult(Status.INVALID_PATTERN, -1);
        private static final UploadResult TARGET_OFFLINE = new UploadResult(Status.TARGET_OFFLINE, -1);
        private static final UploadResult NO_FREE_SLOT = new UploadResult(Status.NO_FREE_SLOT, -1);
        public boolean successful() { return status == Status.SUCCESS; }
    }

    public enum Status {
        SUCCESS,
        INVALID_SOURCE,
        INVALID_PATTERN,
        TARGET_OFFLINE,
        NO_FREE_SLOT
    }

    private PatternUploadService() { }
}
