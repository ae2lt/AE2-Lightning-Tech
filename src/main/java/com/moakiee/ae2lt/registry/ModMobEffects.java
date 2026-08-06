package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.effect.ElectromagneticParalysisEffect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, AE2LightningTech.MODID);

    public static final DeferredHolder<MobEffect, ElectromagneticParalysisEffect>
            ELECTROMAGNETIC_PARALYSIS = EFFECTS.register(
                    "electromagnetic_paralysis",
                    () -> {
                        var effect = new ElectromagneticParalysisEffect();
                        effect.addAttributeModifier(
                                Attributes.MOVEMENT_SPEED,
                                ResourceLocation.fromNamespaceAndPath(
                                        AE2LightningTech.MODID,
                                        "electromagnetic_paralysis_speed"),
                                -0.75D,
                                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                        return effect;
                    });

    private ModMobEffects() {}
}
