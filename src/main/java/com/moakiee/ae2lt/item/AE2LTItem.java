package com.moakiee.ae2lt.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Bridges 1.21's tooltip context callback onto 1.20.1's item tooltip signature.
 */
public class AE2LTItem extends Item {
    public AE2LTItem(Properties properties) {
        super(properties);
    }

    @Override
    public final void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        appendHoverText(stack, new TooltipContext(level), tooltipComponents, tooltipFlag);
    }

    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
    }

    public static final class TooltipContext {
        private final Level level;

        public TooltipContext(@Nullable Level level) {
            this.level = level;
        }

        @Nullable
        public Level level() {
            return level;
        }
    }
}
