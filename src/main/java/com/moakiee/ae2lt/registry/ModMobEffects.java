package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.effect.ElectromagneticParalysisEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, AE2LightningTech.MODID);

    public static final RegistryObject<ElectromagneticParalysisEffect>
            ELECTROMAGNETIC_PARALYSIS = EFFECTS.register(
                    "electromagnetic_paralysis",
                    () -> {
                        var effect = new ElectromagneticParalysisEffect();
                        effect.addAttributeModifier(
                                Attributes.MOVEMENT_SPEED,
                                AE2LightningTech.MODID + ":electromagnetic_paralysis_speed",
                                -0.75D,
                                AttributeModifier.Operation.MULTIPLY_TOTAL);
                        return effect;
                    });

    private ModMobEffects() {}
}
