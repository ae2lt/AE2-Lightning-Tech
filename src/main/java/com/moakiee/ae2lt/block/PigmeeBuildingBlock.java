package com.moakiee.ae2lt.block;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/** The basic Pigmee building block; a plain block without a block entity. */
public final class PigmeeBuildingBlock extends Block {
    public PigmeeBuildingBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_PINK).strength(1.5F, 6.0F).sound(SoundType.STONE)
                .requiresCorrectToolForDrops());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.ae2lt.pigmee_building_block")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.ae2lt.pigmee_building_block.styles")
                .withStyle(ChatFormatting.GRAY));
    }
}
