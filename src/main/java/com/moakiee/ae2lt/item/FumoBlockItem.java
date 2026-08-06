package com.moakiee.ae2lt.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public class FumoBlockItem extends BlockItem implements Equipable {
    @Nullable
    private final String tooltipKey;

    public FumoBlockItem(Block block, Item.Properties properties) {
        this(block, properties, null);
    }

    public FumoBlockItem(Block block, Item.Properties properties, String tooltipKey) {
        super(block, properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        if (tooltipKey == null) {
            return;
        }
        tooltipComponents.add(Component.translatable(tooltipKey + ".1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(tooltipKey + ".2").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(tooltipKey + ".3").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(tooltipKey + ".4").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
