package com.moakiee.ae2lt.item;

import java.util.Set;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;

/**
 * Filter component item for the Overloaded ME Interface.
 * <p>
 * Configurable in AE2's Cell Workbench:
 * <ul>
 *   <li>63 filter slots (all registered AE key types, including chemicals)</li>
 *   <li>2 upgrade slots (fuzzy card + inverter card)</li>
 *   <li>Supports fuzzy matching and whitelist/blacklist inversion</li>
 * </ul>
 * Placed into the interface's filter slot; only affects the input side.
 */
public class OverloadedFilterComponentItem extends Item implements ICellWorkbenchItem {

    private static final int CONFIG_SLOTS = 63;
    private static final int UPGRADE_SLOTS = 2;

    public OverloadedFilterComponentItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isEditable(ItemStack stack) {
        return true;
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return CellConfig.create(
                AEKeyTypes.getAll(),
                stack, CONFIG_SLOTS);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        return stack.getOrDefault(AEComponents.STORAGE_CELL_FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {
        stack.set(AEComponents.STORAGE_CELL_FUZZY_MODE, mode);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, UPGRADE_SLOTS);
    }
}
