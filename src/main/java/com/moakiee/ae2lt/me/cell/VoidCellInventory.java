package com.moakiee.ae2lt.me.cell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.items.storage.StorageCellTooltipComponent;
import appeng.util.ConfigInventory;
import appeng.util.prioritylist.IPartitionList;

import com.moakiee.ae2lt.item.VoidStorageCellItem;

/**
 * Storage implementation for the ME Void Cell.
 *
 * <p>Adapted from ExtendedAE 1.21.1 under LGPL-3.0. It intentionally accepts
 * every AE key type that matches the configured partition. Input amount is
 * normalized using {@link AEKey#getAmountPerUnit()}, exactly like the upstream
 * condenser-based implementation.</p>
 */
public final class VoidCellInventory implements StorageCell {
    private final ItemStack stack;
    private final @Nullable ISaveProvider saveProvider;
    private final VoidStorageCellItem item;
    private final IPartitionList partitionList;
    private final IncludeExclude partitionMode;
    private final VoidCellMode voidMode;

    private final Object2LongMap<AEKey> storedAmounts;
    private double voidEnergy;
    private boolean persisted = true;

    public VoidCellInventory(ItemStack stack, @Nullable ISaveProvider saveProvider) {
        if (!(stack.getItem() instanceof VoidStorageCellItem voidCellItem)) {
            throw new IllegalArgumentException("Cell is not an ME Void Cell");
        }
        this.stack = stack;
        this.saveProvider = saveProvider;
        this.item = voidCellItem;

        var state = VoidCellData.read(stack);
        this.voidMode = state.mode();
        this.voidEnergy = state.energy();
        this.storedAmounts = state.inventory();

        var builder = IPartitionList.builder();
        var upgrades = getUpgradesInventory();
        if (upgrades.isInstalled(AEItems.FUZZY_CARD)) {
            builder.fuzzyMode(getFuzzyMode());
        }
        builder.addAll(getConfigInventory().keySet());
        this.partitionMode = upgrades.isInstalled(AEItems.INVERTER_CARD)
                ? IncludeExclude.BLACKLIST
                : IncludeExclude.WHITELIST;
        this.partitionList = builder.build();
    }

    public boolean isPartitioned() {
        return !partitionList.isEmpty();
    }

    @Override
    public CellState getStatus() {
        return CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return 1.0;
    }

    @Override
    public void persist() {
        if (persisted) {
            return;
        }
        VoidCellData.write(stack, voidMode, voidEnergy, storedAmounts);
        persisted = true;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : storedAmounts.object2LongEntrySet()) {
            if (entry.getLongValue() > 0) {
                out.add(entry.getKey(), entry.getLongValue());
            }
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || partitionList.isEmpty()
                || !partitionList.matchesFilter(what, partitionMode)) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            voidEnergy += (double) amount / what.getAmountPerUnit();
            fillOutput();
            saveChanges();
        }
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0) {
            return 0;
        }
        long currentAmount = storedAmounts.getLong(what);
        if (currentAmount <= 0) {
            return 0;
        }

        long extracted = Math.min(amount, currentAmount);
        if (mode == Actionable.MODULATE) {
            if (extracted == currentAmount) {
                storedAmounts.removeLong(what);
            } else {
                storedAmounts.put(what, currentAmount - extracted);
            }
            saveChanges();
        }
        return extracted;
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return !partitionList.isEmpty() && partitionList.matchesFilter(what, partitionMode);
    }

    private void fillOutput() {
        int requiredPower = voidMode.getRequiredPower();
        if (voidMode == VoidCellMode.TRASH || requiredPower == 0) {
            voidEnergy = 0;
            return;
        }

        AEItemKey output = AEItemKey.of(voidMode.getOutput());
        long amount = (long) (voidEnergy / requiredPower);
        if (output != null && amount > 0) {
            storedAmounts.put(output, storedAmounts.getLong(output) + amount);
            voidEnergy -= amount * requiredPower;
        }
    }

    private ConfigInventory getConfigInventory() {
        return item.getConfigInventory(stack);
    }

    private IUpgradeInventory getUpgradesInventory() {
        return item.getUpgrades(stack);
    }

    private FuzzyMode getFuzzyMode() {
        return item.getFuzzyMode(stack);
    }

    private void saveChanges() {
        persisted = false;
        if (saveProvider != null) {
            saveProvider.saveChanges();
        } else {
            persist();
        }
    }

    public Optional<TooltipComponent> getTooltipImage() {
        var upgradeStacks = new ArrayList<ItemStack>();
        if (AEConfig.instance().isTooltipShowCellUpgrades()) {
            for (var upgrade : getUpgradesInventory()) {
                upgradeStacks.add(upgrade);
            }
        }

        boolean hasMoreContent;
        List<GenericStack> content;
        if (AEConfig.instance().isTooltipShowCellContent()) {
            content = new ArrayList<>();
            for (var entry : storedAmounts.object2LongEntrySet()) {
                if (entry.getLongValue() > 0) {
                    content.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                }
            }
            content.sort(Comparator.comparingLong(GenericStack::amount).reversed());
            int maxShown = AEConfig.instance().getTooltipMaxCellContentShown();
            hasMoreContent = content.size() > maxShown;
            if (hasMoreContent) {
                content.subList(maxShown, content.size()).clear();
            }
        } else {
            hasMoreContent = false;
            content = Collections.emptyList();
        }

        return Optional.of(new StorageCellTooltipComponent(
                upgradeStacks, content, hasMoreContent, true));
    }
}
