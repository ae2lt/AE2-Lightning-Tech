package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.entity.FloatingMatterEntity;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Crafting material that floats away when dropped in the world. Whenever the
 * game would spawn a default item entity for this stack we replace it with a
 * {@link FloatingMatterEntity} so the "tossed = drifts up and vanishes"
 * behaviour applies no matter how the stack was dropped.
 */
public class FloatingMatterItem extends Item {

    public FloatingMatterItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Override
    public Entity createEntity(Level level, Entity location, ItemStack stack) {
        FloatingMatterEntity matter =
                new FloatingMatterEntity(level, location.getX(), location.getY(), location.getZ(), stack);
        matter.setDeltaMovement(location.getDeltaMovement());
        matter.setPickUpDelay(40);
        return matter;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ae2lt.floating_matter.desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
