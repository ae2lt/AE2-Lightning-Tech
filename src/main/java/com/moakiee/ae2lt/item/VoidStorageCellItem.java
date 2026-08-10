package com.moakiee.ae2lt.item;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.api.config.FuzzyMode;
import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellConfig;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.ConfigInventory;

import com.moakiee.ae2lt.me.cell.VoidCellData;
import com.moakiee.ae2lt.me.cell.VoidCellHandler;
import com.moakiee.ae2lt.me.cell.VoidCellInventory;
import com.moakiee.ae2lt.menu.VoidCellMenu;

/**
 * Configurable ME Void Cell, adapted from ExtendedAE 1.21.1 under LGPL-3.0.
 * The 1.20.1 port stores its runtime state in native ItemStack NBT.
 */
public final class VoidStorageCellItem extends AEBaseItem implements ICellWorkbenchItem, IMenuItem {
    private static final String FUZZY_MODE_TAG = "FuzzyMode";

    public VoidStorageCellItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> lines, TooltipFlag advanced) {
        super.appendHoverText(stack, level, lines, advanced);
        var mode = VoidCellData.readMode(stack);
        lines.add(Component.translatable("gui.ae2lt.void_cell.mode." + mode.ordinal())
                .withStyle(ChatFormatting.GREEN));

        var inventory = VoidCellHandler.INSTANCE.getCellInventory(stack, null);
        if (!(inventory instanceof VoidCellInventory voidInventory) || !voidInventory.isPartitioned()) {
            lines.add(Component.translatable("tooltip.ae2lt.void_cell.partition_required")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        var inventory = VoidCellHandler.INSTANCE.getCellInventory(stack, null);
        return inventory instanceof VoidCellInventory voidInventory
                ? voidInventory.getTooltipImage()
                : Optional.empty();
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, 2);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return CellConfig.create(stack);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null) {
            return FuzzyMode.IGNORE_ALL;
        }
        try {
            return FuzzyMode.valueOf(tag.getString(FUZZY_MODE_TAG));
        } catch (IllegalArgumentException ignored) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {
        stack.getOrCreateTag().putString(FUZZY_MODE_TAG, mode.name());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            MenuOpener.open(VoidCellMenu.TYPE, player, MenuLocators.forHand(player, hand));
        }
        return new InteractionResultHolder<>(
                InteractionResult.sidedSuccess(level.isClientSide()),
                player.getItemInHand(hand));
    }

    @Override
    public @Nullable ItemMenuHost getMenuHost(
            Player player, int inventorySlot, ItemStack stack, @Nullable BlockPos pos) {
        return new ItemMenuHost(player, inventorySlot, stack);
    }
}
