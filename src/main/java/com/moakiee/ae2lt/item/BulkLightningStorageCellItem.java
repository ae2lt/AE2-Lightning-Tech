package com.moakiee.ae2lt.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import appeng.core.localization.PlayerMessages;
import appeng.util.InteractionUtil;

import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.util.ItemStackTagSupport;

/**
 * A two-counter storage cell dedicated to the two lightning key variants.
 *
 * <p>The complete inventory is stored directly on the item stack. Unlike the
 * general-purpose infinite cell, this cell never needs an external UUID-backed
 * index because its serialized shape is always exactly two longs.</p>
 */
public final class BulkLightningStorageCellItem extends Item {
    private static final String TAG_HIGH_VOLTAGE = "ae2lt:bulk_lightning_high_voltage";
    private static final String TAG_EXTREME_HIGH_VOLTAGE = "ae2lt:bulk_lightning_extreme_high_voltage";

    private final double idleDrain;

    public BulkLightningStorageCellItem(Properties properties, double idleDrain) {
        super(properties.stacksTo(1));
        this.idleDrain = idleDrain;
    }

    public double getIdleDrain() {
        return idleDrain;
    }

    public static StoredAmounts readStoredAmounts(ItemStack stack) {
        CompoundTag tag = ItemStackTagSupport.getTagCopy(stack);
        return new StoredAmounts(
                sanitize(tag.getLong(TAG_HIGH_VOLTAGE)),
                sanitize(tag.getLong(TAG_EXTREME_HIGH_VOLTAGE)));
    }

    public static void writeStoredAmounts(ItemStack stack, long highVoltage, long extremeHighVoltage) {
        long sanitizedHighVoltage = sanitize(highVoltage);
        long sanitizedExtremeHighVoltage = sanitize(extremeHighVoltage);

        // 1.20.1 keeps cell payloads in the stack NBT; an empty tag is dropped automatically.
        ItemStackTagSupport.updateTag(stack, tag -> {
            putOrRemove(tag, TAG_HIGH_VOLTAGE, sanitizedHighVoltage);
            putOrRemove(tag, TAG_EXTREME_HIGH_VOLTAGE, sanitizedExtremeHighVoltage);
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        disassembleCell(player.getItemInHand(hand), level, player);
        return new InteractionResultHolder<>(
                InteractionResult.sidedSuccess(level.isClientSide()),
                player.getItemInHand(hand));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return disassembleCell(stack, context.getLevel(), context.getPlayer())
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide())
                : InteractionResult.PASS;
    }

    @Override
    // 1.20.1 hover text signature: the TooltipContext argument is replaced by a nullable Level.
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        StoredAmounts amounts = readStoredAmounts(stack);
        tooltipComponents.add(Component.translatable("tooltip.ae2lt.bulk_lightning_storage.capacity")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltipComponents.add(Component.translatable(
                "tooltip.ae2lt.bulk_lightning_storage.high_voltage",
                String.format("%,d", amounts.highVoltage()))
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "tooltip.ae2lt.bulk_lightning_storage.extreme_high_voltage",
                String.format("%,d", amounts.extremeHighVoltage()))
                .withStyle(ChatFormatting.GRAY));
    }

    private boolean disassembleCell(ItemStack stack, Level level, Player player) {
        if (player == null || !InteractionUtil.isInAlternateUseMode(player)) {
            return false;
        }

        // 1.20.1 AE2 has no StorageCellDisassemblyRecipe (added in 1.21); the output is
        // hard-coded here, mirroring BasicStorageCell's core/housing item pair.
        var disassembledStacks = List.of(
                new ItemStack(ModItems.LIGHTNING_ITEM_CELL_HOUSING.get()),
                new ItemStack(ModItems.BULK_LIGHTNING_CELL_COMPONENT.get()));
        if (disassembledStacks.isEmpty()) {
            return false;
        }

        var playerInventory = player.getInventory();
        if (playerInventory.getSelected() != stack) {
            return false;
        }

        var storedAmounts = readStoredAmounts(stack);
        if (storedAmounts.highVoltage() != 0 || storedAmounts.extremeHighVoltage() != 0) {
            player.displayClientMessage(PlayerMessages.OnlyEmptyCellsCanBeDisassembled.text(), true);
            return false;
        }

        playerInventory.setItem(playerInventory.selected, ItemStack.EMPTY);
        for (var disassembledStack : disassembledStacks) {
            playerInventory.placeItemBackInInventory(disassembledStack.copy());
        }
        return true;
    }

    private static long sanitize(long amount) {
        return Math.max(0L, amount);
    }

    private static void putOrRemove(CompoundTag tag, String key, long amount) {
        if (amount == 0L) {
            tag.remove(key);
        } else {
            tag.putLong(key, amount);
        }
    }

    public record StoredAmounts(long highVoltage, long extremeHighVoltage) {
    }
}
