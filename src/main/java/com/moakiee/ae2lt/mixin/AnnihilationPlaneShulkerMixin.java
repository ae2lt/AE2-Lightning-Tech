package com.moakiee.ae2lt.mixin;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.parts.automation.AnnihilationPlanePart;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets an AE2 Annihilation Plane enchanted with Silk Touch (精准采集) "collect" a
 * vanilla Shulker bullet that flies into it: the bullet is consumed and a single
 * Floating Matter is inserted straight into the plane's ME network. Planes
 * without Silk Touch are unaffected, and the player does not need to shoot the
 * bullet down — reaching the plane is enough.
 */
@Mixin(AnnihilationPlanePart.class)
public abstract class AnnihilationPlaneShulkerMixin {

    @Shadow
    private long insertIntoGrid(AEKey what, long amount, Actionable mode) {
        throw new AssertionError();
    }

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void ae2lt$collectShulkerBullet(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof ShulkerBullet) || !entity.isAlive() || entity.level().isClientSide()) {
            return;
        }
        if (!AE2LTCommonConfig.shulkerBulletCollectionEnabled()) {
            return;
        }

        AnnihilationPlanePart self = (AnnihilationPlanePart) (Object) this;
        if (!self.getMainNode().isActive() || !hasSilkTouch(self)) {
            return;
        }

        long inserted = this.insertIntoGrid(
                AEItemKey.of(ModItems.FLOATING_MATTER.get()), 1L, Actionable.MODULATE);
        if (inserted > 0) {
            entity.discard();
            ci.cancel();
        }
    }

    private static boolean hasSilkTouch(AnnihilationPlanePart plane) {
        for (Holder<Enchantment> enchantment : plane.getEnchantments().keySet()) {
            if (enchantment.is(Enchantments.SILK_TOUCH)) {
                return true;
            }
        }
        return false;
    }
}
