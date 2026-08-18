package com.moakiee.ae2lt.me.cell;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.item.BulkLightningStorageCellItem;
import com.moakiee.ae2lt.me.key.LightningKey;

/**
 * Real, saturating storage for both lightning tiers.
 *
 * <p>Each tier owns an independent {@code long} counter. {@link Long#MAX_VALUE}
 * is the practical capacity of each counter; inserts are clamped before addition
 * so stored values can never wrap into the negative range.</p>
 */
public final class BulkLightningCellInventory implements StorageCell {
    private final ItemStack stack;
    private final @Nullable ISaveProvider saveProvider;
    private final double idleDrain;

    private long highVoltage;
    private long extremeHighVoltage;
    private boolean dirty;

    public BulkLightningCellInventory(ItemStack stack, @Nullable ISaveProvider saveProvider) {
        if (!(stack.getItem() instanceof BulkLightningStorageCellItem cellItem)) {
            throw new IllegalArgumentException("Cell is not a bulk lightning storage cell");
        }

        this.stack = stack;
        this.saveProvider = saveProvider;
        this.idleDrain = cellItem.getIdleDrain();

        var amounts = BulkLightningStorageCellItem.readStoredAmounts(stack);
        this.highVoltage = amounts.highVoltage();
        this.extremeHighVoltage = amounts.extremeHighVoltage();
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0L || !isSupported(what)) {
            return 0L;
        }

        long stored = getStored(what);
        long inserted = Math.min(amount, Long.MAX_VALUE - stored);
        if (inserted > 0L && mode == Actionable.MODULATE) {
            setStored(what, stored + inserted);
            markChanged();
        }
        return inserted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0L || !isSupported(what)) {
            return 0L;
        }

        long stored = getStored(what);
        long extracted = Math.min(amount, stored);
        if (extracted > 0L && mode == Actionable.MODULATE) {
            setStored(what, stored - extracted);
            markChanged();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (highVoltage > 0L) {
            out.add(LightningKey.HIGH_VOLTAGE, highVoltage);
        }
        if (extremeHighVoltage > 0L) {
            out.add(LightningKey.EXTREME_HIGH_VOLTAGE, extremeHighVoltage);
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return isSupported(what) && getStored(what) > 0L;
    }

    @Override
    public CellState getStatus() {
        return highVoltage == 0L && extremeHighVoltage == 0L
                ? CellState.EMPTY
                : CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return idleDrain;
    }

    @Override
    public boolean canFitInsideCell() {
        return highVoltage == 0L && extremeHighVoltage == 0L;
    }

    @Override
    public void persist() {
        if (!dirty) {
            return;
        }

        BulkLightningStorageCellItem.writeStoredAmounts(stack, highVoltage, extremeHighVoltage);
        dirty = false;
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }

    private static boolean isSupported(AEKey key) {
        return LightningKey.HIGH_VOLTAGE.equals(key)
                || LightningKey.EXTREME_HIGH_VOLTAGE.equals(key);
    }

    private long getStored(AEKey key) {
        return LightningKey.EXTREME_HIGH_VOLTAGE.equals(key)
                ? extremeHighVoltage
                : highVoltage;
    }

    private void setStored(AEKey key, long amount) {
        if (LightningKey.EXTREME_HIGH_VOLTAGE.equals(key)) {
            extremeHighVoltage = amount;
        } else {
            highVoltage = amount;
        }
    }

    private void markChanged() {
        dirty = true;
        if (saveProvider != null) {
            saveProvider.saveChanges();
        } else {
            persist();
        }
    }
}
