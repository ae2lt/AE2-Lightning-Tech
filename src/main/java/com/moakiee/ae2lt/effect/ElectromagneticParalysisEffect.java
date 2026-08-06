package com.moakiee.ae2lt.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Electromagnetic Paralysis: -75% movement speed (precise, via attribute modifier),
 * 2 seconds by default. Independent of vanilla MOVEMENT_SLOWDOWN so they can stack.
 *
 * <p>The exact -75% multiplier is achieved via {@code addAttributeModifier} on
 * {@link net.minecraft.world.entity.ai.attributes.Attributes#MOVEMENT_SPEED}
 * with operation {@code ADD_MULTIPLIED_TOTAL} and value {@code -0.75}.
 * Vanilla SLOWNESS uses 0.15 per amplifier which can't hit -75% exactly.
 */
public class ElectromagneticParalysisEffect extends MobEffect {
    public ElectromagneticParalysisEffect() {
        super(MobEffectCategory.HARMFUL, 0x88BBFF);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Spawn sparks every 4 ticks for stronger visual feedback.
        return duration % 4 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            double w = entity.getBbWidth() * 0.4;
            double h = entity.getBbHeight() * 0.4;
            level.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    entity.getX(),
                    entity.getY() + entity.getBbHeight() / 2.0,
                    entity.getZ(),
                    4,
                    w, h, w, 0.07);
        }
        return true;
    }
}
