package com.moakiee.ae2lt.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

public final class InfiniteStorageCellItem extends Item {

    private final long capacityLo;
    private final long capacityHi;
    private final int bytesPerType;
    private final int maxTypes;
    private final double idleDrain;

    public InfiniteStorageCellItem(Properties props,
                                   long capacityLo, long capacityHi,
                                   int bytesPerType, int maxTypes,
                                   double idleDrain) {
        super(props.stacksTo(1));
        this.capacityLo = capacityLo;
        this.capacityHi = capacityHi;
        this.bytesPerType = bytesPerType;
        this.maxTypes = maxTypes;
        this.idleDrain = idleDrain;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        // 空壳(创造物品栏里的初始 cell)上显示 "0 types / 0 B" 只是噪音,
        // 完全没带 ae2lt:types / ae2lt:bytes 数据时直接不画 tooltip。
        if (!tag.contains("ae2lt:types") && !tag.contains("ae2lt:bytes")) {
            return;
        }
        int types = tag.getInt("ae2lt:types");
        long bytes = tag.getLong("ae2lt:bytes");

        tooltipComponents.add(Component.translatable(
                "tooltip.ae2lt.infinite_cell.types", String.format("%,d", types))
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "tooltip.ae2lt.infinite_cell.bytes", formatBytes(bytes))
                .withStyle(ChatFormatting.GRAY));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1_000) return String.format("%,d B", bytes);
        if (bytes < 1_000_000) return String.format("%.1f KB", bytes / 1_000.0);
        if (bytes < 1_000_000_000) return String.format("%.1f MB", bytes / 1_000_000.0);
        if (bytes < 1_000_000_000_000L) return String.format("%.1f GB", bytes / 1_000_000_000.0);
        return String.format("%.1f TB", bytes / 1_000_000_000_000.0);
    }

    public long getCapacityLo() { return capacityLo; }
    public long getCapacityHi() { return capacityHi; }
    public int getBytesPerType() { return bytesPerType; }
    public int getMaxTypes() { return maxTypes; }
    public double getIdleDrain() { return idleDrain; }
}
