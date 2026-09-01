package com.moakiee.ae2lt.block;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A Pigmee-themed, low-cost variant of the crystal catalyzer.
 *
 * <p>The block entity supplies the behaviour difference; keeping this as a
 * separate block makes the cheaper machine explicit in recipes and the
 * creative inventory while retaining the familiar catalyzer interaction.</p>
 */
public final class PigmeeCrystalCatalyzerBlock extends CrystalCatalyzerBlock {
    public PigmeeCrystalCatalyzerBlock() {
        super();
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("tooltip.ae2lt.pigmee_crystal_catalyzer.1")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.ae2lt.pigmee_crystal_catalyzer.2")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
