package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Turns an adult pig struck by a falling anvil into the reusable Pigmee crafting core.
 *
 * <p>This intercepts the incoming damage instead of waiting for normal death, so the conversion
 * has exactly one output and cannot also emit pork or Looting-scaled drops.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class PigmeeCoreAcquisitionHandler {
    private PigmeeCoreAcquisitionHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Pig pig)
                || pig.isBaby()
                || pig.isRemoved()
                || !event.getSource().is(DamageTypes.FALLING_ANVIL)
                || !(pig.level() instanceof ServerLevel level)) {
            return;
        }

        var drop = new ItemEntity(
                level,
                pig.getX(),
                pig.getY() + pig.getBbHeight() * 0.5D,
                pig.getZ(),
                new ItemStack(ModItems.PIGMEE_CORE.get()));
        drop.setDefaultPickUpDelay();
        if (!level.addFreshEntity(drop)) {
            return;
        }

        event.setCanceled(true);
        pig.discard();
        level.sendParticles(
                ParticleTypes.POOF,
                pig.getX(),
                pig.getY() + pig.getBbHeight() * 0.5D,
                pig.getZ(),
                12,
                pig.getBbWidth() * 0.25D,
                pig.getBbHeight() * 0.25D,
                pig.getBbWidth() * 0.25D,
                0.02D);
    }
}
