package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.entity.FloatingMatterEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Item whose dropped stack uses the same upward drifting entity as Floating Matter.
 */
public class RisingItem extends Item {

    public RisingItem(Properties properties) {
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
}
