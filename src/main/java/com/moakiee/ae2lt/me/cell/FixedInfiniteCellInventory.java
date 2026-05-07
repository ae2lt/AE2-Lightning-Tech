package com.moakiee.ae2lt.me.cell;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.item.FixedInfiniteCellItem;

public final class FixedInfiniteCellInventory implements StorageCell {

    private final ItemStack stack;
    private final AEKey storedKey;
    private final double idleDrain;
    private final ISaveProvider host;
    private final boolean finiteOuter;

    public FixedInfiniteCellInventory(ItemStack stack, double idleDrain, ISaveProvider host) {
        if (!(stack.getItem() instanceof FixedInfiniteCellItem)) {
            throw new IllegalArgumentException("Cell isn't a fixed infinite cell");
        }
        this.stack = stack;
        this.storedKey = FixedInfiniteCellItem.getEffectiveKey(stack);
        this.idleDrain = idleDrain;
        this.host = host;
        this.finiteOuter = FixedInfiniteCellItem.isOuterCell(stack);
    }

    @Override
    public CellState getStatus() {
        return isConsumed() ? CellState.EMPTY : CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return this.idleDrain;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (finiteOuter) {
            return 0;
        }
        return storedKey.equals(what) ? amount : 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (finiteOuter) {
            if (isConsumed() || !storedKey.equals(what) || amount <= 0L) {
                return 0L;
            }

            long extracted = Math.min(amount, what.getAmountPerUnit());
            if (mode == Actionable.MODULATE && extracted > 0L) {
                FixedInfiniteCellItem.setResultConsumed(stack, true);
                if (host != null) {
                    host.saveChanges();
                }
            }
            return extracted;
        }
        return storedKey.equals(what) ? amount : 0;
    }

    @Override
    public void persist() {
        // 所有可变状态（"ResultConsumed" 标记)已在 extract() 中直接通过 CustomData.update
        // 写入 ItemStack 的 NBT，无需在此再序列化。
        //
        // 若此处再调用 host.saveChanges()，会被 AE2 的 MEChestBlockEntity#onCellContentChanged
        // 回调到本方法，形成 persist -> saveChanges -> onCellContentChanged -> persist ...
        // 的无限递归并触发 StackOverflowError（从而让玩家在 ME 箱体/驱动器中看起来"无法提取"）。
    }

    @Override
    public Component getDescription() {
        return this.stack.getHoverName();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (isConsumed()) {
            return;
        }
        out.add(storedKey, finiteOuter ? storedKey.getAmountPerUnit() : getInfiniteAmount(storedKey));
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return !isConsumed() && storedKey.equals(what);
    }

    private static long getInfiniteAmount(AEKey key) {
        return (long) Integer.MAX_VALUE * key.getAmountPerUnit();
    }

    private boolean isConsumed() {
        return finiteOuter && FixedInfiniteCellItem.isResultConsumed(stack);
    }
}
