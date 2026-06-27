package com.moakiee.ae2lt.entity;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Dropped form of the Floating Matter item. Instead of falling it drifts
 * upward, and once it climbs past a configurable multiple of the world's max
 * build height it despawns. The intended way to keep one is to let an AE2
 * Annihilation Plane capture the rising item into the ME network before it
 * escapes — the plane already picks up {@link ItemEntity}s in front of it.
 */
public class FloatingMatterEntity extends ItemEntity {

    public FloatingMatterEntity(EntityType<? extends FloatingMatterEntity> type, Level level) {
        super(type, level);
    }

    public FloatingMatterEntity(Level level, double x, double y, double z, ItemStack stack) {
        super(ModEntities.FLOATING_MATTER.get(), level);
        this.setPos(x, y, z);
        this.setItem(stack);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        // Cancel gravity and force a gentle, steady rise. Applied on both sides
        // so the client prediction matches the server-authoritative position.
        this.setNoGravity(true);
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x * 0.98D, AE2LTCommonConfig.floatingMatterRiseSpeed(), motion.z * 0.98D);

        super.tick();

        if (!this.level().isClientSide()) {
            double ceiling = this.level().getMaxBuildHeight()
                    * AE2LTCommonConfig.floatingMatterDespawnHeightMultiplier();
            if (this.getY() > ceiling) {
                this.discard();
            }
        }
    }
}
