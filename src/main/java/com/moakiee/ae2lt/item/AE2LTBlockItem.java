package com.moakiee.ae2lt.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Block-item variant of the tooltip bridge used by 1.21-style item code.
 */
public class AE2LTBlockItem extends BlockItem {
    public AE2LTBlockItem(Block block, Properties properties) {
        super(block, properties);
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
