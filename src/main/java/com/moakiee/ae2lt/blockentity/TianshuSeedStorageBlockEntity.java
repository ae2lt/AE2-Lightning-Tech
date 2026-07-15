package com.moakiee.ae2lt.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.moakiee.ae2lt.menu.TianshuSeedStorageMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class TianshuSeedStorageBlockEntity extends AEBaseBlockEntity
        implements InternalInventoryHost {
    public static final int CELL_SLOTS = 10;
    private static final String TAG_CELLS = "Cells";
    private static final String TAG_PORT_POS = "PortPos";

    private final AppEngInternalInventory cells = new AppEngInternalInventory(this, CELL_SLOTS, 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getCount() == 1 && StorageCells.isCellHandled(stack);
        }
    };
    private BlockPos portPos;

    public TianshuSeedStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIANSHU_SEED_STORAGE.get(), pos, state);
    }

    public AppEngInternalInventory getCellInventory() { return cells; }
    public BlockPos getPortPos() { return portPos; }

    public void bindToPort(BlockPos newPortPos) {
        portPos = newPortPos == null ? null : newPortPos.immutable();
        saveChanges();
    }

    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        long remaining = Math.max(0L, amount);
        long total = 0L;
        for (int slot = 0; slot < cells.size() && remaining > 0; slot++) {
            var cell = cell(slot);
            if (cell == null) continue;
            long moved = cell.extract(key, remaining, mode, source);
            if (moved > 0 && mode == Actionable.MODULATE) cell.persist();
            total += moved;
            remaining -= moved;
        }
        return total;
    }

    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        long remaining = Math.max(0L, amount);
        long total = 0L;
        for (int slot = 0; slot < cells.size() && remaining > 0; slot++) {
            var cell = cell(slot);
            if (cell == null) continue;
            long moved = cell.insert(key, remaining, mode, source);
            if (moved > 0 && mode == Actionable.MODULATE) cell.persist();
            total += moved;
            remaining -= moved;
        }
        return total;
    }

    public long amount(AEKey key, IActionSource source) {
        return extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
    }

    public void getAvailableStacks(KeyCounter out) {
        for (int slot = 0; slot < cells.size(); slot++) {
            var cell = cell(slot);
            if (cell == null) continue;
            // KeyCounter delegates to fastutil's raw long addition, which wraps. Fold each cell
            // snapshot explicitly so several very large cells cannot turn a usable seed into a
            // negative/zero planning amount.
            var snapshot = new KeyCounter();
            cell.getAvailableStacks(snapshot);
            for (var entry : snapshot) {
                long amount = entry.getLongValue();
                if (amount <= 0) continue;
                long current = Math.max(0L, out.get(entry.getKey()));
                out.set(entry.getKey(), current > Long.MAX_VALUE - amount
                        ? Long.MAX_VALUE : current + amount);
            }
        }
    }

    private StorageCell cell(int slot) {
        var stack = cells.getStackInSlot(slot);
        return stack.isEmpty() ? null : StorageCells.getCellInventory(stack, this::saveChanges);
    }

    public void openMenu(Player player) {
        MenuOpener.open(TianshuSeedStorageMenu.TYPE, player, MenuLocators.forBlockEntity(this));
    }

    public void dropCells() {
        if (level == null) return;
        for (int slot = 0; slot < cells.size(); slot++) {
            var cell = cell(slot);
            if (cell != null) cell.persist();
            var stack = cells.getStackInSlot(slot);
            if (!stack.isEmpty()) Block.popResource(level, worldPosition, stack.copy());
        }
        cells.clear();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        saveChanges();
        notifyPort();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        notifyPort();
    }

    private void notifyPort() {
        if (level != null && portPos != null
                && level.getBlockEntity(portPos) instanceof TianshuSupercomputerPortBlockEntity port) {
            port.seedDrivesChanged();
        }
    }

    @Override public boolean isClientSide() { return level != null && level.isClientSide; }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        for (int slot = 0; slot < cells.size(); slot++) {
            var cell = cell(slot);
            if (cell != null) cell.persist();
        }
        super.saveAdditional(tag, registries);
        cells.writeToNBT(tag, TAG_CELLS, registries);
        if (portPos != null) tag.putLong(TAG_PORT_POS, portPos.asLong());
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        cells.readFromNBT(tag, TAG_CELLS, registries);
        portPos = tag.contains(TAG_PORT_POS, Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong(TAG_PORT_POS)) : null;
    }

    @Override
    public void addAdditionalDrops(net.minecraft.world.level.Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (int slot = 0; slot < cells.size(); slot++) {
            var stack = cells.getStackInSlot(slot);
            if (!stack.isEmpty()) drops.add(stack.copy());
        }
    }

    @Override public void clearContent() { cells.clear(); }
}
