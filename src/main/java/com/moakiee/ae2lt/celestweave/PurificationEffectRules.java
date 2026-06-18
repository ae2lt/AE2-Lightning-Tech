package com.moakiee.ae2lt.celestweave;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCures;

public final class PurificationEffectRules {
    public static final TagKey<MobEffect> MEKANISM_SPEED_UP_BLACKLIST = TagKey.create(
            Registries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("mekanism", "speed_up_blacklist"));

    private PurificationEffectRules() {
    }

    public static boolean canPurify(MobEffectInstance effect) {
        if (effect == null || !isConfiguredCategory(effect.getEffect().value().getCategory())) {
            return false;
        }
        return effect.getCures().contains(EffectCures.MILK)
                && !effect.getEffect().is(MEKANISM_SPEED_UP_BLACKLIST);
    }

    private static boolean isConfiguredCategory(MobEffectCategory category) {
        return switch (category) {
            case BENEFICIAL -> AE2LTCommonConfig.overloadArmorPurificationBeneficialEffects();
            case NEUTRAL -> AE2LTCommonConfig.overloadArmorPurificationNeutralEffects();
            case HARMFUL -> AE2LTCommonConfig.overloadArmorPurificationHarmfulEffects();
        };
    }
}
